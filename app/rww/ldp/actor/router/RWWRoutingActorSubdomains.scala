package rww.ldp.actor.router

import java.net.{URI => jURI}

import akka.actor.ActorRef
import akka.util.Timeout
import org.w3.banana.{RDF, RDFOps}
import rww.ldp.LDPExceptions.ServerException
import rww.ldp._
import rww.ldp.actor.common.CommonActorMessages._
import rww.ldp.actor.common.RWWBaseActor

import scalaz.{-\/, \/-}

object RWWRoutingActorSubdomains {

  /**
   *
   * @param subhost Optionally a subhost path
   * @param path a string that is the path to the actor
   */
  case class SubdomainSwitch(subhost: Option[String], path: String) {
    lazy val lcSubHost = subhost.map ( sh => sh.toLowerCase() )
  }

  /**
   * We compare the uri u to the base URI, and if this uri seems local to the base uri,
   * this means that the uri content can be retrieved on the filesystem, and not on a remote host
   * Thus we return the local path of the resource
   * @param u the url for which we are seeking the actor name
   * @param rootLDPCuri the base url of the root LDPC
   * @return a SubdomainSwitch
   */
  def local(u: jURI, rootLDPCuri: jURI): Option[SubdomainSwitch] = {
    if (!u.isAbsolute ) {
      RWWRoutingActor.local(u,rootLDPCuri).map(path=>SubdomainSwitch(None,path))
    } else {
      val url = u.toURL
      val baseUrl = rootLDPCuri.toURL
      if (url.getProtocol == baseUrl.getProtocol &&
        url.getHost.endsWith(baseUrl.getHost) &&
        url.getDefaultPort == baseUrl.getDefaultPort) {
        val subhost = if (url.getHost == baseUrl.getHost)
          None
        else
          Some(url.getHost.substring(0,url.getHost.length - baseUrl.getHost.length-1) )

        if (subhost == None) RWWRoutingActor.local(u, rootLDPCuri).map(p=>SubdomainSwitch(None,p))
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
  import rww.ldp.actor.router.RWWRoutingActorSubdomains._

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

  var msg: String = _

  /** We in fact ignore the R and A types, since we cannot capture */
  protected def forwardSwitch[A](cmd: CmdMessage[Rdf,A]) {
    local(new jURI(cmd.command.uri.toString),new jURI(baseUri.toString)).map { switch =>
      rootContainer match {
        case Some(root) => {
          val pathList = switch.path.split('/').toList
          val pathListWithSubdomain = switch.lcSubHost.map(_ :: pathList).getOrElse(pathList)
          val actorPath = root.path / pathListWithSubdomain
          val to = context.actorFor(actorPath)
          to.tell(cmd, context.sender)
        }
        case None => {
          val msg = "RWWebActor not set up yet: missing rootContainer"
          log.warning(msg)
          context.sender ! akka.actor.Status.Failure(ServerException(msg))
        }
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
      }.getOrElse{
        msg = "RWWebActor not set up yet: missing web actor"
        log.warning(msg)
        context.sender ! akka.actor.Status.Failure(ServerException(msg))
      }
    }

  }


}