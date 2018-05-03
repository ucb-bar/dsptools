import ammonite.ops._
import mill._
import mill.scalalib._
import mill.scalalib.publish._

import $file.CommonBuild

val defaultVersions = Map(
  "chisel3" -> "3.1.0",
  "chisel-iotesters" -> "1.2.0",
  "rocketchip" -> "1.2-SNAPSHOT"
)

def getVersion(dep: String, org: String = "edu.berkeley.cs") = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  ivy"$org::$dep:$version"
}

trait CommonModule extends CrossSbtModule with PublishModule {
  def publishVersion = "1.2-SNAPSHOT"

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "edu.berkeley.cs",
    url = "https://github.com/ucb-bar/dsptools.git",
    licenses = Seq(License.`BSD-3-Clause`),
    versionControl = VersionControl.github("ucb-bar", "dsptools"),
    developers = Seq(
      Developer("grebe",    "Paul Rigge",      "https://eecs.berkeley.edu/~rigge/"),
      Developer("shunshou", "Angie Wang",      "https://www.linkedin.com/in/angie-wang-ee/"),
      Developer("chick",    "Charles Markley", "https://aspire.eecs.berkeley.edu/author/chick/")
    )
  )

  def scalacOptions = Seq(
    "-deprecation",
    "-explaintypes",
    "-feature", "-language:reflectiveCalls",
    "-unchecked",
    "-Xcheckinit",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator"
  ) ++ CommonBuild.scalacOptionsVersion(crossScalaVersion)

  def javacOptions = CommonBuild.javacOptionsVersion(crossScalaVersion)
}

val crossVersions = Seq("2.11.12", "2.12.4")

object dsptools extends Cross[DsptoolsModule](crossVersions: _*)

class DsptoolsModule(val crossScalaVersion: String) extends CommonModule {
  def artifactName = "dsptools"

  def cliDeps = Agg("chisel3", "chisel-iotesters").map { d => getVersion(d) }

  def ivyDeps = Agg(
    ivy"org.typelevel::spire:0.14.1",
    ivy"org.scalanlp::breeze:0.13.2"
  ) ++ cliDeps

  object test extends Tests {
    def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.0.5")
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}

object rocket extends Cross[RocketDsptoolsModule](crossVersions: _*)

class RocketDsptoolsModule(val crossScalaVersion: String) extends CommonModule {
  def artifactName = "rocket-dsptools"

  def moduleDeps = Seq(dsptools())

  def cliDeps = Agg("rocketchip").map { d => getVersion(d) }

  def ivyDeps = Agg(
    ivy"org.vegas-viz::vegas:0.3.11",
    ivy"com.gilt::handlebars-scala:2.1.1"
  ) ++ cliDeps

  def unmanagedClasspath = Agg(
    mill.modules.Util.download(
      "https://github.com/ucb-art/craft2-chip/raw/master/lib/ipxact_2.11-1.0.jar",
      "ipxact_2.11-1.0.jar"
    )
  )

  object test extends Tests {
    def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.0.5")
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
