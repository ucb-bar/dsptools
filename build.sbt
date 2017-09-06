// See LICENSE for license details.

name := "dsptools"

organization := "edu.berkeley.cs"

version := "1.1-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers ++= Seq (
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq("chisel3","chisel-iotesters").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.0-SNAPSHOT_2017-08-16",
  "chisel-iotesters" -> "1.1-SNAPSHOT_2017-08-16"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "spire" % "0.14.1",
  "org.scalanlp" %% "breeze" % "0.13.2",
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.scalacheck" %% "scalacheck" % "1.13.4",
  "co.theasi" %% "plotly" % "0.1"
)
