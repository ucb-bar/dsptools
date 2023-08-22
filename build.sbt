// SPDX-License-Identifier: Apache-2.0

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

val defaultVersions = Map(
  "chisel3" -> "3.6.0",
  "chiseltest" -> "0.6.2"
)

name := "dsptools"

val commonSettings = Seq(
  scalaVersion := "2.13.10",
  scalacOptions ++= Seq("-encoding",
                        "UTF-8",
                        "-unchecked",
                        "-deprecation",
                        "-feature",
                        "-language:reflectiveCalls",
                        "-Xfatal-warnings",
                        "-Ymacro-annotations"),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, major)) if major <= 12 => Seq()
      case _                               => Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4")
    }
  },
  libraryDependencies ++= Seq("chisel3").map { dep: String =>
    "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  },
  addCompilerPlugin(("edu.berkeley.cs" %% "chisel3-plugin" % defaultVersions("chisel3")).cross(CrossVersion.full)),
)

val dsptoolsSettings = Seq(
  name := "dsptools",
  organization := "edu.berkeley.cs",
  version := "1.6-SNAPSHOT",
  git.remoteRepo := "git@github.com:ucb-bar/dsptools.git",
  autoAPIMappings := true,
  libraryDependencies ++= Seq(
    "org.typelevel" %% "spire" % "0.18.0",
    "org.scalanlp" %% "breeze" % "2.1.0",
    "org.scalatest" %% "scalatest" % "3.2.15" % "test"
  ),
  libraryDependencies ++= Seq("chiseltest").map { dep: String =>
    "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  },
  pomExtra := <url>http://chisel.eecs.berkeley.edu/</url>
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
    </developers>,
  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) {
      Some("snapshots".at(nexus + "content/repositories/snapshots"))
    } else {
      Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
    }
  },
)

val fixedpointSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.15" % "test",
    "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
  )
)

publishMavenStyle := true

Test / publishArtifact := false
pomIncludeRepository := { x =>
  false
}

def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      Compile / scalaSource := baseDirectory.value / "main" / "scala",
      Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
    )
}

lazy val fixedpoint = freshProject("fixedpoint", file("fixedpoint"))
  .settings(
    commonSettings,
    fixedpointSettings
  )

val dsptools = (project in file("."))
  .dependsOn(fixedpoint)
  //.enablePlugins(BuildInfoPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .settings(commonSettings)
  .settings(dsptoolsSettings)
