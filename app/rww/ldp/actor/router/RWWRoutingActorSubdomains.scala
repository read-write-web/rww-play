package rww.ldp.actor.router

import org.w3.banana.{syntax, RDFOps, RDF}
import akka.util.Timeout
import akka.actor.ActorRef
import java.net.{URI=>jURI}
import scalaz.-\/
import scala.Some
import scalaz.\/-
import rww.ldp._
import rww.ldp.actor._
import rww.ldp.actor.common.{CommonActorMessages, RWWBaseActor}
import CommonActorMessages._
import rww.ldp.actor.common.RWWBaseActor

object RWWRoutingActorSubdomains {

  case class SubdomainSwitch(subhost: Option[String], path: String)

  /**
   * We compare the uri u to the base URI, and if this uri seems local to the base uri,
   * this means that the uri content can be retrieved on the filesystem, and not on a remote host
   * Thus we return the local path of the resource
   * TODO add better doc: i think the return is the relative local path
   * @param u
   * @param base
   * @return
   */
  def local(u: jURI, base: jURI): Option[SubdomainSwitch] = {
    if (!u.isAbsolute ) {
      RWWRoutingActor.local(u,base).map(path=>SubdomainSwitch(None,path))
    } else {
      val url = u.toURL
      val baseUrl = base.toURL
      if (url.getProtocol == baseUrl.getProtocol &&
        url.getHost.endsWith(baseUrl.getHost) &&
        url.getDefaultPort == baseUrl.getDefaultPort) {
        val subhost = if (url.getHost == baseUrl.getHost)
          None
        else
          Some(url.getHost.substring(0,url.getHost.length - baseUrl.getHost.length-1) )

        if (subhost == None) RWWRoutingActor.local(u, base).map(p=>SubdomainSwitch(None,p))
        else {
          val path = RWWRoutingActor.cleanDots(u.getPath)
          Option(SubdomainSwitch(subhost,path.mkString("/")))
        }
      } else None
    }
  }

}

/**
 *
 * A actor that receives commands on a server with subdomains, and knows how to ship
 * them off either to the right WebActor or to the right LDPSActor
 *
 * @param baseUri: the base URI of the main domain. From this the subdomains are constructed
 * @param ops
 * @param timeout
 * @tparam Rdf
 */
class RWWRoutingActorSubdomains[Rdf<:RDF](val baseUri: Rdf#URI)
                          (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RWWBaseActor {
  import syntax.URISyntax.uriW
  import RWWRoutingActorSubdomains._

  var rootContainer: Option[ActorRef] = None
  var web : Option[ActorRef] = None


  def receive = returnErrors {
    case ScriptMessage(script) => {
      script.resume match {
        case command: -\/[LDPCommand[Rdf, LDPCommand.Script[Rdf,_]]] => forwardSwitch(CmdMessage(command.a))
        case \/-(res) => sender ! res
      }
    }
    case cmd: CmdMessage[Rdf,_] => forwardSwitch(cmd)
    case WebActorSetterMessage(webActor) => {
      log.info(s"setting web actor to <$webActor> ")
      web = Some(webActor)
    }
    case LDPSActorSetterMessage(ldps) => {
      log.info(s"setting rootContainer to <$ldps> ")
      rootContainer = Some(ldps)
    }
  }

  /** We in fact ignore the R and A types, since we cannot capture */
  protected def forwardSwitch[A](cmd: CmdMessage[Rdf,A]) {
    local(cmd.command.uri.underlying,baseUri.underlying).map { switch =>
      rootContainer match {
        case Some(root) => {
          val pathList = switch.path.split('/').toList
          val p = root.path / switch.subhost.map(_::pathList).getOrElse(pathList)
          val to = context.actorSelection(p)
          log.debug(s"forwarding message $cmd to akka('$switch')=$to received from $sender")
          to.tell(cmd,context.sender)
        }
        case None => log.warning("RWWebActor not set up yet: missing rootContainer")
      }
    } getOrElse {
      //todo: this relative uri comparison is too simple.
      //     really one should look to see if it
      //     is the same host and then send it to the local lpdserver ( because a remote server may
      //     link to this server ) and if so there is no need to go though the external http layer to
      //     fetch graphs
      web.map {
        log.debug(s"sending message $cmd to general web agent <$web>")
        _ forward cmd
      }.getOrElse(log.warning("RWWebActor not set up yet: missing web actor"))
    }

  }

}