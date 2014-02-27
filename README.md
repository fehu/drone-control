Quadcopter Control
========================

## Matlab Module
_Provides asynchronous matlab connection using [akka](http://akka.io) remote actors_

        the sbt project requires MATLAB_HOME environment variable to be set, otherwise it won't initialize

In order for connection to work 'matlab-server.jar' must be included in matlab's java classpath
and a [MatlabServer][src MatlabServer] instance must be created within matlab workspace.
From the client side a [MatlabClient][src MatlabClient] establishes a connection with the server.
[DroneSimulation][src DroneSimulation] uses a _Model_ to run a simulink simulation and allows setting and getting it's params.

* The server jar is packaged by executing 'build-server-jar' sbt task in 'matlab-control' subproject
* To modify matlab classpath type 'edit classpath.txt' and add a line with a path to the jar. (keep file's last line empty)
* Currently there are two implementations of MatlabServer available:
    * _MatlabAsyncServer_ for normal usage
    * _MatlabQueueServer_, also providing simulink control capabilities
* There is a ready-to-use [Default][src Default] server (_MatlabQueueServer_ subclass), to use it just type

    `feh.tec.matlab.server.Default` in matlab's console

### Simulink Connection Default Implementation

In order to work within simulink, the server instance must be be assigned to **global** var _server_.
Also the default implementation requires [simExec][src simExec] and [setSimOnStart][src setSimOnStart] M-files.
The idea is setting an execution callback (_simExec_) on one of model's blocks, in this case on a plotter.
The callback executes **the first** expression (if any) expression from server's queue, thus providing access to
matlab's Main thread, which is unavailable via JMI's `Matlab.when[MatlabReady/MatlabIdle/AtPrompt]` methods during the simulation.


Start the server

```matlab
global server
server = feh.tec.matlab.server.Default;

Connect to it in Scala

```scala
import scala.concurrent.duration._
import feh.tec.matlab._
import server.Default.system._
```

```scala
val serverSelection = server.Default.sel // akka actor selection - reference to server actor
val client = new MatlabSimClient(serverSelection) // client provides methods eval, feval and start/stop simulation
val model = PCorke.Model // holds path to simulink's .mdl, model's name and parameters descriptions **
val simulation = new DroneSimulation(model, client, 30 seconds)
```

The code below uses [Futures][doc Futures]

```scala
simulation.init() map {
  println("Simulation initialized")
  simulation.start()
  // the model's params are used to specify ones to get or set
  simulation.getParam(PCorke.x).map(p => println(s"x = $p")) // print model's 'x' param asynchronously
  sim.setParam(PCorke.yaw, .1) // set model's 'yaw' param asynchronously; requires double
}
```

`**` _slightly modified P. Corke's quadcopter model_

### References

This projects uses:

* Matlab and Simulink www.mathworks.com
* Peter Corke's Robotics [Toolbox](http://petercorke.com/Robotics_Toolbox.html)




[src MatlabServer]: https://github.com/fehu/drone-control/blob/master/matlab/src/main/scala/feh/tec/matlab/server/MatlabServer.scala
[src MatlabClient]: https://github.com/fehu/drone-control/blob/master/matlab/src/main/scala/feh/tec/matlab/MatlabClient.scala
[src DroneSimulation]: https://github.com/fehu/drone-control/blob/master/matlab/src/main/scala/feh/tec/matlab/DroneSimulation.scala
[src Default]: https://github.com/fehu/drone-control/blob/master/matlab/src/main/scala/feh/tec/matlab/server/ServerDefaults.scala
[src simExec]: https://github.com/fehu/drone-control/blob/master/matlab/simExec.m
[src setSimOnStart]: https://github.com/fehu/drone-control/blob/master/matlab/setSimOnStart.m

[doc Futures]: http://docs.scala-lang.org/overviews/core/futures.html