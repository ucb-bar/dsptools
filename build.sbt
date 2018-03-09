// See LICENSE for license details.

name := "rocket-dsp-utils"

organization := "edu.berkeley.cs"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers ++= Seq (
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.0.2",
  "chisel-iotesters" -> "1.1.3-SNAPSHOT",
  "dsptools" -> "1.0.3-SNAPSHOT",
  "rocketchip" -> "1.2-SNAPSHOT",
  "vegas" -> "0.3.11",
  "handlebars-scala" -> "2.1.1"
)

libraryDependencies ++= Seq("rocketchip", "chisel-iotesters", "dsptools", "chisel3").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}

libraryDependencies += "org.vegas-viz" %% "vegas" % sys.props.getOrElse("vegasVersion", defaultVersions("vegas"))

libraryDependencies += ("com.gilt" %% "handlebars-scala" %
  sys.props.getOrElse("handlebarsVersion", defaultVersions("handlebars-scala"))).exclude("org.slf4j", "slf4j-simple")

parallelExecution in Test := false
