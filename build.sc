import ammonite.ops._
import mill._
import mill.scalalib._
import mill.scalalib.publish._

val defaultVersions = Map(
  "chisel3" -> "3.1.0", //-SNAPSHOT",
  "chisel-iotesters" -> "1.2.0" //-SNAPSHOT"
)

def getVersion(dep: String, org: String = "edu.berkeley.cs") = {
  val version = sys.props.getOrElse(dep + "Version", defaultVersions(dep))
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
    "-Xlint:missing-interpolator"//,
    //"-Ywarn-unused:imports",
    //"-Ywarn-unused:locals"
  )
}

object dsptools extends Cross[DsptoolsModule]("2.11.12", "2.12.4")

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
