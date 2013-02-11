package org.www.play.rdf.plantain;

import org.w3.banana.plantain._
import org.www.play.remote._
import play.api.libs.ws.WS
import org.w3.banana.{ReaderSelector, MimeType}
import akka.actor.ActorRef
import scalaz.-\/
import scalaz.\/-
import org.www.play.remote.WrappedException
import util.Failure
import scala.Some
import org.w3.banana.plantain.GetResource
import org.w3.banana.plantain.PlantainLDPS.Cmd
import util.Success
import org.w3.banana.plantain.PlantainLDPS.Script
import org.w3.banana.plantain.PlantainLDPR

//excluding is a uri that the server is locally listening to, so go directly there
class PlantainWebProxy[Rdf<:Plantain](val excluding: URI, val graphSelector: ReaderSelector[Rdf]) extends RActor {
  /**
   * Runs a command that can be evaluated on this container.
   * @param cmd the command to evaluate
   * @tparam A The final return type of the script
   * @return a script for further evaluation
   */
  def runLocalCmd[A](cmd: LDPCommand[Plantain, Plantain#Script[A]]) {
    System.out.println(s"in RunLocalCmd - received $cmd")
    cmd match {
//      case CreateLDPR(_, slugOpt, graph, k) => {
//        val ldpr = PlantainLDPR(uri, graph.resolveAgainst(uri))
//        LDPRs.put(pathSegment, ldpr)
//        k(uri)
//      }
//      case CreateBinary(_, slugOpt, mime: MimeType, k) => {
//        val (uri, pathSegment) = deconstruct(slugOpt)
//        //todo: make sure the uri does not end in ";meta" or whatever else the meta standard will be
//        val bin = PlantainBinary(root, uri)
//        NonLDPRs.put(pathSegment, bin)
//        k(bin)
//      }
//      case CreateContainer(_,slugOpt,graph,k) => {
//        val (uri,pathSegment) = deconstruct(slugOpt) //todo: deconstruct should check the file system. This should in fact use a file sytem call
//        val p = root.resolve(pathSegment)
//        Files.createDirectory(p)
//        context.actorOf(Props(new PlantainLDPCActor(uri, p)),pathSegment)
//        k(uri)
//      }
      case GetResource(uri, k) => {
        val url = uri.underlying

        /**
         *  note we prefer rdf/xml and turtle over html, as html does not always contain rdfa, and we prefer those over n3,
         * as we don't have a full n3 parser. Better would be to have a list of available parsers for whatever rdf framework is
         * installed (some claim to do n3 when they only really do turtle)
         * we can't currently accept as we don't have GRDDL implemented
         */
        //todo, add binary support
        val response = WS.url(url.toString)
          .withHeaders("Accept" -> "application/rdf+xml,text/turtle,application/xhtml+xml;q=0.8,text/html;q=0.7,text/n3;q=0.2")
          .get
        import play.api.libs.concurrent.Execution.Implicits.defaultContext
        response.onComplete { tryres =>
          import MimeType._

          tryres match {
             case Success(response) => response.header("Content-Type") match {
                 case Some(header) =>  graphSelector(MimeType(normalize(extract(header))))  match {
                   case Some(r) =>  r.read(response.body,"") match {  //todo: add base & use binary type
                     case Success(g) => {
                       //todo: add to cache
                        self forward k(PlantainLDPR(uri,g,None))
                     }
                     case Failure(e) => sender ! WrappedException("had problems parsing document returned by server",e)
                   }
                   case None => sender ! LocalException("no Iteratee/parser for Content-Type " + response.header("Content-Type"))
                 }
                 case None =>sender ! RemoteException.netty("no Content-Type header specified in response returned by server ",response.status,response.getAHCResponse.getHeaders)
             }
             case Failure(response) => sender ! WrappedException("failure fetching resource",response.getCause)
           }

        }
      }
//      case GetMeta(uri, k) => {
//        //todo: GetMeta here is very close to GetResource, as currently there is no big work difference between the two
//        //The point of GetMeta is mostly to remove work if there were work that was very time
//        //consuming ( such as serialising a graph )
//        val path = uri.lastPathSegment
//        val res = LDPRs.get(path).getOrElse(NonLDPRs(path))
//        k(res.asInstanceOf[Meta[Plantain]])
//      }
//      case DeleteResource(uri, a) => {
//        LDPRs.remove(uri.lastPathSegment).orElse {
//          NonLDPRs.remove(uri.lastPathSegment)
//        } orElse (throw new NoSuchElementException("Could not find resource " + uri))
//        a //todo: why no function here?
//      }
//      case UpdateLDPR(uri, remove, add, a) => {
//        val pathSegment = uri.lastPathSegment
//        val graph = LDPRs.get(pathSegment).map(_.graph).getOrElse {
//          throw new NoSuchElementException(s"Resource does not exist at $uri with path segment '$pathSegment'")
//        }
//        val temp = remove.foldLeft(graph) {
//          (graph, tripleMatch) => graph - tripleMatch.resolveAgainst(uri.resolveAgainst(baseUri))
//        }
//        val resultGraph = add.foldLeft(temp) {
//          (graph, triple) => graph + triple.resolveAgainst(uri.resolveAgainst(baseUri))
//        }
//        val ldpr = PlantainLDPR(uri, resultGraph)
//        LDPRs.put(pathSegment, ldpr)
//        a //todo: why no function here?
//      }
//      case SelectLDPR(uri, query, bindings, k) => {
//        val graph = LDPRs(uri.lastPathSegment).graph
//        val solutions = PlantainUtil.executeSelect(graph, query, bindings)
//        k(solutions)
//      }
//      case ConstructLDPR(uri, query, bindings, k) => {
//        val graph = LDPRs(uri.lastPathSegment).graph
//        val resultGraph = PlantainUtil.executeConstruct(graph, query, bindings)
//        k(resultGraph)
//      }
//      case AskLDPR(uri, query, bindings, k) => {
//        val graph = LDPRs(uri.lastPathSegment).graph
//        val resultGraph = PlantainUtil.executeAsk(graph, query, bindings)
//        k(resultGraph)
//      }
//      case SelectLDPC(_,query, bindings, k) => {
//        val solutions = PlantainUtil.executeSelect(tripleSource, query, bindings)
//        k(solutions)
//      }
//      case ConstructLDPC(_,query, bindings, k) => {
//        val graph = PlantainUtil.executeConstruct(tripleSource, query, bindings)
//        k(graph)
//      }
//      case AskLDPC(_,query, bindings, k) => {
//        val b = PlantainUtil.executeAsk(tripleSource, query, bindings)
//        k(b)
//      }
    }
  }


  /**
   * @param script
   * @param t
   * @tparam A
   * @throws NoSuchElementException if the resource does not exist
   */
  final def run[A](sender: ActorRef, script: Plantain#Script[A]) {
    script.resume match {
      case -\/(cmd) => {
        if(local(cmd.uri.underlying,excluding.underlying) == None) {
          runLocalCmd(cmd)
          //if we were to have some commands return an immediate value, then we could do
          // the following with the returned script
         //  run(sender, script)
        }
        else {
          val a = context.actorFor("/user/web")
          System.out.println(s"sending to $a")
          a forward Cmd(cmd)
        }
      }
      case \/-(a) => {
        System.out.println(s"returning to $sender $a")
        sender ! a
      }
    }
  }


   def receive = returnErrors {
      case Script(script) => {
        run(sender, script)
      }
      case Cmd(command) => {
        System.out.println(s"received $command ")
        runLocalCmd(command)
//        run(sender, script)
      }
    }
}
