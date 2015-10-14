import com.typesafe.sbt.web.SbtWeb
import org.sbtidea.SbtIdeaPlugin._
import play.Play.autoImport._
import play.twirl.sbt.Import.TwirlKeys
import play.twirl.sbt.SbtTwirl
import sbt.Keys._
import sbt.{ExclusionRule, _}

object ApplicationBuild extends Build {

  val buildSettings = Seq(
    description := "LDP implementation in Play",
    organization := "bblfish.net",
    version := "0.7.3-SNAPSHOT",
    scalaVersion := "2.10.4",
//    crossScalaVersions := Seq("2.11.2", "2.10.4"),
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
    fork := false,
    parallelExecution in Test := false,
    offline := true,
    // TODO
    testOptions in Test += Tests.Argument("-oDS"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-optimize", "-feature", "-language:implicitConversions,higherKinds", "-Xmax-classfile-name", "140", "-Yinline-warnings"),
    scalacOptions in(Compile, doc) := Seq("-groups", "-implicits"),

    startYear := Some(2012),
    //    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    //    resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    //    resolvers += "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
    //    resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    //    resolvers += "Apache snapshots" at "https://repository.apache.org/content/repositories/snapshots",
    resolvers += "bblfish-snapshots" at "http://bblfish.net/work/repo/snapshots/",
    resolvers += "bblfish.net repository" at "http://bblfish.net/work/repo/releases/",
    resolvers += "sesame-repo-releases" at "http://maven.ontotext.com/content/repositories/aduna/",
    libraryDependencies ++= appDependencies,
    ideaExcludeFolders := Seq(".idea", ".idea_modules"),
    //    excludeFilter in (Compile, unmanagedSources) ~= { _ || new FileFilter {
    //      def accept(f: File) = f.getPath.containsSlice("rww/rdf/jena/")
    //      }
    //    },
    //  unmanagedSources in Compile <<= unmanagedSources in Compile map {files => files.foreach(f=>print("~~"+f));files},
    sourceDirectories in(Compile, TwirlKeys.compileTemplates) := (unmanagedSourceDirectories in Compile).value,
    initialize := {
      //thanks to http://stackoverflow.com/questions/19208942/enforcing-java-version-for-scala-project-in-sbt/19271814?noredirect=1#19271814
      val _ = initialize.value // run the previous initialization
      val specVersion = sys.props("java.specification.version")
      assert(java.lang.Float.parseFloat(specVersion) >= 1.8f, "Java 1.8 or above required. Your version is " + specVersion)
    }
  )



  val banana = (name: String) => "org.w3" %% name % "0.7.2-SNAPSHOT" excludeAll (ExclusionRule(organization = "org.scala-stm"))
  val semargl = (name: String) => "org.semarglproject" % {"semargl-"+name} % "0.6.1"

  val iterateeDeps = "com.typesafe.play" %% "play-iteratees" % "2.3.6-TLS"
  val scalatest = "org.scalatest" %% "scalatest" % "2.0.RC1-SNAP4"
//  val scalaActors = "org.scala-lang" % "scala-actors" % "2.10.2"


  /**
   * @see http://repo1.maven.org/maven2/com/typesafe/akka/akka-http-core-experimental_2.10/
   */
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core-experimental" % "1.0"

  val testsuiteDeps =
    Seq(
      //        scalaActors,
      scalatest
    )

  val appDependencies = Seq("sesame", "jena", "plantain_jvm", "ntriples_jvm").map(banana) ++
//  Seq("core",)
  Seq(
    akkaHttpCore,
    ws,
    "net.rootdev" % "java-rdfa" % "0.4.2-RC2",
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1",
//    "io.spray" % "spray-http" % "1.2.0",
    "org.scalaz" %% "scalaz-core" % "7.0.1", // from "http://repo.typesafe.com/typesafe/releases/org/scalaz/scalaz-core_2.10.0-M6/7.0.0-M2/scalaz-core_2.10.0-M6-7.0.0-M2.jar"
    "org.bouncycastle" % "bcprov-jdk15on" % "1.51",
    "org.bouncycastle" % "bcpkix-jdk15on" % "1.51",
//    "org.scala-lang" % "scala-actors" % "2.10.0", //for tests because of sbt for some reason
    "net.sf.uadetector" % "uadetector-resources" % "2014.01",
    "com.typesafe.akka" %% "akka-actor" % "2.3.4",
    iterateeDeps,
    "org.scalatest" %% "scalatest" % "2.0.RC1-SNAP4",
//    "org.scala-lang" % "scala-actors" % "2.10.2",
    // https://code.google.com/p/guava-libraries/
    "com.google.guava" % "guava" % "16.0.1",
    "com.google.code.findbugs" % "jsr305" % "2.0.2",
    "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
    "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"
    //        "com.typesafe"                      %% "play-mini"                  % "2.0.1",
  )


  val main = Project(
    id = "RWWeb",
    base = file("."),
    settings =  buildSettings
  ).enablePlugins(play.PlayScala).enablePlugins(SbtWeb).enablePlugins(SbtTwirl)


}
