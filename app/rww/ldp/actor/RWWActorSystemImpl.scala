package rww.ldp.actor

import org.slf4j.LoggerFactory
import org.w3.banana.{RDFOps, RDF}
import java.nio.file.Path
import akka.actor._
import akka.util.Timeout
import scala.concurrent.Future
import akka.pattern.ask
import akka.actor.DeadLetter
import rww.ldp._
import rww.ldp.actor.router._
import rww.ldp.actor.common.CommonActorMessages
import CommonActorMessages._
import rww.ldp.LDPExceptions._

object RWWActorSystemImpl {

  val logger = LoggerFactory.getLogger(this.getClass)


  def apply[Rdf<:RDF](baseUri: Rdf#URI, root: Path, cache: Option[Props])
                     (implicit ops: RDFOps[Rdf], timeout: Timeout = Timeout(5000)): RWWActorSystemImpl[Rdf] =
    new RWWActorSystemImpl(baseUri)


}

/**
 * Actor that implements RWW for a local system with only one root
 * @param baseUri
 * @param ops
 * @param timeout
 * @tparam Rdf
 */
class RWWActorSystemImpl[Rdf<:RDF](val baseUri: Rdf#URI)
                     (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RWWActorSystem[Rdf] {
  import RWWActorSystem._
  val system = ActorSystem(systemPath)
  val rwwActorRef = system.actorOf(Props(new RWWRoutingActorSubdomains(baseUri)),name="router")
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