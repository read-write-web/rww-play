import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "test2"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
          "org.w3"                            %% "banana-jena"                % "0.3hjs-SNAPSHOT",
          "org.w3"                            %% "banana-sesame"              % "0.3hjs-SNAPSHOT",
          "org.w3"                            %% "banana-n3"                  % "0.3hjs-SNAPSHOT",
          "net.rootdev"                       %  "java-rdfa"                  % "0.4.2-RC2",
          "nu.validator.htmlparser"           %  "htmlparser"                 % "1.2.1",
          "com.typesafe"                      %% "play-mini"                  % "2.0.1",
          "org.scalaz"                        %% "scalaz-core"                % "7.0-SNAPSHOT"
      )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}
