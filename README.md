# mill-daemon

### Introduction

This project enables [mill](https://mill-build.com/mill/Intro_to_Mill.html) to properly launch systemd
daemons that rebuild themselves with each restart.

This renders convenient configuration-as-compiler-checked code, or quick-editing of templates that convert to
generated source code.

When you use _mill_ as a launcher, you can simply edit your configuration-as-code or your templates, then hit
`systemctl restart myservice` and watch your changes take immediate effect. You enjoy the ergonomics of an
interpreted language with the speed and typesafety of Scala.

### How it works

1. In `build.sc`, let your module extend [`DaemonModule`](src/com/mchange/milldaemon/DaemonModule.scala) defined in this library.
   That will give you access to mill commands
   * `runDaemon`
   * `runMainDaemon`

2. Override the function `def runDaemonPidFile : Option[os.Path]` to define a place where a PID file should be
   written by _mill_ prior to shutting down, but after spawning your process.

   (Consider using [`defaultRunDaemonPidFile(...)`](#more-on-runDaemonPidFile))

3. Include [mill wrapper](https://github.com/lefou/millw) in your project, and define a launch script that's something like
   ```plaintext
   #!/bin.bash

   ./millw runMainDaemon mypkg.MyMain "$@"
   ```
   (This presumes you've also extended `RootModule`. Otherwise, specify `mymodulename.runMainDaemon`.)

4. When you write your _systemd_ unit file, specify your daemon's `Type=forking`. Set `PIDFile=`
   to the location you specified in `runDaemonPidFile`.

5. Start your service (`systemctl start myservice`). Your service will build itself before it starts.
   Edit stuff, templates, config, core source. Type `systemctl restart myservice` and it will all rebuild.

### More on `runDaemonPidFile`

`DaemonModule` will not generate any PID file at all unless you set a file location by 
overriding `def runDaemonPidFile : Option[os.Path]`. 

You can override this to yield any
path you choose (as long as your daemon will have permission to write it).

_mill-daemon_ offers a default. Just write

```scala
  override def runDaemonPidFile : Option[os.Path] = defaultRunDaemonPidFile("mydaemon") // obviously, use your own daemon's name!
```

This will place the PID file at your build's workspace root directory under `mydaemon.pid`.
However, users will be able to define an alternative location by creating a file in the workspace root directory called `.pid-file-path`,
and providing in that file an absolute path.

This creates a good, sensible default, but also allows users to override this default without having to modify `build.mill`.

### Advanced

* If you asked mill to generate a PID file (by overriding `runDaemonPidFile`), your subprocess will have
  `MILL_DAEMON_PID_FILE` in its environment. You can use this to, for example, set up a shutdown hook that
  will delete the PID file when your process terminates.

  * _If you are running daemons under_ systemd _, this is just a nice-to-have backstop!_ systemd
    _will try to delete the PID file when your process terminates without your intervention.
    If you do set a shudown hook to delete the PID file 
    **please check that the file is a file whose content is your process' PID before deleting**.
    Don't blindly delete a file just because someone was able to get its path stuck in an environment variable._
  * There is now a supporting utility to help you do this properly.
    Just include the dependency `com.mchange::mill-daemon-util:<mill-daemon-version>` in your application,
    then

    ```scala
    import com.mchange.milldaemon.util.PidFileManager
    
    PidFileManager.installShutdownHookCarefulDelete()
    ```

* By default, the daemon subprocess inherits the `mill` launcher's standard-in and standard-out.
  That gives _systemd_ control over where they should be directed, and is usually what you want.
  However, you can override

  * `def runDaemonOut : os.ProcessOutput`
  * `def runDaemonErr : os.ProcessOutput`

  to take control of these streams yourself, if you prefer.

### Examples

* Check out a [_feedletter installation_](https://github.com/swaldman/feedletter-tickle) for a simple example.

### FAQ

**Why not just use the `runBackground` and `runMainBackground` tasks built into `JavaModule`?**

Applications started via `runBackground` and `runBackgroundMain` run embedded within a 
[`BackgroundWrapper`](https://github.com/com-lihaoyi/mill/blob/e171ad4c57c34a0bff2325327f8afc98d009f63d/scalalib/backgroundwrapper/src/mill/scalalib/backgroundwrapper/BackgroundWrapper.java) process which watches for changes in the files that built the application
and quits when those occur. This is great, exactly what you want, when you are using the `mill -w` watch
feature. Whenever you change its source (loosely construed), your application quits and restarts so that
you enjoy prompt updates.

However, this approach is not suitable for daemon processes, which are supposed to run stably and indefinitely,
and should not terminate just because someone edits a file or runs a task in the directories from which they emerged.

The `runDaemon` tasks here give you clean daemons, mostly decoupled from any continued activity in the build directories
after the parent `mill` process terminates.

When you update your `mill` build, use `systemctl restart <service>`. Until a restart , the "old" service will
continue in its old way.

(In theory, the daemon may not be _completely_ decoupled from activity its launch directory. Infrequently accessed
classes compiled into the directory might not be loaded immediately upon daemon launch, and if they are
deleted or incompatibly upgraded, your daemon could break when it finally requires them. In practice, this would be unusual.
Nevertheless, daemon launch installations shouldn't be active development directories, just sites for occasional 
modifications, reconfigurations, and relaunches.)
