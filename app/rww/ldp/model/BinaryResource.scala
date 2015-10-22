package rww.ldp.model

import java.io.File
import java.nio.file._

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.w3.banana.io.MimeType
import org.w3.banana.{RDF, RDFOps}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import rww.ldp.SupportedBinaryMimeExtensions
import utils.{FileUtils, Iteratees}

import scala.concurrent.ExecutionContext

/**
 * A binary resource does not get direct semantic interpretation.
 * It has a mime type. One can write bytes to it, to replace its content, or one
 * can read its content.
 * @tparam Rdf subclass of RDF
 */
trait BinaryResource[Rdf<:RDF] extends NamedResource[Rdf]  {

  def mime: MimeType

  // creates a new BinaryResource, with new time stamp, etc...
  def writeIteratee(implicit ec: ExecutionContext):  Iteratee[Array[Byte], BinaryResource[Rdf]]
  def setContentTo(temp: File): Unit
  def readerEnumerator(chunkSize: Int)(implicit ec: ExecutionContext): Enumerator[Array[Byte]]
}




case class LocalBinaryResource[Rdf<:RDF](path: Path, location: Rdf#URI,metaData: Option[Rdf#Graph] = None)
                                 (implicit val ops: RDFOps[Rdf])
  extends BinaryResource[Rdf] with LocalNamedResource[Rdf] with LazyLogging {
  import ops._
  import org.w3.banana.diesel._


  /** type specific metadata */
  override def typeMetadata = (location -- rdf.typ ->- ldp.Resource).graph

  /** context specific metadata */
  override def contextualMetadata = None


  def mime = {
    (for {
      extension <- FileUtils.getFileExtension(path)
      mimeType <- SupportedBinaryMimeExtensions.mime("."+extension)
    } yield mimeType) getOrElse {
      throw new IllegalStateException(s"Unexpected: can't get binary file extension for $path")
    }
  }

  def setContentTo(temp: File): Unit = {
    import java.nio.file._
    Files.move(temp.toPath,path,StandardCopyOption.ATOMIC_MOVE)
  }

  // creates a new BinaryResource, with new time stamp, etc...
  //here I can just write to the file, as that should be a very quick operation, which even if it blocks,
  //should be extremely fast server side.  Iteratee
  def writeIteratee(implicit ec: ExecutionContext): Iteratee[Array[Byte], LocalBinaryResource[Rdf] ] = {
    val tmpfile = Files.createTempFile(path.getFileName.toString+"_",".tmp")
    val out = Files.newOutputStream(tmpfile, StandardOpenOption.WRITE)
    logger.info(s"Will prepare Iteratee to write to temporary file $tmpfile")
    Iteratees.toOutputStream(out) map { _ =>
      logger.info(s"Iteratee is done, will move temporary written file $tmpfile to real path $path")
      Files.move(tmpfile,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
      this
    }
  }

  //this will probably require an agent to push things along.
  def readerEnumerator(chunkSize: Int=1024*8)(implicit ec: ExecutionContext) = Enumerator.fromFile(path.toFile,chunkSize)

}