package com.mchange.milldaemon.util

import scala.util.control.NonFatal

object PidFileManager {
  lazy val location : Option[String] = sys.env.get("MILL_DAEMON_PID_FILE")
  
  def shutdownHookCarefulDelete(verbose : Boolean = true) : Option[Thread] = {
    location.map { pidFileLoc =>
      val pidFilePath = os.Path( pidFileLoc )
      new Thread() {
        override def run() : Unit = {
          try {
            if (os.exists(pidFilePath)) {
              val myPid = ProcessHandle.current().pid()
              val fromFilePid = os.read(pidFilePath).trim().toLong
              if (myPid == fromFilePid) {
                if (verbose) System.err.println(s"Shutdown Hook: Removing PID file '${pidFilePath}'")
                os.remove( pidFilePath )
              }
            }
          }
          catch {
            case NonFatal(t) => {
              if (verbose) {
                System.err.println("WARNING: Throwable while executing autoremove PID file shutdown hook.")
                t.printStackTrace()
              }
            }  
          }
        }
      }
    }
  }
  def installShutdownHookCarefulDelete(verbose : Boolean = true) : Unit = {
    shutdownHookCarefulDelete(verbose).foreach( t => Runtime.getRuntime().addShutdownHook(t) )
  }
}
