package rww.ldp

import org.w3.banana.{syntax, RDFOps, RDF}
import akka.util.Timeout
import akka.actor.ActorRef
import scalaz.{\/-, -\/}
import java.net.{URI=>jURI}

object RWWebActor {

  def path(uPath: String, basePath: String) =
    if (uPath.startsWith(basePath)) {
      val res = uPath.substring(basePath.size)
      val treated = cleanDots(res)
      Option(treated.mkString("/"))
    } else None


  def local(u: jURI, base: jURI): Option[String] = {
    if (!u.isAbsolute) {
      path(u.getPath,base.getPath)
    } else {
      val url = u.toURL
      val baseUrl = base.toURL

      val res = if (url.getProtocol == baseUrl.getProtocol &&
        url.getHost.endsWith(baseUrl.getHost) &&
        url.getDefaultPort == baseUrl.getDefaultPort) {
         path(url.getPath,base.getPath)
      } else None
      res
    }
  }


  def cleanDots(path: String) = {
    val split = path.split('/').toIndexedSeq

    val sections = if (split.length>0 && split(0).isEmpty) split.tail else split
    val fileName = sections.lastOption.getOrElse("")
    var idot = fileName.indexOf('.')
    if (idot > 0) {
      sections.updated(sections.length - 1, fileName.substring(0, idot))
    } else if (idot == 0){
      sections.take(sections.length-1)
    } else sections
  }
}

/**
 *
 * A actor that receives commands and ships them off either to a WebActor or to an LDPSActor
 *
 * @param baseUri
 * @param ops
 * @param timeout
 * @tparam Rdf
 */
class RWWebActor[Rdf<:RDF](val baseUri: Rdf#URI)
                          (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RActor {
  import syntax.URISyntax.uriW
  import RWWebActor._

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
    local(cmd.command.uri.underlying,baseUri.underlying).map { path=>
      rootContainer match {
        case Some(root) => {
          val p = root.path / path.split('/').toIterable
          val to = context.actorSelection(p)
          log.info(s"forwarding message $cmd to akka('$path')=$to ")
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