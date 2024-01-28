# mill-daemon

### Introduction

This project enables [mill](https://mill-build.com/mill/Intro_to_Mill.html) to properly launch systemd
daemons that rebuild themselves with each restart.

This renders convenient configuration as compiler-checked code, or use of templates that become 
generated source code. 

When you use _mill_ as a launcher, you can simply edit your configuration-as-code or your templates, then
`systemctl restart myservice` and watch your changes take immediate effect. You enjoy the ergonomics of an
interpreted language with the speed and typesafety of Scala.

### How it works

1. In `build.sc`, let your module extend `DaemonModule` defined in this package.
   That will give you access to the tasks `runDaemon` and `runMainDaemon`.

2. Override the function `runDaemonPidFile` to define a place where a PID file should be
   written by _mill_ prior to shutting down, but after spawning your process.

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

### Examples

* Check out [_feedletter install_](https://github.com/swaldman/feedletter-install) for a simple example.

### FAQ

**Why not just use the `runMainBackground` task built into `JavaModule` (and `ScalaModule` by inheritance)?**

Applications started via `runBackground` and `runBackgroundMain` are embedded within a 
[`BackgroundWrapper`](https://github.com/com-lihaoyi/mill/blob/e171ad4c57c34a0bff2325327f8afc98d009f63d/scalalib/backgroundwrapper/src/mill/scalalib/backgroundwrapper/BackgroundWrapper.java) process which watches for changes in the files that built the application
and quits when those occur. This is great, exactly what you want, when you are using the `mill -w` watch
feature. Whenever you change its source (loosely construed), your application quits and restarts so that
you enjoy prompt upgrades.

However, this approach is not suitable for daemon processes, which are supposed to run stably and indefinitely,
and should not terminate just because someone is working in the development directories from which they emerged.

The `runDaemon` tasks here give you clean daemons, fully decoupled from your build and whatever may happen next in build directories,
after the parent `mill` process terminates.
