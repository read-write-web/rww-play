package rww.ldp.actor

import org.slf4j.LoggerFactory
import org.w3.banana._
import java.nio.file.Path
import akka.actor._
import akka.util.Timeout
import org.w3.banana.io._
import rww.ldp.actor.local.{LDPCSubdomainActor, LDPCActor}
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern.ask
import rww.ldp._
import rww.ldp.actor.router._
import rww.ldp.actor.remote.LDPWebActor
import rww.ldp.actor.common.CommonActorMessages.ScriptMessage
import akka.actor.DeadLetter
import rww.ldp.LDPExceptions.ResourceDoesNotExist
import rww.ldp.actor.common.CommonActorMessages.LDPSActorSetterMessage
import rww.ldp.actor.common.CommonActorMessages.CmdMessage
import rww.ldp.actor.common.CommonActorMessages.WebActorSetterMessage
import scala.util.Try
import akka.http.scaladsl.model.Uri

object RWWActorSystemImpl {

  val logger = LoggerFactory.getLogger(this.getClass)


  /**
   * Convenience function to start a RWWActor with a root subdomains LDPC
   * @param baseUri the base uri of the root LDPC
   * @param baseDir the base directory for the root LDPC
   * @param fetcher the web fetcher
   * @param ops RDF ops for the given Rdf
   * @param sparqlGraph sparql operations on graphs
   * @param ec execution context
   * @param timeout timeout for the requests ( I think, check ... )
   * @tparam Rdf an implementation of RDF
   * @return the rww actor system through which all requests can be made
   */
  def withSubdomains[Rdf<:RDF](baseUri: Rdf#URI, baseDir: Path, fetcher: WebClient[Rdf])(implicit
                                                 ops: RDFOps[Rdf],
                                                 sparqlOps: SparqlOps[Rdf],
                                                 sparqlGraph: SparqlEngine[Rdf, Try,Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
                                                 reader: RDFReader[Rdf, Try, Turtle],
                                                 writer: RDFWriter[Rdf, Try, Turtle],
                                                 ec: ExecutionContext,
                                                 timeout: Timeout): RWWActorSystem[Rdf] = {
    val cleanBaseUri = ops.URI(Uri(baseUri.toString).toString())
    val rwwActor =  new RWWActorSystemImpl(cleanBaseUri,Props(new RWWRoutingActorSubdomains(baseUri)))
    rwwActor.setWebActor(rwwActor.system.actorOf(Props(new LDPWebActor[Rdf](cleanBaseUri, fetcher)), "webActor"))
    rwwActor.setLDPSActor(rwwActor.system.actorOf(Props(new LDPCSubdomainActor[Rdf](cleanBaseUri, baseDir)),"rootContainer"))
    return rwwActor
  }

  def plain[Rdf<:RDF](baseUri: Rdf#URI, baseDir: Path, fetcher: WebClient[Rdf])(implicit
                                        ops: RDFOps[Rdf],
                                        sparqlOps: SparqlOps[Rdf],
                                        sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
                                        reader: RDFReader[Rdf, Try, Turtle],
                                        writer: RDFWriter[Rdf, Try, Turtle],
                                        ec: ExecutionContext,
                                        timeout: Timeout): RWWActorSystem[Rdf] = {
    val cleanBaseUri = ops.URI(Uri(baseUri.toString).toString())
    val rwwActor = new RWWActorSystemImpl(cleanBaseUri,Props(new RWWRoutingActor(baseUri)))
    rwwActor.setWebActor(rwwActor.system.actorOf(Props(new LDPWebActor[Rdf](cleanBaseUri, fetcher)), "webActor"))
    rwwActor.setLDPSActor(rwwActor.system.actorOf(Props(new LDPCActor[Rdf](cleanBaseUri, baseDir)),"rootContainer"))
    return rwwActor
  }

}

/**
 * Actor that implements RWW for a local system with only one root
 * @param baseUri
 * @param ops
 * @param timeout
 * @tparam Rdf
 */
class RWWActorSystemImpl[Rdf<:RDF](val baseUri: Rdf#URI, routingActor: Props)
                     (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RWWActorSystem[Rdf] {
  import RWWActorSystem._
  val system = ActorSystem(systemPath)
  val rwwActorRef = system.actorOf(routingActor,name="router")
  import RWWActorSystemImpl.logger

  logger.info(s"Created rwwActorRef=<$rwwActorRef>")

  val listener = system.actorOf(Props(new Actor {
    system.eventStream.subscribe(self, classOf[DeadLetter])

    def receive = {
      case d: DeadLetter if ( d.message.isInstanceOf[ScriptMessage[_,_]] || d.message.isInstanceOf[CmdMessage[_,_]] ) ⇒ {
        d.sender !  akka.actor.Status.Failure(ResourceDoesNotExist(s"could not find actor for ${d.recipient}"))
      }
    }
    override def postStop() {
      context.system.eventStream.unsubscribe(self)
    }
  }))


  def execute[A](script: LDPCommand.Script[Rdf, A]) = {
    (rwwActorRef ? ScriptMessage[Rdf,A](script)).asInstanceOf[Future[A]]
  }

  def exec[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]]) = {
    (rwwActorRef ? CmdMessage(cmd)).asInstanceOf[Future[A]]
  }

  def shutdown(): Unit = {
    system.shutdown()
  }

  def setWebActor(ref: ActorRef) {
    rwwActorRef ! WebActorSetterMessage(ref)
  }

  def setLDPSActor(ldpsActor: ActorRef) {
    rwwActorRef ! LDPSActorSetterMessage(ldpsActor)
  }
}