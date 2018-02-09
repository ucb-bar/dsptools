// See LICENSE for license details.

enablePlugins(BuildInfoPlugin)

ChiselProjectDependenciesPlugin.chiselBuildInfoSettings

ChiselProjectDependenciesPlugin.chiselProjectSettings

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

git.remoteRepo := "git@github.com:ucb-bar/dsptools.git"

name := "dsptools"

organization := "edu.berkeley.cs"

version := "1.1-SNAPSHOT"

scalaVersion := "2.11.12"

crossScalaVersions := Seq("2.11.12", "2.12.4")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// NOTE: These need to be "published" versions so Travis can find them.
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
  "org.scalacheck" %% "scalacheck" % "1.13.4"
) ++ chiselDeps.libraries

lazy val dsptools = (project in file("."))
  .dependsOn(dependentProjects.map(classpathDependency(_)):_*)

// Don't add 'scm' elements if we have a git.remoteRepo definition.
pomExtra := pomExtra.value ++
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
