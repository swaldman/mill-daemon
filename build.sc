import mill._, scalalib._, publish._

trait MillDaemonCommon extends ScalaModule with PublishModule {
  override def scalacOptions = T{ Seq("-deprecation") }
  override def publishVersion = T{"0.1.2-SNAPSHOT"}

  def makePomSettings( description : String ) : PomSettings = 
    PomSettings(
      description = description,
      organization = "com.mchange",
      url = "https://github.com/swaldman/mill-daemon",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("swaldman", "mill-daemon"),
      developers = Seq(
	Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
      )
    )
}

object `package` extends RootModule with MillDaemonCommon {

  val MillVersion = "0.12.11"

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-main:${MillVersion}",
    ivy"com.lihaoyi::mill-scalalib:${MillVersion}",
  )

  override def scalaVersion = "2.13.15"
  override def artifactName = "mill-daemon"
  override def pomSettings    = T{ makePomSettings("A mill module that implements spawning of systemd-appropriate forking daemon processes") }

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

  object util extends MillDaemonCommon {
    override def ivyDeps = Agg(
      ivy"com.lihaoyi::os-lib:0.11.4"
    )
    override def scalaVersion = "3.3.6"
    override def artifactName = "mill-daemon-util"
    override def pomSettings  = T{ makePomSettings("Utilites for applications spawned by mill-daemon") }
  }
}
