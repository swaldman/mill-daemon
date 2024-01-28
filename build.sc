import mill._, scalalib._, publish._

val MillVersion = "0.11.5"

object MillDaemon extends RootModule with ScalaModule with PublishModule {
  override def scalaVersion = "2.13.11"
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-main:${MillVersion}",
    ivy"com.lihaoyi::mill-scalalib:${MillVersion}",
  )
  override def scalacOptions = T{ Seq("-deprecation") }

  override def artifactName = "mill-daemon"
  override def publishVersion = T{"0.0.1-SNAPSHOT"}
  override def pomSettings    = T{
    PomSettings(
      description = "A mill module that implements spawning systemd-appropriate forking daemon processes",
      organization = "com.mchange",
      url = "https://github.com/swaldman/mill-daemon",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("swaldman", "mill-daemon"),
      developers = Seq(
	Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
      )
    )
  }
}
