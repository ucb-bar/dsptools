// See LICENSE for license details.

lazy val commonSettings = Seq (
  organization := "edu.berkeley.cs",
  version := "1.0",
  scalaVersion := "2.11.7",
  resolvers ++= Seq (
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  ),
  libraryDependencies ++= Seq("chisel3","chisel-iotesters").map {
    dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep)) },
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls")
)

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.1-SNAPSHOT",
  "chisel-iotesters" -> "1.2-SNAPSHOT",
  "rocketchip" -> "1.2",
  "testchipip" -> "1.0"
)

lazy val dsptools = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "org.spire-math" %% "spire" % "0.11.0",
      "org.scalanlp" %% "breeze" % "0.12",
      "org.scalatest" %% "scalatest" % "2.2.5",
      "org.scalacheck" %% "scalacheck" % "1.12.4",
      "co.theasi" %% "plotly" % "0.1"
    )
  )

lazy val dspblocks = (project in file("dspblocks")).
  settings(commonSettings: _*).
  dependsOn(dsptools).
  settings(
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "testchipip" % sys.props.getOrElse("testchipipVersion", defaultVersions("testchipip"))
    )
  )

(publishLocal in dsptools) <<= (publishLocal in dspblocks).dependsOn(publishLocal in dsptools)
