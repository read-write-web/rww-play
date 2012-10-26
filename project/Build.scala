import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "RWWeb"
    val appVersion      = "0.3-SNAPSHOT"


    val appDependencies = Seq(
          "org.scalatest"                     % "scalatest_2.10.0-M7"         % "2.0.M4-2.10.0-M7-B1",
          "org.w3"                            %% "banana-jena"                % "x07-20121013-SNAPSHOT",
          "org.w3"                            %% "banana-sesame"              % "x07-20121013-SNAPSHOT",
          "org.w3"                            %% "banana-rdf"                 % "x07-20121013-SNAPSHOT",
          "net.rootdev"                       %  "java-rdfa"                  % "0.4.2-RC2",
          "nu.validator.htmlparser"           %  "htmlparser"                 % "1.2.1",
          "org.scalaz"                        %  "scalaz-core_2.10.0-M7"      % "7.0.0-M3",
          "org.bouncycastle"                  %  "bcprov-jdk15on"             % "1.47",
          "org.scala-lang"                    % "scala-actors"                % "2.10.0-M7" //for tests because of sbt for some reason
//        "com.typesafe"                      %% "play-mini"                  % "2.0.1",
      )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots", //for latest scalaz
      resolvers += "sesame-repo-releases" at "http://repo.aduna-software.org/maven2/releases/",
      resolvers += "bblfish-snapshots" at "http://bblfish.net/work/repo/snapshots/"
    )

}
