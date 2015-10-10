package rww.ldp.actor.common

import akka.actor.Actor
import rww.ldp.actor.RWWActorSystem

/**
 * Base actor trait used by many actors of this project
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
trait RWWBaseActor extends Actor with akka.actor.ActorLogging {


  /**
   * Permits to catch exceptions and forward them to the sender as a Future failure
   * @param pf
   * @tparam A
   * @tparam B
   * @return
   */
  def returnErrors[A,B](pf: Receive): Receive = new PartialFunction[Any,Unit] {
    //interestingly it seems we can't catch an error here! If we do, we have to return a true or a false
    // and whatever we choose it could have bad sideffects. What happens if the isDefinedAt throws an exception?
    def isDefinedAt(x: Any): Boolean = pf.isDefinedAt(x)
    def apply(a: Any): Unit = try {
      pf.apply(a)
    } catch {
      case e: Exception => sender ! akka.actor.Status.Failure(e)
    }
  }

  // TODO as we've seen, actorSelection is not like ActorRef and
  // sending message to a selection could lead to no message being sent at all, without dead lettering
  def rwwRouterActor =  context.actorSelection(RWWActorSystem.rwwPath)

}
