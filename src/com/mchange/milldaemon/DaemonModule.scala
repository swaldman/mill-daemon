package com.mchange.milldaemon

import mill._, define._, scalalib._
import mill.api.{Ctx,Result}
import mill.define.Command
import mill.util.Jvm

import mainargs.arg

import scala.util.control.NonFatal

trait DaemonModule extends JavaModule {

  val EnvMillDaemonPidFile = "MILL_DAEMON_PID_FILE"

  def runDaemonOut : os.ProcessOutput = os.InheritRaw
  def runDaemonErr : os.ProcessOutput = os.InheritRaw

  def runDaemonPidFile : Option[os.Path] = None

  // modified from mill.util.Jvm.runSubprocessWithBackgroundOutputs
  /**
   * Runs a JVM subprocess as a freely forked daemon with the given configuration
   * @param mainClass The main class to run
   * @param classPath The classpath
   * @param JvmArgs Arguments given to the forked JVM
   * @param envArgs Environment variables used when starting the forked JVM
   * @param workingDir The working directory to be used by the forked JVM
   * @param daemonOutputs A tuple (stdout,stderr) for the spawned process
   * @param useCpPassingJar When `false`, the `-cp` parameter is used to pass the classpath
   *                        to the forked JVM.
   *                        When `true`, a temporary empty JAR is created
   *                        which contains a `Class-Path` manifest entry containing the actual classpath.
   *                        This might help with long classpaths on OS'es (like Windows)
   *                        which only supports limited command-line length
   */
  def runDaemonSubprocess(
      mainClass: String,
      classPath: Agg[os.Path],
      jvmArgs: Seq[String] = Seq.empty,
      envArgs: Map[String, String] = Map.empty,
      mainArgs: Seq[String] = Seq.empty,
      workingDir: os.Path = null,
      daemonOutputs: Tuple2[os.ProcessOutput, os.ProcessOutput],
      useCpPassingJar: Boolean = false
  )(implicit ctx: Ctx): os.SubProcess = {
    val cp =
      if (useCpPassingJar && !classPath.iterator.isEmpty) {
        val passingJar = os.temp(prefix = "run-", suffix = ".jar", deleteOnExit = false)
        ctx.log.debug(
          s"Creating classpath passing jar '${passingJar}' with Class-Path: ${classPath.iterator.map(
              _.toNIO.toUri().toURL().toExternalForm()
            ).mkString(" ")}"
        )
        Jvm.createClasspathPassingJar(passingJar, classPath)
        Agg(passingJar)
      } else {
        classPath
      }
    val javaExe = Jvm.javaExe
    val args =
      Vector(javaExe) ++
        jvmArgs ++
        Vector("-cp", cp.iterator.mkString(java.io.File.pathSeparator), mainClass) ++
        mainArgs

    ctx.log.debug(s"Run daemon subprocess with args: ${args.map(a => s"'${a}'").mkString(" ")}")

    // from 0.12.0+, we can't use mill utility JVM.spawnSubprocessWithBackgroundOutputs(...)
    // because the newer version of os-lib it uses defaults to destroying the subprocess on parent
    // process exit.

    os.proc(args).spawn(
      cwd = workingDir,
      env = envArgs,
      stdin = "",
      stdout = daemonOutputs._1,
      stderr = daemonOutputs._2,
      destroyOnExit = false
    )
  }

  lazy val ProcessPidMethod : java.lang.reflect.Method =
    try {
      classOf[Process].getMethod("pid")
    }
    catch {
      case nsme : NoSuchMethodException =>
        throw new Exception("Pre Java 9 JVMs do not support discovery of process PIDs.", nsme)
    }

  private def pid( subProcess : os.SubProcess ) : Long = {
    val jproc = subProcess.wrapped
    ProcessPidMethod.invoke( jproc ).asInstanceOf[Long]
  }

  private def canWrite( ctx : Ctx, path : os.Path ) : Boolean = {
    val f = path.toIO
    if (os.exists(path)) f.canWrite()
    else {
      try {
        f.createNewFile()
      }
      catch {
        case NonFatal(t) =>
          ctx.log.debug(s"Exception while testing file creation: $t")
          false
      }
      finally {
        try f.delete()
        catch {
          case NonFatal(t) =>
            ctx.log.debug(s"Exception while cleaning up (deleting) file creation test: $t")
        }
      }
    }
  }

  protected def doRunDaemon(
      runClasspath: Seq[PathRef],
      forkArgs: Seq[String],
      forkEnv: Map[String, String],
      finalMainClass: String,
      forkWorkingDir: os.Path,
      runUseArgsFile: Boolean,
      daemonOutputs: Tuple2[os.ProcessOutput, os.ProcessOutput],
      pidFile: Option[os.Path]
  )(args: String*): Ctx => Result[os.SubProcess] = ctx => {
    def spawnIt( extraEnv : Seq[(String,String)] = Nil ) =
      runDaemonSubprocess(
        finalMainClass,
        runClasspath.map(_.path),
        forkArgs,
        forkEnv ++ extraEnv,
        args,
        workingDir = forkWorkingDir,
        daemonOutputs,
        useCpPassingJar = runUseArgsFile
      )(ctx)

    try {
      pidFile match {
        case Some( path ) if os.exists( path ) =>
          Result.Failure(s"A file already exists at PID file location ${path}. Please ensure no daemon is currently running, then delete this file.")
        case Some( path ) if !canWrite(ctx,path) =>
          Result.Failure(s"Insufficient permission: Cannot write PID file to location ${path}.")
        case Some( path ) =>
          val subProcess = spawnIt( Seq( EnvMillDaemonPidFile -> path.toString() ) )
          os.write( path, data = pid(subProcess).toString() + System.lineSeparator() )
          Result.Success(subProcess)
        case None =>
          Result.Success(spawnIt())
      }
    }
    catch {
      case NonFatal(t) =>
        Result.Failure("Failed to spawn daemon subprocess: " + t.printStackTrace())
    }
  }

  /**
   * Runs this module's code in the background as a freestanding daemon process.
   * The process will run indefinitely, until it exits or it is terminated externally.
   * It can survive termination of the parent mill process.
   */
  def runDaemon(args: String*): Command[Unit] = T.command {
    val ctx = implicitly[Ctx]
    val rsubp =
      doRunDaemon(
        runClasspath   = runClasspath(),
        forkArgs       = forkArgs(),
        forkEnv        = forkEnv(),
        finalMainClass = finalMainClass(),
        forkWorkingDir = forkWorkingDir(),
        runUseArgsFile = runUseArgsFile(),
        daemonOutputs  = ( runDaemonOut, runDaemonErr ),
        pidFile = runDaemonPidFile
      )(args: _*)(ctx)
    rsubp.map( _ => () )
  }

  /**
   * Same as `runDaemon`, but lets you specify a main class to run
   */
  def runMainDaemon(@arg(positional = true) mainClass: String, args: String*): Command[Unit] = T.command {
    val ctx = implicitly[Ctx]
    val rsubp =
      doRunDaemon(
        runClasspath   = runClasspath(),
        forkArgs       = forkArgs(),
        forkEnv        = forkEnv(),
        finalMainClass = mainClass,
        forkWorkingDir = forkWorkingDir(),
        runUseArgsFile = runUseArgsFile(),
        daemonOutputs  = ( runDaemonOut, runDaemonErr ),
        pidFile = runDaemonPidFile
      )(args: _*)(ctx)
    rsubp.map( _ => () )
  }
}
