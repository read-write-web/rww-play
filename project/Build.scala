import sbt._
import sbt.Keys._

object ApplicationBuild extends Build {

    val appName         = "RWWeb"
    val appVersion      = "0.6-SNAPSHOT"


    val appDependencies = Seq(
          "org.scalatest"                     %  "scalatest_2.10.0-RC5"       % "2.0.M5-B1",
          "org.w3"                            %% "banana-jena"                % "2013_01_16-SNAPSHOT",
          "org.w3"                            %% "banana-sesame"              % "2013_01_16-SNAPSHOT",
          "org.w3"                            %% "banana-rdf"                 % "2013_01_16-SNAPSHOT",
          "org.w3"                            %% "plantain"                   % "2013_01_16-SNAPSHOT",
          "net.rootdev"                       %  "java-rdfa"                  % "0.4.2-RC2",
          "nu.validator.htmlparser"           %  "htmlparser"                 % "1.2.1",
          "org.scalaz"                        %  "scalaz-core_2.10"           % "7.0-SNAPSHOT", // from "http://repo.typesafe.com/typesafe/releases/org/scalaz/scalaz-core_2.10.0-M6/7.0.0-M2/scalaz-core_2.10.0-M6-7.0.0-M2.jar"
          "org.bouncycastle"                  %  "bcprov-jdk15on"             % "1.47",
          "org.scala-lang"                    %  "scala-actors"               % "2.10.0",        //for tests because of sbt for some reason
          "net.sf.uadetector"                 %  "uadetector-resources"       % "2012.12"
//        "com.typesafe"                      %% "play-mini"                  % "2.0.1",
      )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots", //for latest scalaz
      resolvers += "sesame-repo-releases" at "http://repo.aduna-software.org/maven2/releases",
      resolvers += "bblfish-snapshots" at "http://bblfish.net/work/repo/snapshots",
      scalaVersion := "2.10.0",
      javacOptions ++= Seq("-source","1.7", "-target","1.7")
  )

}
