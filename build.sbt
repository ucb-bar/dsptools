// See LICENSE for license details.

enablePlugins(BuildInfoPlugin)

ChiselProjectDependenciesPlugin.chiselBuildInfoSettings

ChiselProjectDependenciesPlugin.chiselProjectSettings

name := "dsptools"

organization := "edu.berkeley.cs"

version := "1.1-SNAPSHOT"

crossScalaVersions := Seq("2.11.11", "2.12.3")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.1-SNAPSHOT",
  "chisel-iotesters" -> "1.2-SNAPSHOT"
)

def chiselVersion(proj: String): String = {
  sys.props.getOrElse(proj + "Version", defaultVersions(proj))
}

// The Chisel projects we're dependendent on.
val chiselDeps = chisel.dependencies(Seq(
    ("edu.berkeley.cs" %% "chisel3" % chiselVersion("chisel3"), "chisel3"),
    ("edu.berkeley.cs" %% "chisel-iotesters" % chiselVersion("chisel-iotesters"), "chisel-testers")
))

val dependentProjects = chiselDeps.projects

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls")

libraryDependencies ++= Seq(
  "org.typelevel" %% "spire" % "0.14.1",
  "org.scalanlp" %% "breeze" % "0.13.2",
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.scalacheck" %% "scalacheck" % "1.13.4",
  "co.theasi" %% "plotly" % "0.1"
) ++ chiselDeps.libraries

lazy val dsptools = (project in file("."))
  .dependsOn(dependentProjects.map(classpathDependency(_)):_*)
