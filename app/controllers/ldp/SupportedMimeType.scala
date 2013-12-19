package controllers.ldp


/**
 * These are the currently supported mime types that can be used to transmit the RDF data to clients
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object SupportedMimeType extends Enumeration {
  val Turtle = Value("text/turtle")
  val RdfXml = Value("application/rdf+xml")
  val Html = Value("text/html")

  val StringSet = SupportedMimeType.values.map(_.toString)
}
