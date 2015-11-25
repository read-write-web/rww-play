package rww.ldp.auth

import java.net.{URI => jURI}
import java.security.Principal
import java.util.regex.Pattern

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.w3.banana._
import play.Logger
import play.api.libs.iteratee._
import rww.ldp.LDPExceptions.{MissingACLException, NoAuthorization}
import rww.ldp.{LDPCommand, PiNG, WebResource}
import rww.play.auth.Subject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}


// TODO not appropriate place
object Method extends Enumeration {
  val Read = Value
  val Write = Value
  val Append = Value
}


/**
 * WACAuthZ groups methods to find the authorized WebIDs for a particular resource
 * using a simple implementation of WebAccessControl http://www.w3.org/wiki/WebAccessControl
 *
 * @param web object that makes it easy to access web resources (local or remote) asynchronously
 * @param ops
 * @tparam Rdf
 */
class WACAuthZ[Rdf <: RDF](web: WebResource[Rdf])(implicit ops: RDFOps[Rdf]) extends LazyLogging {

  import LDPCommand._
  import ops._

  implicit val rww = web.rwwActorSys

  import org.w3.banana.diesel._


  val foaf = FOAFPrefix[Rdf]
  val wac  = WebACLPrefix[Rdf]
  val cert = CertPrefix[Rdf]
  val rdfs = RDFSPrefix[Rdf]


  /**
    * Is subject authorized to do action in mode `mode` on resource
    * @return first authorized principal found
    */
  def isAuthorized(subject: Subject, mode: Method.Value, on: Rdf#URI): Future[Principal] = {
    val result = aclFor(on).flatMap {
      case None => Future.failed(MissingACLException(new jURI(on.toString)))
      case Some(aclDoc) => {
        val futureP = allowsMethodForSubject(subject, aclDoc.point(wacIt(mode)), on) run Iteratee.head
        futureP flatMap {
          case None => Future.failed(NoAuthorization(subject, new jURI(on.toString), mode))
          case Some(principal) => Future.successful(principal)
        }
      }
    }
    result.onComplete {
      case x => Logger.info(s"~~~> authorizedPrincipals($subject,$mode,$on)=$x")
    }
    result
  }

  /**
    * ( very close to isAuthorized )
    * @return the set of principals allowed access in given mode on given resource
    */
  def authorizedPrincipals(subject: Subject, mode: Method.Value, on: Rdf#URI): Future[Set[Principal]] =
    aclFor(on).flatMap {
      case None =>
        Future.failed(MissingACLException(new jURI(on.toString)))
      case Some(aclDoc) => {
        val futureList = allowsMethodForSubject(subject, aclDoc.point(wacIt(mode)), on) run Iteratee.getChunks
        futureList flatMap {
          case Nil => Future.failed(NoAuthorization(subject, new jURI(on.toString), mode))
          case list => Future.successful(list.toSet)
        }
      }
    }


  /**
    * //todo: note what happens if there is more than one acl in the header?
    * @param uri the URL of the resource for which we are seeking the acl
    * @return as a future the acl as a Pointer to the named graph itself
    */
  def aclFor(uri: Rdf#URI): Future[Option[PiNG[Rdf]]] = rww.execute {
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


  def wacIt(mode: Method.Value) = mode match {
    case Method.Read => wac.Read
    case Method.Write => wac.Write
    case Method.Append => wac.Append
  }


    /**
     * method to test if an ACL graph allows access for the given mode to the given user
     * @param aclMode selected mode pointing into the acl
     * @param on the resource on which access is being requested
     * @param subject the subject requiring access
     * @return the first Principal that was found to be enabled
     **/
    protected
    def allowsMethodForSubject(
      subject: Subject,
      aclMode: PiNG[Rdf],
      on: Rdf#URI,
      recursionMax: Int = 5
    ): Enumerator[Principal] = {
      if (recursionMax <= 0) return Enumerator()
      // It would be interesting to return a Future[Proof]
      // An initial way to think of a Proof would be as a path of PiNGs and relations used
      // to get from one to the other side ending with a Principals of the Subject
      // Proof = (Path[PiNG],Principal)
      // But a simple linear path may not be enough: we may need a Tree.
      // For example for Access Control Rules we need to find the acl that gives access to
      // a given resource in a given mode, and from there find the agent, identified either
      // by webID, or indirectly by keyId, or even the key's information, by e-mail address, by
      // openid, etc, etc... ( one would need to determine when jumps are ok, and when not ).
      //
      // So for example if we have a PubKeyPrincipal then we have a proof if we have an ACL
      // for a given resource and a given mode, and that points either to a
      //
      //  a. rule ~- wac:agent ~-> [ ~- cert:key -~> [ cert:modulus m; cert:exp ?p ] ].
      //  b. rule ~- wac:agentClass -> [ ~- foaf:member [ ~- cert:key -~> [ ... ] ]
      //
      // In the above one can use the information locally or one can jump at every stage, both will be
      // ok. That is if the acl tells us only someone in that graph with the given pk, or if the key
      // is published remotely, it does not matter.
      // ( well if the url of the local key clashes with the remote one, then there
      // is a potentially dangerous inconsistency )
      //
      // More precisely It should be a (Path[Pattern],Principal) where Pattern is a pattern
      // in a graph. eg: one has to start with finding the

      val modeBeingConsidered = aclMode.pointer.asInstanceOf[Rdf#URI]
      assert (List(wac.Read,wac.Write,wac.Control,wac.Append).exists(_ == modeBeingConsidered))

      val authsForMode = aclMode /- wac.mode
      // filter those pointedGraphs that give access to the required resource
      val authsResourceAndMode = authsForMode
        .filter(ping => authorizationPermitsAccessToResource(on)(ping.pointedGraph))

      /**
        * given an agent pointer, find if from there one can find a match for the verified principals
        * @return an enumeration of successful principals
        */
      def agentToPrincipal(agentPng: PiNG[Rdf]): Enumerator[Principal] =
        Enumerator(subject.principals.toSeq: _*).flatMap { principal =>
          principal match {
            case w@WebIDPrincipal(wid) if wid.toUri == agentPng.pointer =>
              Enumerator[Principal](w)
            case k@WebKeyPrincipal(key) =>
              (agentPng / cert.key).toEnum.jump.flatMap { keyPng =>
                if (keyPng.pointer == key.toUri)
                  Enumerator[Principal](k)
                else Enumerator.empty[Principal]
              }
            case other => Enumerator.empty[Principal]
          }
        }


      val agentPrincipals: Enumerator[Principal] = (authsResourceAndMode/wac.agent).toEnum
        .flatMap(agentToPrincipal)


      val classPrincipals:  Enumerator[Principal] =
        (authsResourceAndMode/wac.agentClass).toEnum.flatMap{agentClass=>
         if(agentClass.pointer == foaf.Agent)
           Enumerator(Agent) //<-- allows everyone
         else {
           agentClass.thisAndJump.flatMap(ac=> (ac/foaf.member).toEnum).flatMap(agentToPrincipal)
         }
       }

      val all = agentPrincipals interleave( classPrincipals)

      //danger of infinite recursion
      val seeAlsoAnswers = (aclMode.document~>(wac.include)).flatMap{ doc =>
        val modePointer = doc.point(aclMode.pointer)
        allowsMethodForSubject(subject, modePointer, on, recursionMax-1)
      }

      all interleave seeAlsoAnswers
    }



}

