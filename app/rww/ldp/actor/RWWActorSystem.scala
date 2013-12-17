package rww.ldp.actor

import org.w3.banana.RDF
import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.Future
import rww.ldp.LDPCommand

object RWWActorSystem {
  // the two values below specify the akka paths
  // full example akka://rww/user/router

  val systemPath = "rww"
  val rwwPath = "/user/router"
}

/**
 * Trait for interactions with ReadWriteWeb
 * @tparam Rdf
 */
trait RWWActorSystem[Rdf <: RDF] {  //not sure which of exec or execute is going to be needed
   def system: ActorSystem

  /**
   * Execute a script and return an answer
   * @param script
   * @tparam A
   * @return
   */
  def execute[A](script: LDPCommand.Script[Rdf,A]): Future[A]

  /**
   * Execute a command, which could lead to further commands.
   * ( one of execute or exec may be unecessary, usage will tell )
   * @param cmd
   * @tparam A
   * @return
   */
  def exec[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]]): Future[A]
  /**
   *  The Web Actor that can work with commands required on the web
   */
  def setWebActor(webActor: ActorRef)

  /**
   * The Actor For the LDP Server which stores resources locally ( in a file system or DB )
   * @param ldpsActor
   */
  def setLDPSActor(ldpsActor: ActorRef)

  /**
   * Shut the RWW server down
   */
  def shutdown(): Unit
}

