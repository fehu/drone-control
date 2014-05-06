package feh.tec.matlab

import feh.tec.matlab.server.MatlabQueueServer
import akka.actor.ActorSystem
import com.typesafe.config._

object Server {
  def create(actorName: String,
             systemName: String,
             host: String,
             port: Int) =
  {
    val conf = ConfigFactory.load()
      .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(host))
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))

    new MatlabQueueServer(actorName)(ActorSystem.create(systemName, conf))
  }

//    RemoteMatlabQueueServer(actorName, host, port)(ActorSystem.create(systemName))

}
