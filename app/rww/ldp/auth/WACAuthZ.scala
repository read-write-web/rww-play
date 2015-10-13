package rww.ldp.auth

import java.util.regex.Pattern

import com.typesafe.scalalogging.slf4j.Logging
import org.w3.banana._
import play.Logger
import play.api.libs.iteratee._
import rww.ldp.{LDPCommand, PiNG, PiNGs, WebResource}
import rww.play.Method
import utils.Iteratees

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/**
 * WACAuthZ groups methods to find the authorized WebIDs for a particular resource
 * using a simple implementation of WebAccessControl http://www.w3.org/wiki/WebAccessControl
 *
 * @param web object that makes it easy to access web resources (local or remote) asynchronously
 * @param ops
 * @tparam Rdf
 */
class WACAuthZ[Rdf <: RDF](web: WebResource[Rdf])(implicit ops: RDFOps[Rdf]) extends Logging {

  import LDPCommand._
  import ops._

  implicit val rww = web.rwwActorSys

  import org.w3.banana.diesel._


  val foaf = FOAFPrefix[Rdf]
  val wac = WebACLPrefix[Rdf]
  val rdfs = RDFSPrefix[Rdf]

  /**
   * Returns a Script for authentication that looks in the metadata file for a resource
   * to see what agents have access to a given resource in a given manner, following
   * resources.
   *
   *
   * but is not tail rec because of flatMap
   * @param aclUri metadata
   * @param method the method of access asked for ( in wac ontology )
   * @param on the resource to which access is requested
   * @return a Free based recursive structure that will return a list of agents ( identified by WebIDs. )
   **/
  def getAuth(aclUri: Rdf#URI, method: Rdf#URI, on: Rdf#URI): Future[List[Rdf#URI]] = {
    logger.debug(s"Will try to get the authorized agents for $method on $on with Acl URI = $aclUri")
    val acl: Enumerator[LinkedDataResource[Rdf]] = web ~ (aclUri)
    val authzWebIds = getAuthEnum(acl, method, on)
    Iteratees.enumeratorAsList(authzWebIds)
  }


  protected
  def getAuthEnum(acls: Enumerator[LinkedDataResource[Rdf]],
                  method: Rdf#URI, on: Rdf#URI): Enumerator[Rdf#URI] = {
    acls.flatMap { ldr =>
      authzWebIDs(ldr, on, method)
    }

  }


  /**
   * getAuth for a resource ( fetch metadata for it )
   * @param resource the resource to check authorization for
   * @param method the type of access requested
   * @return the list of WebIDs for which authentication on the given resource is allowed for the specified method.
   */
  def getAuthorizedWebIDsFor(resource: Rdf#URI, method: Rdf#URI): Future[List[Rdf#URI]] = {
    val authzWebIds = getAuthEnum(acl(resource), method, resource)
    Iteratees.enumeratorAsList(authzWebIds)
  }

  /** retrieves the acl for a resource */
  def acl2(uri: Rdf#URI): Enumerator[PiNG[Rdf]] = {
    val futureACL: Future[Option[PiNG[Rdf]]] = rww.execute {
      //todo: this code could be moved somewhere else see: Command.GET
      val docUri = uri.fragmentLess
      getMeta(docUri).flatMap { m =>
        val x: LDPCommand.Script[Rdf, Option[PiNG[Rdf]]] = m.acl match {
          case Success(aclUri) => {
            logger.info(s"Resource $uri claims its ACLs are stored at $aclUri")
            getLDPR(aclUri).map { g =>
              Some(PiNG(aclUri, PointedGraph(aclUri, g)))
            }
          }
          case _ => `return`(None)
        }
        x
      }
    }
    //todo we require an execution context here - I imported a global one, but user choice may be better
    new Enumerator[PiNG[Rdf]] {
      def apply[A](i: Iteratee[PiNG[Rdf], A]): Future[Iteratee[PiNG[Rdf], A]] =
        futureACL.flatMap { aclOpt =>
          i.feed(aclOpt match {
            case Some(ldr) => Input.El(ldr)
            case None => Input.Empty
          })
        }
    }
  }

