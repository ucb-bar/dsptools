// SPDX-License-Identifier: Apache-2.0

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

git.remoteRepo := "git@github.com:ucb-bar/dsptools.git"

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

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel-iotesters" -> "1.5.3",
  "dsptools" -> "1.4.3",
  "rocketchip" -> "1.2-SNAPSHOT"
)

name := "rocket-dsp-utils"

val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.5-SNAPSHOT",
  autoAPIMappings := true,
  scalaVersion := "2.12.13",
  crossScalaVersions := Seq("2.12.13"),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls") ++ scalacOptionsVersion(scalaVersion.value),
  javacOptions ++= javacOptionsVersion(scalaVersion.value),
  pomExtra := (<url>http://chisel.eecs.berkeley.edu/</url>
  <licenses>
    <license>
      <name>apache_v2</name>
      <url>https://opensource.org/licenses/Apache-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
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
  )
)

val rocketSettings = Seq(
    name := "rocket-dsp-utils",
    libraryDependencies ++= defaultVersions.map { case (dep, version) =>
      "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", version)
    }.toSeq,
    Test / parallelExecution := false,
    crossScalaVersions := Seq("2.12.13"),
)

publishMavenStyle := true

publishArtifact in Test := false
pomIncludeRepository := { x => false }

val `rocket-dsp-utils` = (project in file(".")).
  settings(commonSettings: _*).
  settings(rocketSettings: _*)
