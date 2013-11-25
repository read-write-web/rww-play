package rww.ldp

import org.w3.banana.{syntax, RDFOps, RDF}
import akka.util.Timeout
import akka.actor.ActorRef
import scalaz.{\/-, -\/}
import java.net.{URI=>jURI}

object RWWebActorSubdomains {
  case class Switch(subhost: Option[String], path: String)

  def local(u: jURI, base: jURI): Option[Switch] = {

    if (!u.isAbsolute ) {
      RWWebActor.local(u,base).map(path=>Switch(None,path))
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

        if (subhost == None) RWWebActor.local(u, base).map(p=>Switch(None,p))
        else {
          val path = RWWebActor.cleanDots(u.getPath)
          Option(Switch(subhost,path.mkString("/")))
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
class RWWebActorSubdomains[Rdf<:RDF](val baseUri: Rdf#URI)
                          (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RActor {
  import syntax.URISyntax.uriW
  import RWWebActorSubdomains._

  var rootContainer: Option[ActorRef] = None
  var web : Option[ActorRef] = None


  def receive = returnErrors {
    case Scrpt(script) => {
      script.resume match {
        case command: -\/[LDPCommand[Rdf, LDPCommand.Script[Rdf,_]]] => forwardSwitch(Cmd(command.a))
        case \/-(res) => sender ! res
      }
    }
    case cmd: Cmd[Rdf,_] => forwardSwitch(cmd)
    case WebActor(webActor) => {
      log.info(s"setting web actor to <$webActor> ")
      web = Some(webActor)
    }
    case LDPSActor(ldps) => {
      log.info(s"setting rootContainer to <$ldps> ")
      rootContainer = Some(ldps)
    }
  }

  /** We in fact ignore the R and A types, since we cannot capture */
  protected def forwardSwitch[A](cmd: Cmd[Rdf,A]) {
    local(cmd.command.uri.underlying,baseUri.underlying).map { switch =>
      rootContainer match {
        case Some(root) => {
          val pathList = switch.path.split('/').toList
          val p = root.path / switch.subhost.map(_::pathList).getOrElse(pathList)
          val to = context.actorSelection(p)
          log.info(s"forwarding message $cmd to akka('$switch')=$to ")
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
        log.info(s"sending message $cmd to general web agent <$web>")
        _ forward cmd
      }.getOrElse(log.warning("RWWebActor not set up yet: missing web actor"))
    }

  }


}