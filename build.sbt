// See LICENSE for license details.

// sbt-site - sbt-ghpages

enablePlugins(SiteScaladocPlugin)

// Firrtl code

organization := "edu.berkeley.cs"

name := "firrtl"

version := "1.2-SNAPSHOT"

scalaVersion := "2.12.10"

crossScalaVersions := Seq("2.12.10", "2.11.12")

def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

addCompilerPlugin(scalafixSemanticdb) // enable SemanticDB

scalacOptions := scalacOptionsVersion(scalaVersion.value) ++ Seq(
  "-deprecation",
  "-unchecked",
  "-Yrangepos",          // required by SemanticDB compiler plugin
  "-Ywarn-unused-import" // required by `RemoveUnused` rule
)

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8, but we continue to generate
    //  Java 7 compatible code until we need Java 8 features
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

javacOptions ++= javacOptionsVersion(scalaVersion.value)

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// sbt 1.2.6 fails with `Symbol 'term org.junit' is missing from the classpath`
// when compiling tests under 2.11.12
// An explicit dependency on junit seems to alleviate this.
libraryDependencies += "junit" % "junit" % "4.12" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.1"

libraryDependencies += "net.jcazevedo" %% "moultingyaml" % "0.4.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.6.7"

libraryDependencies += "org.apache.commons" % "commons-text" % "1.7"

// Java PB

enablePlugins(ProtobufPlugin)

sourceDirectory in ProtobufConfig := baseDirectory.value / "src" / "main" / "proto"

protobufRunProtoc in ProtobufConfig := (args =>
  com.github.os72.protocjar.Protoc.runProtoc("-v351" +: args.toArray))

// Assembly

assemblyJarName in assembly := "firrtl.jar"

test in assembly := {} // Should there be tests?

assemblyOutputPath in assembly := file("./utils/bin/firrtl.jar")

// ANTLRv4

enablePlugins(Antlr4Plugin)

antlr4GenVisitor in Antlr4 := true // default = false

antlr4GenListener in Antlr4 := false // default = true

antlr4PackageName in Antlr4 := Option("firrtl.antlr")

antlr4Version in Antlr4 := "4.7.1"

javaSource in Antlr4 := (sourceManaged in Compile).value

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { x => false }
// scm is set by sbt-ci-release
pomExtra := <url>http://chisel.eecs.berkeley.edu/</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>jackbackrack</id>
      <name>Jonathan Bachrach</name>
      <url>http://www.eecs.berkeley.edu/~jrb/</url>
    </developer>
  </developers>

publishTo := {
  val v = version.value
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

// MiMa
// All changes must be binary compatible with prior 1.2 minor releases
import com.typesafe.tools.mima.core._
mimaPreviousArtifacts := Set("edu.berkeley.cs" %% "firrtl" % "1.2.7")
mimaBinaryIssueFilters ++= Seq(
  // Removed private inner classes, see https://github.com/lightbend/mima/issues/54
  ProblemFilters.exclude[MissingClassProblem]("firrtl.transforms.CheckCombLoops$LogicNode$"),
  ProblemFilters.exclude[MissingClassProblem]("firrtl.transforms.CheckCombLoops$LogicNode")
)


// ScalaDoc

enablePlugins(ScalaUnidocPlugin)

doc in Compile := (doc in ScalaUnidoc).value

//target in unidoc in ScalaUnidoc := crossTarget.value / "api"

autoAPIMappings := true

apiMappings ++= {
    Option(System.getProperty("sun.boot.class.path")).flatMap { classPath =>
      classPath.split(java.io.File.pathSeparator).find(_.endsWith(java.io.File.separator + "rt.jar"))
    }.map { jarPath =>
      Map(
        file(jarPath) -> url("https://docs.oracle.com/javase/8/docs/api")
      )
    }.getOrElse {
      streams.value.log.warn("Failed to add bootstrap class path of Java to apiMappings")
      Map.empty[File,URL]
    }
  }
<<<<<<< HEAD
=======
}
lazy val docSettings = Seq(
  doc in Compile := (doc in ScalaUnidoc).value,
  autoAPIMappings := true,
  scalacOptions in Compile in doc ++= Seq(
    "-feature",
    "-diagrams",
    "-diagrams-max-classes", "25",
    "-doc-version", version.value,
    "-doc-title", name.value,
    "-doc-root-content", baseDirectory.value+"/root-doc.txt",
    "-sourcepath", (baseDirectory in ThisBuild).value.toString,
    "-doc-source-url",
    {
      val branch =
        if (version.value.endsWith("-SNAPSHOT")) {
          "master"
        } else {
          s"v${version.value}"
        }
      s"https://github.com/chipsalliance/firrtl/tree/$branch€{FILE_PATH_EXT}#L€{FILE_LINE}"
    }
  ) ++ scalacDocOptionsVersion(scalaVersion.value)
)
>>>>>>> 6e0e7605... Add file line to source link from scaladoc (#2072)

scalacOptions in Compile in doc ++= Seq(
  "-diagrams",
  "-diagrams-max-classes", "25",
  "-doc-version", version.value,
  "-doc-title", name.value,
  "-doc-root-content", baseDirectory.value+"/root-doc.txt",
  "-sourcepath", (baseDirectory in ThisBuild).value.toString,
  "-doc-source-url",
  {
    val branch =
      if (version.value.endsWith("-SNAPSHOT")) {
        "master"
      } else {
        s"v${version.value}"
      }
    s"https://github.com/freechipsproject/firrtl/tree/$branch€{FILE_PATH}.scala"
  }
) ++ scalacOptionsVersion(scalaVersion.value)

fork := true
Test / testForkedParallel := true
