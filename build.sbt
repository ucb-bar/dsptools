// SPDX-License-Identifier: Apache-2.0

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

val defaultVersions = Map(
  "chisel-iotesters" -> "2.5-SNAPSHOT",
  "chisel3" -> "3.5-SNAPSHOT",
)

name := "dsptools"

val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.5-SNAPSHOT",
  git.remoteRepo := "git@github.com:ucb-bar/dsptools.git",
  autoAPIMappings := true,
  scalaVersion := "2.12.14",
  crossScalaVersions := Seq("2.13.6", "2.12.14"),
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  pomExtra := (<url>http://chisel.eecs.berkeley.edu/</url>
  <licenses>
    <license>
      <name>apache_v2</name>
      <url>https://opensource.org/licenses/Apache-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>grebe</id>
      <name>Paul Rigge</name>
      <url>http://www.eecs.berkeley.edu/~rigge/</url>
    </developer>
    <developer>
      <id>shunshou</id>
      <name>Angie Wang</name>
      <url>https://www.linkedin.com/in/angie-wang-ee/</url>
    </developer>
    <developer>
      <id>chick</id>
      <name>Charles Markley</name>
      <url>https://aspire.eecs.berkeley.edu/author/chick/</url>
    </developer>
   </developers>),
  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) {
      Some("snapshots" at nexus + "content/repositories/snapshots")
    }
    else {
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  },
  resolvers ++= Seq (
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  ),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, major)) if major <= 12 => Seq()
      case _ => Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.3")
    }
  },
  libraryDependencies ++= Seq("chisel-iotesters").map { dep: String =>
    "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  },
  addCompilerPlugin("edu.berkeley.cs" %% "chisel3-plugin" % defaultVersions("chisel3") cross CrossVersion.full),
)

val dsptoolsSettings = Seq(
  name := "dsptools",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "spire" % "0.17.0",
    "org.scalanlp" %% "breeze" % "1.1",
    "org.scalatest" %% "scalatest" % "3.2.+" % "test"
  ),
)

publishMavenStyle := true

publishArtifact in Test := false
pomIncludeRepository := { x => false }

val dsptools = (project in file("."))
  //.enablePlugins(BuildInfoPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .settings(commonSettings: _*)
  .settings(dsptoolsSettings: _*)
