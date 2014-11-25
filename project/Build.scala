import org.sbtidea.SbtIdeaPlugin._
import sbt.Keys._
import sbt._
import play.Play.autoImport._
import PlayKeys._

object ApplicationBuild extends Build {

  val appName = "RWWeb"
  val appVersion = "0.7.2-SNAPSHOT"

  val banana = (name: String) => "org.w3" %% name % "0.7.1-SNAPSHOT" excludeAll (ExclusionRule(organization = "org.scala-stm"))
  val semargl = (name: String) => "org.semarglproject" % {"semargl-"+name} % "0.6.1"

  val iterateeDeps = "com.typesafe.play" %% "play-iteratees" % "2.3-SNAPSHOT"
  val scalatest = "org.scalatest" %% "scalatest" % "2.0.RC1-SNAP4"
//  val scalaActors = "org.scala-lang" % "scala-actors" % "2.10.2"


  /**
   * @see http://repo1.maven.org/maven2/com/typesafe/akka/akka-http-core-experimental_2.10/
   */
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core-experimental" % "0.9"

  val testsuiteDeps =
    Seq(
      //        scalaActors,
      scalatest
    )

  val appDependencies = Seq("sesame", "jena", "plantain_jvm", "ntriples_jvm").map(banana) ++
//  Seq("core",)
  Seq(
    akkaHttpCore,
    "net.rootdev" % "java-rdfa" % "0.4.2-RC2",
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1",
    "io.spray" % "spray-http" % "1.2.0",
    "org.scalaz" % "scalaz-core_2.10" % "7.0.0-RC1", // from "http://repo.typesafe.com/typesafe/releases/org/scalaz/scalaz-core_2.10.0-M6/7.0.0-M2/scalaz-core_2.10.0-M6-7.0.0-M2.jar"
    "org.bouncycastle" % "bcprov-jdk15on" % "1.47",
    "org.scala-lang" % "scala-actors" % "2.10.0", //for tests because of sbt for some reason
    "net.sf.uadetector" % "uadetector-resources" % "2014.01",
    iterateeDeps,
    "org.scalatest" %% "scalatest" % "2.0.RC1-SNAP4",
    "org.scala-lang" % "scala-actors" % "2.10.2",
    "com.google.guava" % "guava" % "15.0",
    "com.google.code.findbugs" % "jsr305" % "2.0.2",
    "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
    "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"
    //        "com.typesafe"                      %% "play-mini"                  % "2.0.1",
  )


  val main = Project(appName, file(".")).enablePlugins(play.PlayScala).settings(
    resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots", //for latest scalaz
    resolvers += "Typesafe snapshots" at "http://repo.typesafe.com/typesafe/snapshots",
    resolvers += "sesame-repo-releases" at "http://maven.ontotext.com/content/repositories/aduna/",
    resolvers += "spray repo" at "http://repo.spray.io",

    ideaExcludeFolders := Seq(".idea",".idea_modules" ),
//    excludeFilter in (Compile, unmanagedSources) ~= { _ || new FileFilter {
//      def accept(f: File) = f.getPath.containsSlice("rww/rdf/jena/")
//      }
//    },
//  unmanagedSources in Compile <<= unmanagedSources in Compile map {files => files.foreach(f=>print("~~"+f));files},
//    resolvers += "bblfish-snapshots" at "http://bblfish.net/work/repo/snapshots",
    scalaVersion := "2.10.4",
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
    initialize := {
      //thanks to http://stackoverflow.com/questions/19208942/enforcing-java-version-for-scala-project-in-sbt/19271814?noredirect=1#19271814
      val _ = initialize.value // run the previous initialization
      val specVersion = sys.props("java.specification.version")
      assert(java.lang.Float.parseFloat(specVersion) >= 1.7, "Java 1.7 or above required. Your version is " + specVersion)
    }


  )

}
