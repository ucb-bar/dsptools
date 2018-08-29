// See LICENSE for license details.

name := "rocket-dsp-utils"

organization := "edu.berkeley.cs"

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.4"

resolvers ++= Seq (
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Int)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}


scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls") ++ scalacOptionsVersion(scalaVersion.value)

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.2-SNAPSHOT",
  "chisel-iotesters" -> "1.2.2",
  "dsptools" -> "1.1.2",
  "rocketchip" -> "1.2",
  "vegas" -> "0.3.11",
  "handlebars-scala" -> "2.1.1"
)

libraryDependencies ++= Seq("rocketchip", "chisel-iotesters", "dsptools", "chisel3").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}

// libraryDependencies += "org.vegas-viz" %% "vegas" % sys.props.getOrElse("vegasVersion", defaultVersions("vegas"))

// libraryDependencies += ("com.gilt" %% "handlebars-scala" %
//  sys.props.getOrElse("handlebarsVersion", defaultVersions("handlebars-scala"))).exclude("org.slf4j", "slf4j-simple")

parallelExecution in Test := false
