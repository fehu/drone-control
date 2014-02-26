package feh.tec.matlab.server

import akka.actor.ActorSystem

object ServerDefaults {
//  def serverName = sys.props.get("matlab.server.name") getOrElse "default"
//  def systemName = sys.props.get("matlab.server.system") getOrElse "MatlabServer"
  def serverName = "default"
  def systemName = "MatlabServer"
  def host = "localhost"
  def port = 2553
}

object Default{
  import ServerDefaults._

  implicit lazy val system = ActorSystem.create(systemName)
  def sel = system.actorSelection(s"akka.tcp://$systemName@$host:$port/user/$serverName")
}

class Default extends MatlabServer(ServerDefaults.serverName)(Default.system)