// See LICENSE for license details.

enablePlugins(BuildInfoPlugin)

ChiselProjectDependenciesPlugin.chiselBuildInfoSettings

ChiselProjectDependenciesPlugin.chiselProjectSettings

name := "dsptools"

organization := "edu.berkeley.cs"

version := "1.1-SNAPSHOT"

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// NOTE: These need to be "published" versions so Travis can find them.
val defaultVersions = Map(
  "chisel3" -> "3.0-SNAPSHOT",
  "chisel-iotesters" -> "1.1-SNAPSHOT"
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
  "co.theasi" %% "plotly" % "0.2.0"
) ++ chiselDeps.libraries

lazy val dsptools = (project in file("."))
  .dependsOn(dependentProjects.map(classpathDependency(_)):_*)

pomExtra := pomExtra.value ++ (
<scm>
  <url>https://github.com/ucb-bar/dsptools.git</url>
  <connection>scm:git:github.com/ucb-bar/dsptools.git</connection>
</scm>
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
 </developers>
)
