package rww.ldp.actor.remote

import akka.actor.ActorRef
import org.w3.banana._
import org.w3.banana.io._
import rww.ldp.actor.common.CommonActorMessages._
import rww.ldp.actor.common.RWWBaseActor
import rww.ldp.model.{LDPR, Meta}
import rww.ldp.{AskLDPC, AskLDPR, ConstructLDPC, ConstructLDPR, CreateContainer, CreateLDPR, DeleteResource, GetMeta, GetResource, SelectLDPC, SelectLDPR, UpdateLDPR, WrappedException, WrongTypeException, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/-}

/**
* A LDP actor that interacts with remote LDP resources
*
* @param excluding base of URIs that the local server is listening to. So don't fetch those URIs externally
* @param webc   fetches resources off the web. Important for testing, among other things.
* @tparam Rdf
*/
class LDPWebActor[Rdf<:RDF](val excluding: Rdf#URI, val webc: WebClient[Rdf])
                           (implicit ops: RDFOps[Rdf], sparqlGraph: SparqlEngine[Rdf,Try,Rdf#Graph],
                            writer: Writer[Rdf#Graph,Try,Turtle],
                            ec: ExecutionContext) extends RWWBaseActor {

  import ops._


  def fetchGraph(uri: Rdf#URI): Future[LDPR[Rdf]] = {
    webc.get(uri).flatMap { res =>
      res match {
        case ldpr: LDPR[Rdf] => Future.successful(ldpr)
        case _ => Future.failed(WrongTypeException("remote resource cannot be transformed to a graph"))
      }
    }
  }

  val ldp = LDPPrefix[Rdf]


  /**
   * Runs a command that can be evaluated on this container.
   * @param cmd the command to evaluate
   * @tparam A The final return type of the script
   * @return a script for further evaluation
   */
  def runCmd[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]]) {
    log.debug(s"in RunLocalCmd - received $cmd")

    def failMsg(e: Throwable, sender: ActorRef, msg: =>String) {
      sender ! akka.actor.Status.Failure({
        e match {
          case be: BananaException => be
          case other: Throwable => WrappedException(msg, other.getCause)
        }
      })
    }
    cmd match {
      case CreateLDPR(container, slugOpt, graph, k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = webc.post(container,slugOpt,graph,Syntax.Turtle)
        result.onComplete{ tryres =>
          tryres match {
            case Success(url) => rwwRouterActor.tell(ScriptMessage(k(url)),sender)
            case Failure(e) => failMsg(e, sender,s"failure creating LDPR with $slugOpt to remote container <$container>")
          }
        }
      }
      case PutLDPR(uri, graph, a) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = webc.put(uri,graph,Syntax.Turtle)
        result.onComplete{ tryres =>
          tryres match {
            case Success(url) => rwwRouterActor.tell(ScriptMessage(a),sender)
            case Failure(e) => failMsg(e, sender,s"failure PUTing LDPR  on remote resource <$uri>")
          }
        }

      }

      //      case CreateBinary(_, slugOpt, mime: MimeType, k) => {
      //        val (uri, pathSegment) = deconstruct(slugOpt)
      //        //todo: make sure the uri does not end in ";aclPath" or whatever else the aclPath standard will be
      //        val bin = PlantainBinary(root, uri)
      //        NonLDPRs.put(pathSegment, bin)
      //        k(bin)
      //      }
      case CreateContainer(container,slugOpt,graph,k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = webc.post(container,slugOpt,graph.union(Graph(Triple(URI(""),rdf.typ, ldp.Container))),Syntax.Turtle)
        result.onComplete{ tryres =>
          tryres match {
            case Success(url) => rwwRouterActor.tell(ScriptMessage(k(url)),sender)
            case Failure(e) => failMsg(e, sender,s"failure creating a container with POST and slug $slugOpt in remote <$container>")
          }
        }

//        val (uri,pathSegment) = deconstruct(slugOpt) //todo: deconstruct should check the file system. This should in fact use a file sytem call
//        val p = root.resolve(pathSegment)
//        Files.createDirectory(p)
//        context.actorOf(Props(new PlantainLDPCActor(uri, p)),pathSegment)
//        k(uri)
      }
      case GetResource(uri,_, k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = webc.get(uri)
        result.onComplete { tryres =>
          tryres match {
            case Success(response) =>  rwwRouterActor.tell(ScriptMessage(k(response)),sender)
            case Failure(e) => {
              failMsg(e, sender,s"failure fetching resource <$uri>")
            }
          }
        }
      }
      case GetMeta(uri, k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        //todo: develop a special fetch that will only do a HEAD
        val result = webc.get(uri)
        result.onComplete { tryres =>
          tryres match {
            case Success(response) => {
              log.debug(s"cache received $response")
              rwwRouterActor.tell(ScriptMessage(k(response.asInstanceOf[Meta[Rdf]])),sender)
            }
            case Failure(e) => failMsg(e, sender,s"failure fetching acl for resource <$uri>")
          }
        }
      }
      case DeleteResource(uri, a) => {
        val sender = context.sender
        val result = webc.delete(uri)
        result.onComplete{ tryres =>
          tryres match {
            case Success(()) => {
              rwwRouterActor.tell(ScriptMessage(a),sender)
            }
            case Failure(e) => failMsg(e, sender,s"failure DELETing remote resource <$uri>")
          }
        }
      }
      case PutLDPR(uri,graph,a) => {

      }
      case UpdateLDPR(uri, remove, add, a) => {
        val sender = context.sender
        sender ! akka.actor.Status.Failure(new NotImplementedError("UpdateLDPR on remote resource not implemented"))
//        val result = webc.patch(uri,remove,add)
//        result.onComplete{ tryres =>
//          tryres match {
//            case Success(()) => {
//              self tell (Scrpt(a),sender)
//            }
//            case Failure(e) => failMsg(e, sender,s"failure PATCHing remote resource <$uri>")
//          }
//        }
      }
      case SelectLDPR(uri, query, bindings, k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = fetchGraph(uri)
        result.onComplete{ tryRes =>
          tryRes.flatMap(ldpr=> sparqlGraph.executeSelect(ldpr.graph,query, bindings)) match {
            case Success(solutions) => {
              rwwRouterActor.tell(ScriptMessage(k(solutions)),sender)
            }
            case Failure(e) => sender ! akka.actor.Status.Failure({
              e match {
                case e: BananaException => e
                case other: Throwable => WrappedException("failure fetching resource", other.getCause)
              }
            })
          }

        }
      }
      case ConstructLDPR(uri, query, bindings, k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = fetchGraph(uri)
        result.onComplete{ tryRes =>
          tryRes.flatMap(ldpr=>sparqlGraph.executeConstruct(ldpr.graph,query, bindings)) match {
            case Success(solutions) => {
              rwwRouterActor tell  (ScriptMessage(k(solutions)),sender)
            }
            case Failure(e) => sender ! akka.actor.Status.Failure({
              e match {
                case e: BananaException => e
                case other: Throwable => WrappedException("failure fetching resource", other.getCause)
              }
            })
          }
        }
      }
      case AskLDPR(uri, query, bindings, k) => {
        val sender = context.sender  //very important. Calling in function onComplete will return deadLetter
        val result = fetchGraph(uri)
        result.onComplete{ tryRes =>
          tryRes.flatMap(ldpr=>sparqlGraph.executeAsk(ldpr.graph,query, bindings)) match {
            case Success(solutions) => {
              rwwRouterActor tell  (ScriptMessage(k(solutions)),sender)
            }
            case Failure(e) => sender ! akka.actor.Status.Failure({
              e match {
                case e: BananaException => e
                case other: Throwable => WrappedException("failure fetching resource", other.getCause)
              }
            })
          }
        }

      }
      case SelectLDPC(_,query, bindings, k) => {
        context.sender ! akka.actor.Status.Failure(new NotImplementedError("SelectLDPC on remote Container not standardised"))
//        val solutions = PlantainUtil.executeSelect(tripleSource, query, bindings)
//        k(solutions)
      }
      case ConstructLDPC(_,query, bindings, k) => {
        context.sender ! akka.actor.Status.Failure(new NotImplementedError("ConstructLDPC on remote Container not standardised"))
//        val graph = PlantainUtil.executeConstruct(tripleSource, query, bindings)
//        k(graph)
      }
      case AskLDPC(_,query, bindings, k) => {
        context.sender ! akka.actor.Status.Failure(new NotImplementedError("AskLDPC on remote Container not standardised"))
        //        val b = PlantainUtil.executeAsk(tripleSource, query, bindings)
        //        k(b)
      }
    }
  }


  /**
   * @param sender the sender of the script
   * @param script the script sent
   * @tparam A  the return value of the script
   * @throws NoSuchElementException if the resource does not exist
   */
  final def run[A](sender: ActorRef, script: LDPCommand.Script[Rdf,A]) {
    script.resume match {
      case -\/(cmd) => {
          runCmd(cmd)
          //if we were to have some commands return an immediate value, then we could do
          // the following with the returned script
          //  run(sender, script)
      }
      case \/-(a) => {
        log.debug(s"returning to $sender $a")
        sender ! a
      }
    }
  }


  def receive = returnErrors {
    case s: ScriptMessage[Rdf,_]  => {
      run(sender, s.script)
    }
    case cmd: CmdMessage[Rdf,_] => {
      runCmd(cmd.command)
    }
  }
}
