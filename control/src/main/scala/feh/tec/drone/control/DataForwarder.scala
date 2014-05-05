package feh.tec.drone.control

import akka.actor._
import akka.util.Timeout
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import feh.util._
import feh.tec.drone.control.FeedReader.ReadRawAndForward

/**
 * Forwards data read from drone feeds to listeners
 */
trait DataForwarder extends Actor{
  def channel: IOFeedChannel
  
  def readerForFeed: Map[DataFeed, ActorRef]

  def listeners: Map[ActorRef, Set[DataFeed]]
}

trait FeedReader extends Actor{
  val feed: DataFeed
  /** reads data from raw bytes received from the drone
   */
  def read(raw: Array[Byte]): feed.Data
}


object DataForwarder{
  trait InMessage
  trait OutMessage

  case class Subscribe(feed: DataFeed) extends InMessage
  case class Unsubscribe(feed: DataFeed) extends InMessage
  case class Read(raw: Array[Byte]) extends InMessage

  case class Forward[Feed <: DataFeed](feed: Feed, data: Feed#Data) extends OutMessage
  case class FeedError(feed: DataFeed, err: Throwable) extends OutMessage

/*
  def create(controller: ActorRef,
             channel: IOFeedChannel,
             readers: Map[DataFeed, Props],
             controlTimeout: Timeout)
            (implicit system: ActorSystem) = system.actorOf(props(controller, channel, readers, controlTimeout))
*/
  def props(controller: ActorRef,
            channel: IOFeedChannel,
            readers: ActorRef => Map[DataFeed, Props],
            controlTimeout: Timeout)
           (implicit system: ActorSystem) = Props(classOf[GenericDataForwarder], controller, channel, readers, controlTimeout, system)

  case object Tick

  def channelReader(channel: IOFeedChannel,
                    tryReading: IOFeedChannel => Option[Array[Byte]],
                    forwarder: ActorRef,
                    scheduler: Scheduler,
                    execContext: ExecutionContext,
                    freq: FiniteDuration) = Props(classOf[ChannelReader], channel, tryReading, forwarder, scheduler, execContext, freq)
  
  class ChannelReader(channel: IOFeedChannel,
                      tryReading: IOFeedChannel => Option[Array[Byte]],
                      forwarder: ActorRef,
                      scheduler: Scheduler,
                      implicit val execContext: ExecutionContext,
                      freq: FiniteDuration) extends Actor{
    var enabled = false

    def schedule() = scheduler.scheduleOnce(freq, self, Tick)

    def receive = {
      case Control.Start =>
        enabled = true
        schedule()
      case Control.Stop =>
        enabled = false
      case Tick if enabled =>
        tryReading(channel) map {
          raw => forwarder ! Read(raw)
        }
        schedule()
      case Tick =>
    }
  }

  class GenericDataForwarder(controller: ActorRef,
                             val channel: IOFeedChannel,
                             tryReading: IOFeedChannel => Option[Array[Byte]],
                             readers: ActorRef => Map[DataFeed, Props],
                             tryReadFreqDelay: FiniteDuration,
                             implicit val system: ActorSystem) extends DataForwarder{

    lazy val channelReader = system.actorOf(
      DataForwarder.channelReader(channel, tryReading, self, system.scheduler, system.dispatcher, tryReadFreqDelay)
    )
    
    lazy val readerForFeed = readers(self).map{
      case (feed, props) => feed -> system.actorOf(props)
    }
    lazy val feedByRef = readerForFeed.map(_.swap).toMap

    val _listeners = mutable.HashMap.empty[ActorRef, Set[DataFeed]]
    def listeners = _listeners.toMap

    def receive = {
      case control: Control.Message => msgControl(control)
      case in: InMessage => msgIn(in)
    }

    def msgControl: PartialFunction[Control.Message, Unit] = {
      case Control.Start =>
        readerForFeed; feedByRef;
        channelReader ! Control.Start
      case Control.Stop =>
    }

    def msgIn: PartialFunction[InMessage, Unit] = {
      case Subscribe(feed) => _listeners <<=(sender, _ + feed)
      case Unsubscribe(feed) => _listeners <<=(sender, _ - feed)
      case Read(raw) =>
        for{
          (feed, reader) <- readerForFeed
          l = listeners.filter(_._2.contains(feed)).keySet
        } reader ! ReadRawAndForward(raw, l)
    }
  }
}

object FeedReader{
  trait Message

  case class ReadRawAndForward(raw: Array[Byte], forwardTo: Set[ActorRef]) extends Message

  def generic[Feed <: DataFeed, Ch <: IOFeedChannel](feed: Feed,
                                                     forwarder: ActorRef,
                                                     read: Array[Byte] => Option[Feed#Data]) =
    Props(classOf[GenericFeedReader[Feed, Ch]], feed, forwarder, read)

  class GenericFeedReader[Feed <: DataFeed, Ch <: IOCommandChannel](val feed: Feed,
                                                                    forwarder: ActorRef,
                                                                    readRaw: Array[Byte] => Feed#Data)
    extends FeedReader
  {
    /** reads data from raw bytes received from the drone
      */
    def read(raw: Array[Byte]) = readRaw(raw).asInstanceOf[feed.Data]

    def receive = {
      case ReadRawAndForward(arr, to) => to.foreach(_ ! DataForwarder.Forward(feed, read(arr)))
    }
  }
}
