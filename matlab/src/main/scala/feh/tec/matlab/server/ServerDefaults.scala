package feh.tec.matlab.server

import akka.actor.{ActorPath, ActorSelection, ActorSystem}
import com.typesafe.config.{ConfigValueFactory, Config, ConfigFactory}
import feh.tec.matlab.Server
import feh.tec.matlab.server.Default.ConnectionSetting

//object ServerDefaults {
////  def serverName = sys.props.get("matlab.server.name") getOrElse "default"
////  def systemName = sys.props.get("matlab.server.system") getOrElse "MatlabServer"
//  def serverName = "default"
//  def systemName = "MatlabServer"
//  def host = "localhost"
//  def port = 2553
//}

object Default{
  protected implicit class SetWrapper(conf: Config){
    def set(host: String, port: Int) = conf
      .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(host))
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))

    def actor(path: String) = conf.getString(path + ".actor")
    def system(path: String) = conf.getString(path + ".system")
    def host(path: String) = conf.getString(path + ".host")
    def port(path: String) = conf.getInt(path + ".port")

    def setFrom(path: String) = set(host(path), port(path))

    def toUri(path: String) = s"akka.tcp://${system(path)}@${host(path)}:${port(path)}/user/${actor(path)}"

    def create(path: String) = Server.create(actor(path), system(path), host(path), port(path))
  }

  private def defaultPrefix = "feh.tec.matlab-server"

  private val defaultConfig = {
    val c = ConfigFactory.load()
    c.setFrom(defaultPrefix)
  }
  def defaultName = defaultConfig.actor(defaultPrefix)

  implicit lazy val system = ActorSystem.create(defaultConfig.getString(defaultPrefix + ".system"), defaultConfig)
//  def sel = system.actorSelection(defaultConfig.toUri(defaultPrefix))

  def path = defaultConfig.toUri(defaultPrefix)

  class ConnectionSetting(prefix: String){
    val conf = defaultConfig.setFrom(prefix)

    implicit lazy val system = ActorSystem.create(conf.system(prefix), conf)

//    def sel = system.actorSelection(conf.toUri(prefix))

    def path = conf.toUri(prefix)

    def create() = conf.create(prefix)
  }

}

object DynControl extends ConnectionSetting("feh.tec.drone.dyn-control.matlab")
object DroneEmul extends ConnectionSetting("feh.tec.drone.emul.matlab")

class Default extends MatlabQueueServer(Default.defaultName)(Default.system)