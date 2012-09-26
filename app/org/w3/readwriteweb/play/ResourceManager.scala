package org.w3.readwriteweb.play

import akka.actor.{Props, ActorRef, Actor}
import org.w3.banana._
import java.io.File
import org.w3.banana.jena._
import java.net.URL
import scalaz.Scalaz._
import scalaz._
import akka.event.Logging
import com.hp.hpl.jena.sparql.core.DatasetGraphFactory
import akka.actor.InvalidActorNameException
import org.w3.banana.WrongExpectation


trait ReadWriteWebException extends Exception

case class CannotCreateResource(msg: String) extends ReadWriteWebException
/**
 *
 */

class ResourceManager(baseDirectory: File, baseUrl: URL) extends Actor {

  def getOrCreateResourceActor(path: String): ActorRef = {
    try {
      context.children.find(path == _.path.name) getOrElse {
        val fileOnDisk = new File(baseDirectory, path)
        context.actorOf(Props(new JenaResourceActor(fileOnDisk,path,new URL(baseUrl,path))), name = path)
      }
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / path)
    }
  }

  protected def receive: Actor.Receive = {
    case req @ Request(_,path)=>  {
      val resourceActorRef = getOrCreateResourceActor(path)
      resourceActorRef.forward(req)
    }
    case req @ Put(path,_)=>  {
      val resourceActorRef = getOrCreateResourceActor(path)
      resourceActorRef.forward(req)
    }

    case req @ Query(_,path) =>  {
      val resourceActorRef = getOrCreateResourceActor(path)
      resourceActorRef.forward(req)
    }

  }
}

case class Request(method: String, path: String )
case class Put[Rdf<:RDF](path: String, content: RwwContent)
case class Query[Rdf<:RDF](query: QueryRwwContent[Rdf], path: String)

abstract
class ResourceActor[Rdf <: RDF,+SyntaxType](
            file: File,
            path: String,
            url: URL)(implicit
                      ops: RDFOps[Rdf],
                      graphQuery: Rdf#Graph => SparqlEngine[Rdf,Id],
                      sparqlOps: SparqlOps[Rdf]) extends Actor {

//  val dsl = Diesel(ops)
//  import dsl._
  import ops._

  val defaultGraph = URI("http://default.graph/")
  val log = Logging(context.system, this)


  def reader[S>: SyntaxType]: RDFReader[Rdf, S]

  def writer[S>: SyntaxType]: RDFWriter[Rdf, S]

  lazy val parent = file.getParentFile

  log.info("Resource actor for " + file.getAbsolutePath + " and url=" + url)

  protected def graph: Validation[BananaException, Rdf#Graph] =
    reader.read(file, url.toString)

  protected def receive: Actor.Receive = {
    case req@Request("GET", path) => {
      sender ! graph
    }
    case pg@Put(path, GraphRwwContent(model: Rdf#Graph)) => {
      val result =  WrappedThrowable.fromTryCatch {
        if (parent.isDirectory) true
        else parent.mkdirs()
      } flatMap {
        case true => {
          log.info("created dir " + parent + " now saving file")
          writer.write(model, file, url.toString)
        }
        case false => {
          log.warning("could not create dir " + parent)
          WrongExpectation("path=" + path + " file=" + file.getAbsolutePath).fail
        }
      }
      sender ! result
    }
    case Query(QueryRwwContent(q: Rdf#Query), _) => {
      sender ! graph.map {
        graph =>  q match {
          case ask: Rdf#AskQuery => Either3.left3(graph.executeAsk(ask))
          case select: Rdf#SelectQuery => Either3.middle3(graph.executeSelect(select))
          case construct: Rdf#ConstructQuery => Either3.right3(graph.executeConstruct(construct))
        }
      }
    }
    case unknown => {
      log.warning("received unknown message=>" + unknown)
      sender ! WrongExpectation("could not parse message " + unknown).fail
    }
  }
}

class JenaResourceActor(file: File, path: String, url: URL) extends ResourceActor[Jena,Turtle](
   file,path,url)(JenaOperations, JenaGraphSparqlEngine.makeSparqlEngine, JenaSparqlOps) {

  def reader[S >: Turtle] = Jena.turtleReader

  def writer[S >: Turtle] = Jena.turtleWriter

  val store = JenaStore(DatasetGraphFactory.createMem())

// not sure what happened to OpenSPARQLEngine
//  protected lazy val sparqlEngine = OpenSPARQLEngine(JenaSPARQLOperations,store)

}