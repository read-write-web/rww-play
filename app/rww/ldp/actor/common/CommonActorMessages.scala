package rww.ldp.actor.common

import akka.actor.ActorRef
import org.w3.banana.RDF
import rww.ldp.LDPCommand

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object CommonActorMessages {


  case class WebActorSetterMessage(web: ActorRef)
  case class LDPSActorSetterMessage(ldps: ActorRef)


  case class ScriptMessage[Rdf<:RDF,A](script:LDPCommand.Script[Rdf,A])
  case class CmdMessage[Rdf<:RDF,A](command: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]])

}
