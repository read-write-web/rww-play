///*
// * Copyright 2012 Henry Story, http://bblfish.net/
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package rww.play.auth
//
//import org.w3.banana._
//import concurrent.{ExecutionContext, Future}
//import rww.play.{ReadWriteWeb, LinkedDataCache}
//import scalaz.{Scalaz, \/}
//import util.Try
//import org.w3.banana.LinkedDataResource
//
//
//object WebACL {
//  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new WebACL(ops)
//}
//
//
//class WebACL[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("acl", "http://www.w3.org/ns/auth/acl#")(ops) {
//  val Authorization = apply("Authorization")
//  val agent = apply("agent")
//  val agentClass = apply("agentClass")
//  val accessTo = apply("accessTo")
//  val accessToClass = apply("accessToClass")
//  val defaultForNew = apply("defaultForNew")
//  val mode = apply("mode")
//  val Access = apply("Access")
//  val Read = apply("Read")
//  val Write = apply("Write")
//  val Append = apply("Append")
//  val accessControl = apply("accessControl")
//  val Control = apply("Control")
//  val owner = apply("owner")
//  val regex = apply("regex")
//  val WebIDAgent = apply("WebIDAgent")
//}
//
//sealed trait Mode
//
//object Mode {
//  implicit def binder[Rdf<:RDF](implicit dsl: Diesel[Rdf],wac: WebACL[Rdf]): PointedGraphBinder[Rdf, Mode] = new PointedGraphBinder[Rdf, Mode] {
//    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[Mode] =
//      Read.binder.fromPointedGraph(pointed) orElse Write.binder.fromPointedGraph(pointed) orElse
//        Control.binder.fromPointedGraph(pointed)
//
//    def toPointedGraph(mode: Mode): PointedGraph[Rdf] = mode match {
//      case Read => Read.binder.toPointedGraph(Read)
//      case Write => Write.binder.toPointedGraph(Write)
//      case Control => Control.binder.toPointedGraph(Control)
//    }
//  }
//}
//
//case object Read extends Mode {
//  implicit def binder[Rdf<:RDF](implicit dsl: Diesel[Rdf],wac: WebACL[Rdf]): PointedGraphBinder[Rdf, Read.type] =
//    dsl.constant(this, wac.Read)
//
//}
//case object Write extends Mode {
//  implicit def binder[Rdf<:RDF](implicit dsl: Diesel[Rdf],wac: WebACL[Rdf]): PointedGraphBinder[Rdf, Write.type] =
//    dsl.constant(this, wac.Write)
//
//}
//case object Control extends Mode {
//  implicit def binder[Rdf<:RDF](implicit dsl: Diesel[Rdf],wac: WebACL[Rdf]): PointedGraphBinder[Rdf, Control.type] =
//    dsl.constant(this, wac.Control)
//
//}
//
//case class NotAuthorized(message: String) extends BananaException
//object NoMatch extends BananaException
//
///**
// * A Guard that gives access to a resource according to the metadata for that resource
// * specified in the WebAccessControl ontology  http://www.w3.org/wiki/WebAccessControl
// *
// * @param cache A Linked Data cache to fetch ACLs on the web
// * @tparam Rdf
// */
//case class WebAccessControl[Rdf<:RDF](rww: ReadWriteWeb[Rdf])//,base: Rdf#URI, , authn: AuthN
//(implicit ops: RDFOps[Rdf], diesel: Diesel[Rdf],ec: ExecutionContext, wac: WebACL[Rdf])
//extends IdGuard[Rdf] {
//  import diesel._
//  import ops._
//
//  val foaf = FOAFPrefix(ops)
//  /**
//   * determine if a subject is a member of the group
//   *
//   * @param request the request which needs to be authenticated
//   * @return The Group of Agents that can access the resource
//   */
//  def allow[A](request: WebRequest[A]): Future[Subject] =  {
//    cache.get(URI(request.meta.toString)).flatMap {acl =>
//      val auths: Seq[Authorization] = authorizations(acl).filter{ auth=>
//        auth.appliesToResource(URI(request.url.toString))
//      }
//      if (!auths.exists(a=> a.modes.contains(request.mode)))
//        Future.failed(NotAuthorized(s"No authorizations found for requested Mode ${request.mode}"))
//      else if (auths.exists(a=> a.public))
//        Future.successful(Anonymous) //the resource is public
//      else {
//        request.subject.flatMap { subj =>
//          val listOfFutures: Seq[Future[WebIDPrincipal]] = auths.map(_.allows(subj,request.mode))
//          val res: Future[Option[WebIDPrincipal]] =  Future.find(listOfFutures)(_=>true)
//          res.flatMap[Subject]{ opt =>
//            opt.fold[Future[Subject]](Future.failed[Subject](NoMatch)){p : WebIDPrincipal =>
//              Future.successful(Subject(subj.principals,List(p)))
//            }
//          }
//        }
//      }
//    }
//  }
//
//  /**
//   * The authorizations found in the webacl.
//   * @return
//   */
//  def authorizations(acl: LinkedDataResource[Rdf]): Seq[Authorization] = {
//       authNodes(acl).toSeq.flatMap { n =>
//         val localGraph = acl.resource.graph
//         val valAuth = PointedGraph(n, localGraph).as[Authorization]
//         if (valAuth.isFailure) System.out.println("incomplete authorization:"+valAuth)
//         valAuth.toOption.map{a=>a.provenance=acl.uri;a }  //todo: UGLY: but need to inject provenance!!!
//       }
//  }
//
//  /**
//   * Find the authorization nodes.
//   * This would be easy with an inferencing graph, as one would just need to
//   * search for the wac:Authorization objects.
//   * This implementations has to do the inferencing. It searches for wac:accessTo and wac:accessToClass
//   * triples only, since we could not get started without those.
//   * @return  the sequence of nodes, as a Set to remove duplicates
//   */
//  protected def authNodes(webacl: LinkedDataResource[Rdf]): Set[Rdf#Node] = {
//     val localGraph = webacl.resource.graph
//     val a = ops.find(localGraph,ANY,toConcreteNodeMatch(wac.accessTo),ANY).map(t=>fromTriple(t)._1).toSet
//     val ac = ops.find(localGraph,ANY,toConcreteNodeMatch(wac.accessToClass),ANY).map(t=>fromTriple(t)._1).toSet
//     a.union(ac)
//  }
//
//  //  implicit class GraphW(pointed: PointedGraph[Rdf]) extends AnyVal {
//  //    import pointed.graph
//  //    def pointer: Rdf#Node = pointed.pointer
//  //
//  //    def \(p: Rdf#URI): PointedGraphs = {
//  //      val nodes = getSubjects(graph, p, pointer)
//  //      new PointedGraphs(nodes, graph)
//  //    }
//  //  }
//
//  /**
//   * property to extract the URI pointed to if one exists
//   *
//   * @param objectBinder
//   * @return
//   */
//  //  def idUri(implicit objectBinder: PointedGraphBinder[Rdf, Rdf#URI]): Property[Rdf, Option[Rdf#URI]] =
//  //    new Property[Rdf, Option[Rdf#URI]] {
//  //      val uri = URI("http://www.w3.org/2002/07/owl#sameAs")
//  //
//  //      def pos(tOpt: Option[Rdf#URI]): Iterable[(Rdf#URI, PointedGraph[Rdf])] = tOpt match {
//  //      case None => Set()
//  //      case Some(t) => Set((uri, PointedGraph(t)(ops)))
//  //    }
//  //    def extract(pointed: PointedGraph[Rdf]): BananaValidation[Option[Rdf#URI]] =
//  //      pointed.pointer.fold(
//  //        uri => Some(uri).success[BananaException],
//  //        bnd => None.success[BananaException],
//  //        lit => None.success[BananaException])
//  //
//  //  }
//
//
//  case class AgentClass(members: Set[Rdf#URI])
//
//  object AgentClass {
//    val members = set[Rdf#URI](foaf("member"))
//
//    implicit val binder: PointedGraphBinder[Rdf, AgentClass] =
//      pgb[AgentClass](members)(AgentClass.apply, AgentClass.unapply)
//
//  }
//
//  case class ResourceSet(regexStr: Option[String] )   {
//    lazy val regexOpt = regexStr.map { _.r }
//    def isMember(uri: Rdf#URI) = {
//      regexOpt.map{_.pattern.matcher(uri.toString).matches}.getOrElse(false)
//    }
//  }
//
//  object ResourceSet {
//    val regex = optional[String](wac.regex)
//
//    implicit val binder: PointedGraphBinder[Rdf, ResourceSet] =
//      pgb[ResourceSet](regex)(ResourceSet.apply, ResourceSet.unapply)
//
//  }
//
//
//
//  case class Authorization(agent: Set[Rdf#URI]= Set.empty, agentClasses: Set[PointedGraph[Rdf]],
//                           accessTo: Set[Rdf#URI]= Set.empty, accessToClass: Set[PointedGraph[Rdf]],
//                           modes: Set[Mode]= Set.empty) {
//    var provenance: Rdf#URI = _
//    /**
//     * must be called after verification that the resource applies to this Authorization
//     * @param subj
//     * @param mode
//     * @return
//     */
//    def allows(subj: Subject, mode: Mode): Future[WebIDPrincipal] = {
//      val resultFutures : List[Future[WebIDPrincipal]] = subj.webIds.map { wid =>
//        if (agent.contains(URI(wid.toString))) Future.successful(WebIDPrincipal(wid))
//        else if (agentClasses exists  { _.pointer == wac.WebIDAgent } ) Future.successful(WebIDPrincipal(wid))
//        else {
//          val res: Future[Option[AgentClass]] = Future.find(setFutureAgents) {
//            agentClass =>
//              agentClass.members.contains(URI(wid.toString))
//          }
//          res.flatMap(_.fold[Future[WebIDPrincipal]]( Future.failed(NoMatch))
//                            (agentcl => Future.successful(WebIDPrincipal(wid)))
//          )
//        }
//      }
//      Future.find(resultFutures)(_=>true).flatMap{ opt =>
//        opt.fold[Future[WebIDPrincipal]](Future.failed[WebIDPrincipal](NoMatch)){p =>
//          Future.successful[WebIDPrincipal](p)
//        }
//      }//just find the first one
//    }
//
//    /**
//     * does this authorization apply to the given resource?
//     * @param uri a uri
//     * @return true if it does
//     */
//    def appliesToResource(uri: Rdf#URI): Boolean = {
//       accessTo.contains(uri) || resourceSet.exists(_.isMember(uri))
//    }
//
//    val public: Boolean = agentClasses.exists(_.pointer == foaf("Agent"))
//
//    protected lazy val resourceSet: Set[ResourceSet] = accessToClass.flatMap { pg =>
//      val groupVal = pg.as[ResourceSet]
//      if (groupVal.isFailure) System.out.println("group is incomplete "+groupVal)
//      groupVal.toOption
//    }
//
//    // only the valid futures
//    protected def setFutureAgents: Set[Future[AgentClass]] = {
//      agentClasses.map { pg =>
//        canonical(pg.pointer).fold(
//          remoteUri => {
//              cache.get(remoteUri).flatMap  { ldr: LinkedDataResource[Rdf] =>
//                ldr.resource.as[AgentClass].asFuture
//              }
//          },
//          r => {
//            pg.as[AgentClass].asFuture
//          }
//        )
//      }
//    }
//
//
//    //todo: this prooves that you really need to know where data comes from, even in the objects,
//    //todoor else you cannot know if information is defined in the object or elsewhere... ?? (3am)
//    private def canonical(node: Rdf#Node): \/[Rdf#URI,Rdf#Node] = {
//      import Scalaz._
//      node.fold(uri=>if (uri.toString.startsWith(provenance.toString)) uri.right else uri.left,
//        _=>node.right,
//        _=>node.right
//      )(ops)
//    }
//  }
//
//  //todo: should be defined in banana
//  implicit class LDR(ldr: LinkedDataResource[Rdf]) {
//    import scalaz._
//    import Scalaz._
//    /**
//     * todo: simple implementation: more complex one may require dealing with redirects
//     * if bnodes were associated with graphs the bnode support would make more sense
//     * (for the moment it just makes it easier to write code to support bnodes)
//     * @param node
//     * @return  Left it URI is defined in remote resource, right otherwise
//     */
//    def canonical(node: Rdf#Node): \/[Rdf#URI,Rdf#Node] = {
//      node.fold(uri=>if (uri.toString.startsWith(ldr.uri.toString)) uri.right else uri.left,
//       _=>node.right,
//       _=>node.right
//      )(ops)
//    }
//  }
//
//  object Authorization {
//    //    implicit val classUris = classUrisFor[Authorization](acl.Authorization)
//
//    val agent = set[Rdf#URI](wac.agent)
//    val agentClass = set[PointedGraph[Rdf]](wac.agentClass)
//    val accessTo = set[Rdf#URI](wac.accessTo)
//    val accessToClass = set[PointedGraph[Rdf]](wac.accessToClass)
//    val modes = set[Mode](wac.mode)
//
//    implicit val binder: PointedGraphBinder[Rdf, Authorization] =
//      pgb[Authorization](agent, agentClass,accessTo,accessToClass,modes)(Authorization.apply, Authorization.unapply)
//
//  }
//
//}
//
//
//
///**
// * A very silly check that just requires the user to be authenticated with TLS
// * if the path starts with "/a"
// */
////case class ACheck(req: RequestHeader) extends Check {
////
////  def request(subj: => Futur[Subject]): Boolean = req.path.startsWith("/a")
////
////  def findSubject = req.certs.map{certs=>
////     val subj = new Subject()
////     subj.getPublicCredentials.add(certs)
////     subj.getPrincipals.add(PubKeyPrincipal(certs(0)))
////     subj
////  }
////
////}
////
/////** A principal where the identifier is just the public key itself */
////case class PubKeyPrincipal(cert: Certificate) extends Principal {
////  //would be good to find a better way to write this out
////  def getName = cert.getPublicKey.toString
////}