package feh.tec.drone.control

import akka.actor.{Props, ActorRef, Actor}
import feh.tec.drone.control.DataForwarder.Forward
import scala.reflect.runtime.universe._

/** Listens to data, obtained from IO channel(s)
 */
trait DataListener[Data] extends Actor{
  def feeds: Map[DataFeed, DataFeed#Data => Data]

  protected def feedsEntry[F <: DataFeed](feed: F, build: F#Data => Data) =
    (feed -> build).asInstanceOf[(DataFeed, DataFeed#Data => Data)]

  type BuildData = PartialFunction[(DataFeed, DataFeed#Data), Data]

//  def buildDataMatcher[F <: DataFeed](f: F) =

  def buildData: BuildData

  def forwarded(data: Data)
  def start()
  def stop()

  def receive = {
    case Forward(feed, data) if feeds contains feed => forwarded(buildData(feed -> data))
    case Control.Start =>
      start()
      sender ! {}
    case Control.Stop =>
      stop()
      sender ! {}
  }
}

class BuildDataMatcher[F <: DataFeed](implicit tag: TypeTag[F]){
  def unapply(p: (DataFeed, DataFeed#Data)): Option[F#Data] =
    if(tag.tpe =:= p._1.dataTag.tpe) Some(p._2.asInstanceOf[F#Data]) else None
}
object BuildDataMatcher{
  def apply[F <: DataFeed: TypeTag] = new BuildDataMatcher[F]
}

class BuildForwardMatcher[F <: DataFeed](implicit tag: TypeTag[F]){
  def unapply(f: Forward[DataFeed]): Option[F#Data] =
    if(tag.tpe =:= f.feed.dataTag.tpe) Some(f.data.asInstanceOf[F#Data]) else None
}

class BuildFeedMatcher[F <: DataFeed](feed: F){
  def unapply(f: Forward[DataFeed]): Option[F#Data] = if (f.feed == feed) Some(f.data.asInstanceOf[F#Data]) else None
}

object BuildForwardMatcher{
  def apply[F <: DataFeed: TypeTag] = new BuildForwardMatcher[F]
}

/** Analyses data to advise the decider (or other analyzers)
 */
trait Analyzer[Data] extends DataListener[Data]{

}

/** Watches execution of a process and notifies the decider on completion and problems
 */
trait Watcher[Data] extends DataListener[Data]
{

}

/** Notifies the decider about certain data constraints violations, intended to be used for danger detection
 */
trait Guardian[Data] extends DataListener[Data]{

}

/** Wrapper for DataListener ActorRef
 */
trait DataListenerRef[Data]{
  def listener: ActorRef
}
