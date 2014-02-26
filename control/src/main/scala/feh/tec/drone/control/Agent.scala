package feh.tec.drone.control

import scala.reflect.ClassTag
import akka.util.Timeout
import scala.concurrent.Future
import akka.actor.{Actor, ActorRef}
/*

trait Language{
  type Expr <: LanguageExpression
}

trait LanguageExpression
case class AgentId(id: String)

trait Reactor{
  def respond: PartialFunction[LanguageExpression, Response]
}

trait AbstractAgent[+Action, +Response] extends Reactor[Response]{
  type AgentActor <: Actor
  def actorClass: Class[AgentActor]

  def id: AgentId
  def decide(in: Any): Action


  def actor: ActorRef
}

trait AgentsController{
  type Response

  protected def agentLangTag: AgentId => ClassTag[_ <: LanguageExpression]
  protected[control] def register(ag: AbstractAgent[_, Response])
  protected[control] def registered: Seq[AbstractAgent[_, Response]]

  def tell[Expr: ClassTag](whom: AgentId, what: Expr)(implicit sender: AgentId)
  def ask[Expr: ClassTag](whom: AgentId, what: Expr)(implicit sender: AgentId, timeout: Timeout): Future[Expr]

  def handleResponse(resp: Response)
}*/
