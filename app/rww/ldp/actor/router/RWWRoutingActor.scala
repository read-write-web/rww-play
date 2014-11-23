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


object RWWRoutingActor {

  def path(uPath: String, basePath: String) =
    if (uPath.startsWith(basePath)) {
      val res = uPath.substring(basePath.size)
      val treated = cleanDots(res)
      Option(treated.mkString("/"))
    } else None

  /**
   * We compare the uri u to the base URI, and if this uri seems local to the base uri,
   * this means that the uri content can be retrieved on the filesystem, and not on a remote host
   * Thus we return the local path of the resource
   * TODO add better doc: i think the return is the relative local path
   * @param u
   * @param base
   * @return
   */
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
class RWWRoutingActor[Rdf<:RDF](val baseUri: Rdf#URI)
                          (implicit ops: RDFOps[Rdf], timeout: Timeout) extends RWWBaseActor {
  import rww.ldp.actor.router.RWWRoutingActor._
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
    // TODO do we really need to be able to change the actorRef of these at runtime? it could be simpler to simply use Props?
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
  // TODO doc! local vs remote
  protected def forwardSwitch[A](cmd: CmdMessage[Rdf,A]) {
    import ops._
    implicit def toJURI(u: Rdf#URI) = new jURI(u.getString)
    local(cmd.command.uri,baseUri).map { path=>
      rootContainer match {
        case Some(root) => {
          val p = root.path / path.split('/').toIterable
          val to = context.actorFor(p)
          to.tell(cmd,context.sender)
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
        log.info(s"sending message $cmd to general web agent <$web>")
        _ forward cmd
      }.getOrElse {
        val msg = "RWWebActor not set up yet: missing web actor"
        log.warning(msg)
        context.sender ! akka.actor.Status.Failure(ServerException(msg))
      }
    }

  }

}

