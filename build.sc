import mill._, scalalib._, publish._

object `package` extends RootModule with ScalaModule with PublishModule {

  val MillVersion = "0.12.1"

  override def scalaVersion = "2.13.15"
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-main:${MillVersion}",
    ivy"com.lihaoyi::mill-scalalib:${MillVersion}",
  )
  override def scalacOptions = T{ Seq("-deprecation") }

  override def artifactName = "mill-daemon"
  override def publishVersion = T{"0.1.2-SNAPSHOT"}
  override def pomSettings    = T{
    PomSettings(
      description = "A mill module that implements spawning of systemd-appropriate forking daemon processes",
      organization = "com.mchange",
      url = "https://github.com/swaldman/mill-daemon",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("swaldman", "mill-daemon"),
      developers = Seq(
	Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
      )
    )
  }

  /**
   * Update the millw script.
   * modified from https://github.com/lefou/millw
   */
  def overwriteLatestMillw() = T.command {
    import java.nio.file.attribute.PosixFilePermission._
    val target = mill.util.Util.download("https://raw.githubusercontent.com/lefou/millw/main/millw")
    val millw = Task.workspace / "millw"
    os.copy.over(target.path, millw)
    os.perms.set(millw, os.perms(millw) + OWNER_EXECUTE + GROUP_EXECUTE + OTHERS_EXECUTE)
    target
  }
}
