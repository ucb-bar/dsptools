// See LICENSE for license details.

name := "rocket-dsp-utils"

organization := "edu.berkeley.cs"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq (
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq("chisel3", "chisel-iotesters", "dsptools", "testchipip", "builtin-debugger", "tapeout").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.1-SNAPSHOT",
  "chisel-iotesters" -> "1.2-SNAPSHOT",
  "dsptools" -> "1.0",
  "rocketchip" -> "1.2",
  "builtin-debugger" -> "0",
  "testchipip" -> "1.0",
  "tapeout" -> "0.1-SNAPSHOT"
)

libraryDependencies += "com.gilt" %% "handlebars-scala" % "2.1.1"