  /** retrieves the acl for a resource */
  def acl(uri: Rdf#URI): Enumerator[LinkedDataResource[Rdf]] = {
    logger.debug(s"Will try to get Acls for $uri")
    val futureACL: Future[Option[LinkedDataResource[Rdf]]] = rww.execute {
      //todo: this code could be moved somewhere else see: Command.GET
      val docUri = uri.fragmentLess
      getMeta(docUri).flatMap { m =>
        val x: LDPCommand.Script[Rdf, Option[LinkedDataResource[Rdf]]] = m.acl match {
          case Success(aclUri) => {
            logger.info(s"Resource $uri claims its ACLs are stored at $aclUri")
            getLDPR(aclUri).map { g =>
              Some(LinkedDataResource(aclUri, PointedGraph(aclUri, g)))
            }
          }
          case _ => `return`(None)
        }
        x
      }
    }
    //todo we require an execution context here - I imported a global one, but user choice may be better
    new Enumerator[LinkedDataResource[Rdf]] {
      def apply[A](i: Iteratee[LinkedDataResource[Rdf], A]): Future[Iteratee[LinkedDataResource[Rdf], A]] =
        futureACL.flatMap { aclOpt =>
          i.feed(aclOpt match {
            case Some(ldr) => Input.El(ldr)
            case None => Input.Empty
          })
        }
    }
  }

  /**
   * Create a  Enumeratee filter that filters PointedGraph Enumerators, pointing to ACLs
   * @param on  the resource to which access is requested
   * @return an Enumeratee of those PointedGraphs giving access to the given resource
   */
  protected
  def accessToResourceFilterFor(on: Rdf#URI) = Enumeratee.filter[PointedGraph[Rdf]] {
    authorizationPermitsAccessToResource(on)
  }

  /**
   * tests if authorizations permit access to a given resource
   * @param on the resource on which authorization is requested
   * @param aclPG the authorization pg
   * @return true if the aclpPG gives authorization on on
   */
  def authorizationPermitsAccessToResource(on: Rdf#URI)(aclPG: PointedGraph[Rdf]): Boolean = {
    (aclPG / wac.accessTo).exists(_.pointer == on) ||
      (aclPG / wac.accessToClass).exists { clazzPg =>
        (clazzPg / wac.regex).exists { regexPg =>
          foldNode(regexPg.pointer)(
            uri => false,
            bnode => false,
            lit => Try(Pattern.compile(lit.lexicalForm).matcher(on.toString).matches()).getOrElse(false))
        }
      }
  }

