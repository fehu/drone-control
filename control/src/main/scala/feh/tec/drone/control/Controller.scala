package feh.tec.drone.control

import akka.actor.{ActorRef, Actor}
import scala.reflect.ClassTag

/**
 * Establishes connection,
 * Translates messages from decision (or backup) component 
 * // maintains connection (watchdog) 
 */
trait Controller extends Actor{
  type ControlCommand
  
  def ioControl: IOCommandChannel
  def forwarder: ActorRef

  def watchdog: ActorRef
}

/**
 * Forwards data read from drone feeds to listeners
 */
trait DataForwarder extends Actor{
  def ioData: Seq[DataFeed]
  def feedReaders: Map[DataFeed, ActorRef]
  
  def listeners: Map[ActorRef, Set[DataFeed]]
  
  trait FeedReader extends Actor{
    def feed: DataFeed
    def read(): DataFeed#Data
  }
}

trait DataFeed{
  type Data

  def channel: IOFeedChannel
  def parseData: Array[Byte] => Data
}

trait IOChannel{

}

trait IOCommandChannel extends IOChannel{

}

trait IOFeedChannel extends IOChannel{

}

