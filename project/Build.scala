import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "test2"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "play" %% "webid" % "2.1-SNAPSHOT"
      // Add your project dependencies here,
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}
