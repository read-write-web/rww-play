package controllers

import play.api.Play
import java.io.File
import java.nio.file.Path


object RwwConfiguration {

  val RdfViewerHtmlTemplatePathKey = "rww.rdf.html.viewer.template.path"
  val RootContainerPathKey = "rww.root.container.path"

  /**
   * we check the existence of the file because Resource.fromFile creates the file if it doesn't exist
   * (the doc says it raises an exception but it's not the case)
   * @param key
   * @return
   */
  def getFileForConfigurationKey(key: String): File = {
    val path = Play.current.configuration.getString(key)
    require(path.isDefined,s"Missing configuration for key $key")
    val file = new File(path.get)
    require(file.exists() && file.canRead,s"Unable to find or read file/directory: $file")
    file
  }


  val rdfViewerHtmlTemplate: String = {
    import scalax.io.{Resource=>xResource}
    val file = getFileForConfigurationKey(RdfViewerHtmlTemplatePathKey)
    require(file.isFile,s"The RDF viewer template file ($file) is not a file")
    xResource.fromFile(file).string
  }


  val rootContainerPath: Path = {
    val file = getFileForConfigurationKey(RootContainerPathKey)
    require(file.isDirectory,s"The root container ($file) is not a directory")
    file.toPath.toAbsolutePath
  }


}
