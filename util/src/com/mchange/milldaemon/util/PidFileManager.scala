package com.mchange.milldaemon.util

import scala.util.control.NonFatal

object PidFileManager {
  private val stdErrLogInfo    : String => Unit = msg => System.err.println(s"INFO: ${msg}")
  private val stdErrLogWarning : String => Unit = msg => System.err.println(s"WARNING: ${msg}")

  lazy val location : Option[String] = sys.env.get("MILL_DAEMON_PID_FILE")
  
  def shutdownHookCarefulDelete(logInfo : String => Unit = stdErrLogInfo, logWarning : String => Unit = stdErrLogWarning) : Option[Thread] = {
    location.map { pidFileLoc =>
      val pidFilePath = os.Path( pidFileLoc )
      new Thread() {
        override def run() : Unit = {
          try {
            if (os.exists(pidFilePath)) {
              val myPid = ProcessHandle.current().pid()
              val fromFilePid = os.read(pidFilePath).trim().toLong
              if myPid == fromFilePid then
                logInfo(s"Shutdown Hook: Removing PID file '${pidFilePath}'")
                os.remove( pidFilePath )
            }
          }
          catch {
            case NonFatal(t) =>
              logWarning("Throwable while executing autoremove PID file shutdown hook.")
              t.printStackTrace()
          }
        }
      }
    }
  }
  def installShutdownHookCarefulDelete(logInfo : String => Unit = stdErrLogInfo, logWarning : String => Unit = stdErrLogWarning) : Unit = {
    shutdownHookCarefulDelete(logInfo, logWarning).foreach( t => Runtime.getRuntime().addShutdownHook(t) )
  }
}
