package rww.ldp

import org.slf4j.LoggerFactory
import org.w3.banana.{RDFOps, RDF}
import java.nio.file.Path
import akka.actor._
import akka.util.Timeout
import scala.concurrent.Future
import akka.actor.DeadLetter
import akka.pattern.ask


object RWWeb {

  val logger = LoggerFactory.getLogger(this.getClass)


  def apply[Rdf<:RDF](baseUri: Rdf#URI, root: Path, cache: Option[Props])
                     (implicit ops: RDFOps[Rdf], timeout: Timeout = Timeout(5000)): RWWeb[Rdf] =
    new RWWeb(baseUri)


}

/**
 * Actor that implements RWW for a local system with only one root
 * @param baseUri
 * @param ops
 * @param timeout
 * @tparam Rdf
 */
class RWWeb[Rdf<:RDF](val baseUri: Rdf#URI)
                     (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RWW[Rdf] {
  import RWW._
  val system = ActorSystem(systemPath)
  val rwwActorRef = system.actorOf(Props(new RWWebActor(baseUri)),name="router")
  import RWWeb.logger

  logger.info(s"Created rwwActorRef=<$rwwActorRef>")

  val listener = system.actorOf(Props(new Actor {
    def receive = {
      case d: DeadLetter if ( d.message.isInstanceOf[Scrpt[_,_]] || d.message.isInstanceOf[Cmd[_,_]] ) ⇒ {
        d.sender !  akka.actor.Status.Failure(ResourceDoesNotExist(s"could not find actor for ${d.recipient}"))
      }
    }
  }))
  system.eventStream.subscribe(listener, classOf[DeadLetter])


  def execute[A](script: LDPCommand.Script[Rdf, A]) = {
    (rwwActorRef ? Scrpt[Rdf,A](script)).asInstanceOf[Future[A]]
  }

  def exec[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]]) = {
    (rwwActorRef ? Cmd(cmd)).asInstanceOf[Future[A]]
  }

  def shutdown(): Unit = {
    system.shutdown()
  }

  def setWebActor(ref: ActorRef) {
    rwwActorRef ! WebActor(ref)
  }

  def setLDPSActor(ldpsActor: ActorRef) {
    rwwActorRef ! LDPSActor(ldpsActor)
  }
}