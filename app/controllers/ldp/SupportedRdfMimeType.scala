package controllers.ldp


/**
 * These are the currently supported mime types that can be used to transmit the RDF data to clients
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object SupportedRdfMimeType extends Enumeration {
  val Turtle = Value("text/turtle")
  val RdfXml = Value("application/rdf+xml")

  // yes we consider that html is a valid rendering for a resource
  val Html = Value("text/html")

  val StringSet = SupportedRdfMimeType.values.map(_.toString)
}
