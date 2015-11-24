package rww.ldp

import _root_.play.api.libs.iteratee.{Enumerator, Input, Iteratee}
import org.w3.banana._
import rww.ldp.LDPCommand._
import rww.ldp.actor.RWWActorSystem
import utils.Iteratees

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


/**
 * Point in Named Graph
 */
trait PiNG[Rdf <: RDF]  {
  import org.w3.banana.diesel._

  val location: Rdf#URI

  val pointedGraph: PointedGraph[Rdf]

  def pointer = pointedGraph.pointer

  /**
   * create a new PiNG with the same location and graph, but with pointer at location
   * ( there won't necessarily be any statement at that location )
   * @param newPointer the new pointer
   * @return  a new PiNG
   */
  def point(newPointer: Rdf#Node): PiNG[Rdf] = PiNG(location, PointedGraph(newPointer,pointedGraph.graph))

  def document: PiNG[Rdf] = PiNG(location,PointedGraph(location,pointedGraph.graph))

  /**
   * same as / on PointedGraph
   * @param relation
   * @param ops
   * @return iterable of PiNGs
   */
  def / (relation: Rdf#URI)(implicit ops: RDFOps[Rdf]): Iterable[PiNG[Rdf]] =
    (new PointedGraphW(pointedGraph)/relation).map(pg=>PiNG(location,pg))

  /**
   * same as /- on PointedGraph, ie search backwards on this PiNG
   * @param relation
   * @param ops
   * @return iterable of PiNGs
   */
  def /- (relation: Rdf#URI)(implicit ops: RDFOps[Rdf]): Iterable[PiNG[Rdf]] =
    (new PointedGraphW(pointedGraph)/-relation).map(pg=>PiNG(location,pg))

  /**
   * Jump to the definitional graph, ie. if this pointer is not defined in this graph,
   * and is not a literal or a bnode then fetch graph in which pointer is defined.
   * //todo, take into account redirects
   * //todo, optional parameter for web fetches
   * @param rww
   * @return
   */
  def jump(implicit
    ops: RDFOps[Rdf],
    rww: RWWActorSystem[Rdf],
    ec: ExecutionContext): Enumerator[PiNG[Rdf]] = {
    import ops._

    pointedGraph.pointer match {
      case uri: Rdf#URI if (isURI(uri) && uri.fragmentLess!=location) =>
        PiNG(uri.fragmentLess).map(ping=> PiNG(ping.location,PointedGraph(uri,ping.pointedGraph.graph)))
      case _ => Enumerator(this)
    }
  }

  def thisAndJump(implicit ops: RDFOps[Rdf],  rww: RWWActorSystem[Rdf], ec: ExecutionContext) = {
    import ops._

    pointedGraph.pointer match {
      case uri: Rdf#URI if (isURI(uri) && uri.fragmentLess!=location) =>
        Enumerator(this) andThen PiNG(uri.fragmentLess).map(ping=> PiNG(ping.location,PointedGraph(uri,ping.pointedGraph.graph)))
      case _ => Enumerator(this)
    }
  }

  /**
   * follow the relation from this object and jump each node
   * @param relation
   * @param unlessFetchCondition condition when not to jump ( eg: when relation is to a type, form a well known ontology )
   * @return An Enumberation of the resulting jumped PiNGs
   */
  def ~>(relation: Rdf#URI, unlessFetchCondition: PointedGraph[Rdf]=>Boolean=(_=>false))
        (implicit ops: RDFOps[Rdf], rww: RWWActorSystem[Rdf], ec: ExecutionContext)
  : Enumerator[PiNG[Rdf]] = {
    import org.w3.banana.diesel.toPointedGraphW
    val res = pointedGraph/relation
    follow(res, unlessFetchCondition )
  }

  /**
   * Follow the inverse of the relation ( ie look backwards from this node )
   * @param relation
   * @param unlessFetchCondition
   * @return An Enumberation of the resulting jumped PiNGs
   */
  def <~ (relation: Rdf#URI, unlessFetchCondition: PointedGraph[Rdf] => Boolean=(_=>false))
         (implicit rww: RWWActorSystem[Rdf], ops: RDFOps[Rdf],  ec: ExecutionContext)
  : Enumerator[PiNG[Rdf]] = {
    import org.w3.banana.diesel.toPointedGraphW

    val res = pointedGraph/-relation
    follow(res, unlessFetchCondition)
  }

  /**
   * Transform the pointed Graphs to PiNGs. If a graph is remote jump to the remote graph
   * definition.
   *
   * @param res pointed Graphs to follow
   * @return An Enumerator that will feed the results to an Iteratee
   */
  protected
  def follow(res: PointedGraphs[Rdf], unlessFetchCond: PointedGraph[Rdf]=>Boolean)
                      (implicit ops: RDFOps[Rdf], rww: RWWActorSystem[Rdf], ec: ExecutionContext): Enumerator[PiNG[Rdf]] = {
    import ops._
    val local_remote = res.groupBy {
      pg =>
        if (unlessFetchCond(pg)) "local"
        else foldNode(pg.pointer)(
          uri => if (uri.fragmentLess == location)  "local" else "remote",
          bnode => "local",
          lit => "local"
        )
    }
    val localEnum: Enumerator[PiNG[Rdf]] = Enumerator(
      local_remote.get("local").getOrElse(Iterable.empty[PointedGraph[Rdf]]).toSeq.map {
        pg => PiNG(location, pg)
      }: _*)

    /*
     * see discussion http://stackoverflow.com/questions/15543261/transforming-a-seqfuturex-into-an-enumeratorx
     * In order to avoid the whole failing if only one of the Future[LDR]s fails, the failures of the sequence of Futures
     * have to be made visible by a Try. Then the results can be collected by the flt partial function.
     * @param seqFutureLdr: the Sequence of Futures where Failed Futures are mapped to try
     * @param flt: filter out all elements that don't succeed with the partial function
     */
    def toEnumerator[LDR](seqFutureLdr: Seq[Future[Try[LDR]]])(flt: PartialFunction[Try[LDR],LDR])
                         (implicit ec: ExecutionContext) = new Enumerator[LDR] {
      def apply[A](i: Iteratee[LDR, A]): Future[Iteratee[LDR, A]] = {
        Future.sequence(seqFutureLdr).flatMap { seqLdrs: Seq[Try[LDR]] =>
          seqLdrs.collect(flt).foldLeft(Future.successful(i)) {
            case (i, ldr) => i.flatMap(_.feed(Input.El(ldr)))
          }
        }
      }
    }

    local_remote.get("remote").map { remote =>
      val remoteLdrs = remote.map { pg =>
        val pgUri = pg.pointer.asInstanceOf[Rdf#URI]
        val pgDoc = pgUri.fragmentLess
        //todo: the following code does not take redirects into account
        //todo: we need a GET that returns a LinkedDataResource, that knows how to follow redirects
        rww.execute(getLDPR(pgDoc)).map {
          g => PiNG(pgDoc, PointedGraph(pgUri, g))
        }
      }
      //move failures into a Try, so that the toEnumerator method does not fail if one future fails
      val nonFailing: Iterable[Future[Try[PiNG[Rdf]]]] = for {
        futureLdr <- remoteLdrs
      } yield {
        val f: Future[Try[PiNG[Rdf]]] = futureLdr.map(Success(_))
        f recover { case e => Failure(e) }
      }

      val rem = toEnumerator(nonFailing.toSeq) { case Success(ldr) => ldr}
      localEnum andThen rem

    } getOrElse (localEnum)
  }


}


object PiNG {

  /** retrieves a resource based on its URI */
  def apply[Rdf<:RDF](uri: Rdf#URI)
                     (implicit rww: RWWActorSystem[Rdf], ops: RDFOps[Rdf], ec: ExecutionContext)
  : Enumerator[PiNG[Rdf]] = {
    //todo: this code could be moved somewhere else see: Command.GET
    import ops._

    val docUri = uri.fragmentLess
    val script = getLDPR(docUri).map{graph=>
      val pointed = PointedGraph(uri, graph)
      PiNG(docUri, pointed)
    }
    val futurePG: Future[PiNG[Rdf]] = rww.execute(script)
    Iteratees.singleElementEnumerator(futurePG)
  }

  def apply[Rdf<:RDF](_location: Rdf#URI, _pg: PointedGraph[Rdf]): PiNG[Rdf] = new PiNG[Rdf] {
    val location = _location
    val pointedGraph = _pg
  }

  def unapply[Rdf<:RDF](ping: PiNG[Rdf]): Option[(Rdf#URI,PointedGraph[Rdf])] = Some((ping.location,ping.pointedGraph))

  /**
    * todo: Can one do the same here as with PointedGraphs nameley extend Enumerator?
    * @param pings
    * @tparam Rdf
    */
  implicit class PiNGsEnum[Rdf<:RDF](val pings: Enumerator[PiNG[Rdf]]) extends AnyVal {

    def ~>(relation: Rdf#URI,unlessFetchCondition: PointedGraph[Rdf]=>Boolean=(_=>false))
      (implicit ec: ExecutionContext, rww: RWWActorSystem[Rdf], rdfOps: RDFOps[Rdf])
    :  Enumerator[PiNG[Rdf]] =
      pings.flatMap(_~>(relation,unlessFetchCondition))

    def jump(implicit ec: ExecutionContext,rww: RWWActorSystem[Rdf], rdfOps: RDFOps[Rdf]): Enumerator[PiNG[Rdf]] =
      pings.flatMap(_.jump)
  }

  implicit class PingsIter[Rdf<:RDF](val pings: Iterable[PiNG[Rdf]]) extends AnyVal {

    def toEnum(implicit ec: ExecutionContext): Enumerator[PiNG[Rdf]] = Enumerator.enumerate(pings)

    def /(rel:  Rdf#URI)(implicit rdfOps: RDFOps[Rdf]) = pings.flatMap( _ /rel)

  }


}