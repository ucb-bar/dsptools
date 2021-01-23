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
  "chisel-iotesters" -> "1.6.+",
  "rocketchip" -> "1.2.+"
)

name := "dsptools"

val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.5-SNAPSHOT",
  git.remoteRepo := "git@github.com:ucb-bar/dsptools.git",
  autoAPIMappings := true,
  scalaVersion := "2.12.10",
  crossScalaVersions := Seq("2.12.10", "2.11.12"),
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

val dsptoolsSettings = Seq(
  name := "dsptools",
// sbt 1.2.6 fails with `Symbol 'term org.junit' is missing from the classpath`
// when compiling tests under 2.11.12
// An explicit dependency on junit seems to alleviate this.
  libraryDependencies ++= Seq(
    "org.typelevel" %% "spire" % "0.16.2",
    "org.scalanlp" %% "breeze" % "1.1",
    "junit" % "junit" % "4.13" % "test",
    "org.scalatest" %% "scalatest" % "3.2.+" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.3" % "test"
  ),
)

val rocketSettings = Seq(
    name := "rocket-dsptools",
    libraryDependencies ++= defaultVersions.map { case (dep, version) =>
      "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", version)
    }.toSeq,
    Test / parallelExecution := false,
    // rocket-chip currently (3/7/19) doesn't build under 2.11
    crossScalaVersions := Seq("2.12.10"),
)

publishMavenStyle := true

publishArtifact in Test := false
pomIncludeRepository := { x => false }

// Don't add 'scm' elements if we have a git.remoteRepo definition.
// TODO: FIXME: remove once `chisel-testers` 1.6.X is released with bumped `scalatest` deps
// to enable add -Dsbt.sourcemode=true to SBT cmdline
lazy val chiselIotestersRef = ProjectRef(uri("git://github.com/abejgonzalez/chisel-testers.git#5b9cc56"), "chisel-testers")
lazy val chiselIotestersLib = Seq("chisel-iotesters").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}.head

val dsptools = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  enablePlugins(ScalaUnidocPlugin).
  settings(commonSettings: _*).
  settings(dsptoolsSettings: _*).
  sourceDependency(chiselIotestersRef, chiselIotestersLib)


val `rocket-dsptools` = (project in file("rocket")).
  settings(commonSettings: _*).
  settings(rocketSettings: _*).
  dependsOn(dsptools)