  /**
   * return the list of agents that are allowed access to the given resource
   * stop looking if everybody is authorized
   * @param acldr the graph which contains the acl rules
   * @param on the resource on which access is being requested
   * @param method the type of access requested
   * @return A list of Agents with access ( sometimes just an Agent )
   **/
  protected
  def authzWebIDs(acldr: LinkedDataResource[Rdf], on: Rdf#URI, method: Rdf#URI): Enumerator[Rdf#URI] = {
    val authDefs = Enumerator((PointedGraph(method, acldr.resource.graph) /- wac.mode).toSeq: _*)

    // filter those pointedGraphs that give access to the required resource
    val accessToResourceFilter = accessToResourceFilterFor(on)

    val relevantAcls: Enumerator[PointedGraph[Rdf]] = authDefs.through(accessToResourceFilter)

    //follow the wac.agent relations add those that are not bnodes to a list
    val agents: Enumerator[List[Rdf#URI]] = agentsEnabledBy(relevantAcls)

    val agentClassLDRs: Enumerator[LinkedDataResource[Rdf]] =
      relevantAcls.flatMap(pg => web.~>(LinkedDataResource(acldr.location, pg), wac.agentClass) {
        _.pointer != foaf.Agent
      })

    val seeAlso: Enumerator[Rdf#URI] = for {
      ldr <- web.~>(acldr, wac.include)()
      uri <- authzWebIDs(ldr, on, method)
    } yield {
      uri
    }

    val groupMembers: Enumerator[List[Rdf#URI]] = extractGroupMembers(agentClassLDRs)

    //todo: stop at the first discovery of a foaf:Agent?
    //todo: collapse all agents into one foaf:Agent

    (agents andThen groupMembers).flatMap(uris => Enumerator(uris.toSeq: _*)) andThen seeAlso
  }


  /**
   * Transform an Enumerator of PointedGraphs pointing each to an ACL to an Enumerators of WebIDs it enables access to
   * @param relevantAcls the acls as Enumerators of PGs
   * @return an Enumerator of WebIDs it enables directly ( via wac.agent relation )
   */
  protected
  def agentsEnabledBy(relevantAcls: Enumerator[PointedGraph[Rdf]]): Enumerator[List[Rdf#URI]] =
    relevantAcls.map[List[Rdf#URI]] { pg =>
      val webids = (pg / wac.agent).collect { case PointedGraph(p, _) if isURI(p) => p.asInstanceOf[Rdf#URI]}
      webids.toList
    }

  /**
   * Transform an Enumerator of LinkedDataResources pointing each to an agent class, to an Enumerator of those
   * WebIDs that are members of that group.
   * @param agentClassLDRs an Enumerator of LDR
   * @return
   */
  protected
  def extractGroupMembers(agentClassLDRs: Enumerator[LinkedDataResource[Rdf]]): Enumerator[List[Rdf#URI]] =
    agentClassLDRs.map { ldr =>
      val webids = if (ldr.resource.pointer == foaf.Agent) Iterable(foaf.Agent) //todo <- here we can stop
      else (ldr.resource / foaf.member).collect { case PointedGraph(p, _) if isURI(p) => p.asInstanceOf[Rdf#URI]}
      webids.toList
    }

  def wacIt(mode: Method.Value) = mode match {
    case Method.Read => wac.Read
    case Method.Write => wac.Write
    case Method.Append => wac.Append
  }

  /**
   *
   * @param on   resource for which we are looking for rights
   * @param user verified WebIds of the authenticated user, or the empty list
   * @return a Future which will contain the list of wac:modes allowed: wac:Read, wac:Write, wac:Append
   */
  def getAllowedMethodsForAgent(on: Rdf#URI, user: List[WebIDPrincipal])
  : Future[Set[Method.Value]] = {
    //todo: move this to a more specialised class
    def toMethod(modeUri: Rdf#URI): Option[Method.Value] = {
      modeUri match {
        case wac.Read => Some(Method.Read)
        case wac.Write => Some(Method.Write)
        case wac.Append => Some(Method.Append)
        case _ => None
      }
    }
    //todo: it would be nice to have this process stop as soon as it has all the answers
    val authzMethods = getAllowedMethodsForAgentEnum(acl2(on), on, user.map(p => URI(p.webid.toString)))
    Iteratees.enumeratorAsList(authzMethods).map { listOfModes =>
      Logger.info(s"getAllowedMethodsForAgent($on,$user) is $listOfModes")
      listOfModes.flatMap(toMethod(_)).toSet
    }
  }

  /**
   *
   * @param acls  Enumeration of LinkedDataResources to be looked into to find the information
   * @param on: the uri on which a request is being made
   * @param user verified WebIds of the authenticated user, or the empty list
   * @return a Enumerator of the list of wac:modes allowed: wac:Read, wac:Write, wac:Append
   */
  protected
  def getAllowedMethodsForAgentEnum(acls: Enumerator[PiNG[Rdf]],
                                    on: Rdf#URI,
                                    user: List[Rdf#URI]): Enumerator[Rdf#URI] = {
    acls.flatMap { aclPing =>
      val aclsInGraph = Enumerator(wac.Read, wac.Write, wac.Control, wac.Append)
        .map(ctrl => aclPing.point(ctrl)) &> allowsMethodForAgent2(on, user)

      val seeAlso = (aclPing.document ~> wac.include) &> Enumeratee.filter[PiNG[Rdf]](p => ops.isURI(p.location))
      aclsInGraph andThen getAllowedMethodsForAgentEnum(seeAlso, on, user)
    }
  }

  def allowsMethodForAgent2(on: Rdf#URI,
                            webids: List[Rdf#URI])
  : Enumeratee[PiNG[Rdf], Rdf#URI] = {

    Enumeratee.mapFlatten { mode =>
      val modeBeingConsidered = mode.pointedGraph.pointer.asInstanceOf[Rdf#URI]
      assert(List(wac.Read, wac.Write, wac.Control, wac.Append).exists(_ == modeBeingConsidered))

      val authsForMode = mode /- wac.mode
      // filter those pointedGraphs that give access to the required resource
      val authsResourceAndMode = authsForMode.filter(ping => authorizationPermitsAccessToResource(on)(ping.pointedGraph))

      val modeEnabled = authsResourceAndMode.exists { authsPg =>
        (authsPg / wac.agent).exists(pg => webids.contains(pg.pointedGraph.pointer))
      }

      if (modeEnabled) Enumerator(modeBeingConsidered)
      else {
        val agentClassesPGsEnums = PiNGs(authsResourceAndMode) ~>(wac.agentClass, _.pointer == foaf.Agent)
        val findMode = Enumeratee.mapFlatten[PiNG[Rdf]][Rdf#URI] { agentClassPG: PiNG[Rdf] =>
          val result: Enumerator[Rdf#URI] = if (agentClassPG.pointedGraph.pointer == foaf.Agent) {
            Enumerator(modeBeingConsidered)
          } else {
            val containsUser = (agentClassPG.pointedGraph / foaf.member).exists { pg =>
              pg.pointer.fold(u=>webids.contains(u),_=>false,_=>false)
            }
            if (containsUser)
              Enumerator(modeBeingConsidered)
            else
              Enumerator.empty
          }
          result
        }
        agentClassesPGsEnums &> findMode
      }

    }
  }


  //  /**
  //   * method to test if an ACL graph allows access for the given mode to the given user
  //   * @param mode selected mode in the Named Graph
  //   * @param on the resource on which access is being requested
  //   * @param webids the webids of the user requesting access
  //   * @return the mode if it is allowed
  //   **/
  //  protected
  //  def allowsMethodForAgent(mode: PiNG[Rdf],
  //                           on: Rdf#URI,
  //                           webids: List[Rdf#URI]): Future[(Rdf#URI,Boolean)] = {
  //
  //    val modeBeingConsidered = mode.pointedGraph.pointer.asInstanceOf[Rdf#URI]
  //    assert (List(wac.Read,wac.Write,wac.Control,wac.Append).exists(_ == modeBeingConsidered))
  //
  //    val authsForMode = mode /- wac.mode
  //    // filter those pointedGraphs that give access to the required resource
  //    val authsResourceAndMode = authsForMode.filter(ping=> authorizationPermitsAccessToResource(on)(ping.pointedGraph))
  //
  //    val modeEnabled = authsResourceAndMode.exists { authsPg =>
  //      (authsPg / wac.agent).exists(pg => webids.contains(pg.pointedGraph.pointer))
  //    }
  //
  //    if (modeEnabled) return Future.successful((modeBeingConsidered,true))
  //    else {
  //       val agentClassesPGs = PiNGs(authsResourceAndMode)~>(wac.agentClass,_.pointer!=foaf.Agent)
  //       val folder = Iteratee.fold2[PiNG[Rdf],(Rdf#URI,Boolean)]((modeBeingConsidered,false)){
  //         (cont,agentClassPG)=>
  //           val result = if (agentClassPG.pointedGraph.pointer == foaf.Agent) {
  //             true
  //           } else {
  //              (agentClassPG.pointedGraph/foaf.member).exists { pg =>
  //                pg.pointer match {
  //                  case u: Rdf#URI => webids.contains(u)
  //                  case _ => false
  //                }
  //             }
  //           }
  //         Future.successful(((modeBeingConsidered,result),result))
  //       }
  //
  //       agentClassesPGs |>>> folder
  //    }
  //
  ////    val seeAlso: Enumerator[Rdf#URI] = for {
  ////      ldr <- web.~>(acldr, wac.include)()
  ////      uri <- authzAllWebIDs(ldr, on)
  ////    } yield {
  ////      uri
  ////    }
  ////
  ////    val groupMembers: Enumerator[List[Rdf#URI]] = extractGroupMembers(agentClassLDRs)
  ////
  ////    //todo: stop at the first discovery of a foaf:Agent?
  ////    //todo: collapse all agents into one foaf:Agent
  ////
  ////    (agents andThen groupMembers).flatMap(uris => Enumerator(uris.toSeq: _*)) andThen seeAlso
  //  }


}

