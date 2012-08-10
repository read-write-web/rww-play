import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "RW Web"
    val appVersion      = "0.3-SNAPSHOT"


    val appDependencies = Seq(
          "org.w3"                            %% "banana-jena"                % "0.3-20120810-SNAPSHOT",
          "org.w3"                            %% "banana-sesame"              % "0.3-20120810-SNAPSHOT",
          "net.rootdev"                       %  "java-rdfa"                  % "0.4.2-RC2",
          "nu.validator.htmlparser"           %  "htmlparser"                 % "1.2.1",
          "org.scalaz"                        %% "scalaz-core"                % "7.0-SNAPSHOT"
//        "com.typesafe"                      %% "play-mini"                  % "2.0.1",
      )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += "sesame-repo-releases" at "http://repo.aduna-software.org/maven2/releases/",
      resolvers += "bblfish-snapshots" at "http://bblfish.net/work/repo/snapshots/"
    )

}
