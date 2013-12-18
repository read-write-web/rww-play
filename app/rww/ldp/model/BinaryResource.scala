package rww.ldp.model

import org.w3.banana.{PointedGraph, RDFOps, MimeType, RDF}
import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.{Enumerator, Iteratee}
import java.nio.file.{StandardCopyOption, StandardOpenOption, Files, Path}
import scala.util.Try
import java.util.Date
import utils.Iteratees

/**
 * A binary resource does not get direct semantic interpretation.
 * It has a mime type. One can write bytes to it, to replace its content, or one
 * can read its content.
 * @tparam Rdf
 */
trait BinaryResource[Rdf<:RDF] extends NamedResource[Rdf]  {

  def size: Option[Long]

  def mime: MimeType

  // creates a new BinaryResource, with new time stamp, etc...
  def write(implicit ec: ExecutionContext):  Iteratee[Array[Byte], BinaryResource[Rdf]]
  def reader(chunkSize: Int)(implicit ec: ExecutionContext): Enumerator[Array[Byte]]
}




case class LocalBinaryResource[Rdf<:RDF](path: Path, location: Rdf#URI)
                                 (implicit val ops: RDFOps[Rdf])
  extends BinaryResource[Rdf] with LocalNamedResource[Rdf] {
  import ops._

  def meta = PointedGraph(location,Graph.empty)  //todo: need to build it correctly


  // also should be on a metadata trait, since all resources have update times
  def updated = Try { new Date(Files.getLastModifiedTime(path).toMillis) }.toOption

  val size = Try { Files.size(path) }.toOption

  def mime = ???

  // creates a new BinaryResource, with new time stamp, etc...
  //here I can just write to the file, as that should be a very quick operation, which even if it blocks,
  //should be extreemly fast server side.  Iteratee
  def write(implicit ec: ExecutionContext): Iteratee[Array[Byte], LocalBinaryResource[Rdf] ] = {
    val tmpfile = Files.createTempFile(path.getParent,path.getFileName.toString,"tmp")
    val out = Files.newOutputStream(tmpfile, StandardOpenOption.WRITE)
    val iteratee = Iteratees.toOutputStream(out)
    iteratee.map { _ =>
      Files.move(tmpfile,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
      this
    }
  }

  //this will probably require an agent to push things along.
  def reader(chunkSize: Int=1024*8)(implicit ec: ExecutionContext) = Enumerator.fromFile(path.toFile,chunkSize)

}