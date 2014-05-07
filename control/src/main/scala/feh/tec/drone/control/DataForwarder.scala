package feh.tec.drone.control

import akka.actor._
import akka.util.Timeout
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import feh.util._
import feh.tec.drone.control.FeedReader.ReadRawAndForward
import feh.tec.drone.control.DataForwarder.Forward
import akka.event.Logging
import akka.pattern.ask
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

  case class Forward[Feed <: DataFeed](feed: Feed, data: Feed#Data) extends InMessage with OutMessage
  case class FeedError(feed: DataFeed, err: Throwable) extends OutMessage

/*
  def create(controller: ActorRef,
             channel: IOFeedChannel,
             readers: Map[DataFeed, Props],
             controlTimeout: Timeout)
            (implicit system: ActorSystem) = system.actorOf(props(controller, channel, readers, controlTimeout))
*/
  def props(channel: IOFeedChannel,
            tryReading: IOFeedChannel => Option[Array[Byte]],
            readers: ActorRef => Map[DataFeed, FeedReaderProps],
            extraFeedsProps:  Map[DataFeed, FeedNotifierProps],
            tryReadFreqDelay: FiniteDuration)
           (implicit system: ActorSystem) =
    Props(classOf[GenericDataForwarder], channel, tryReading, readers, extraFeedsProps, tryReadFreqDelay, system)

  case object Tick

  def channelReader(channel: IOFeedChannel,
                    tryReading: IOFeedChannel => Option[Array[Byte]],
                    forwarder: ActorRef,
                    freq: FiniteDuration) = Props(classOf[ChannelReader], channel, tryReading, forwarder, freq)
  
  class ChannelReader(channel: IOFeedChannel,
                      tryReading: IOFeedChannel => Option[Array[Byte]],
                      forwarder: ActorRef,
                      freq: FiniteDuration) extends Actor{
    var enabled = false

    def scheduler = context.system.scheduler
    implicit def execContext = context.system.dispatcher

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

  class GenericDataForwarder(val channel: IOFeedChannel,
                             tryReading: IOFeedChannel => Option[Array[Byte]],
                             readers: ActorRef => Map[DataFeed, FeedReaderProps],
                             extraFeedsProps:  Map[DataFeed, FeedNotifierProps],
                             tryReadFreqDelay: FiniteDuration,
                             implicit val system: ActorSystem) extends DataForwarder{

    import system._

    lazy val channelReader = system.actorOf(
      DataForwarder.channelReader(channel, tryReading, self, tryReadFreqDelay)
    )
    
    lazy val readerForFeed = readers(self).map{
      case (feed, props) => feed -> system.actorOf(props.get)
    }
    lazy val feedByRef = readerForFeed.map(_.swap).toMap

    val _listeners = mutable.HashMap.empty[ActorRef, Set[DataFeed]]
    def listeners = _listeners.toMap

    def receive = {
      case control: Control.Message => msgControl(control)
      case in: InMessage => msgIn(in)
    }

    lazy val extraFeeds = extraFeedsProps.mapValues(system actorOf _.props)

    def msgControl: PartialFunction[Control.Message, Unit] = {
      case Control.Start =>
        readerForFeed; feedByRef; extraFeeds // init lazy vals
        channelReader ! Control.Start
        val s = sender()
        Future.sequence(extraFeeds map {
          case (k, v) => (v ? Control.Start)(extraFeedsProps(k).startupTimeout)
        }) onComplete {
         t =>
           log.info("all extra feeds initialized")
           s ! t.map(_ => Unit)
        }
      case Control.Stop =>
        channelReader ! Control.Stop
        extraFeeds foreach(_._2 ! Control.Stop)
    }

    private def listenersFor(feed: DataFeed) = listeners.filter(_._2.contains(feed)).keySet

    protected val log = Logging(context.system, this)

    def msgIn: PartialFunction[InMessage, Unit] = {
      case Subscribe(feed) if sender != ActorRef.noSender =>
        if(!_listeners.contains(sender)) _listeners += sender -> Set()
        _listeners <<=(sender, _ + feed)
      case Unsubscribe(feed) if sender != ActorRef.noSender => _listeners <<=(sender, _ - feed)
      case f@Forward(feed, data) if extraFeeds.keySet contains feed =>
        log.info(s"forwarding $feed data: $data")
        listenersFor(feed) foreach (_ ! f)
      case Read(raw) =>
        for{
          (feed, reader) <- readerForFeed
          l = listenersFor(feed)
        } reader ! ReadRawAndForward(raw, l)
    }
  }
}

case class FeedReaderProps(props: Props){ def get: Props = props }
object FeedReaderProps{
  implicit def feedReaderPropsWrapper(fp: FeedReaderProps) = fp.props
}

object FeedReader{

  trait Message

  case class ReadRawAndForward(raw: Array[Byte], forwardTo: Set[ActorRef]) extends Message

  def generic[Feed <: DataFeed, Ch <: IOFeedChannel](feed: Feed,
                                                     read: Array[Byte] => Option[Feed#Data]) =
    FeedReaderProps(Props(classOf[GenericFeedReader[Feed, Ch]], feed, read))

  class GenericFeedReader[Feed <: DataFeed, Ch <: IOCommandChannel](val feed: Feed,
                                                                    readRaw: Array[Byte] => Feed#Data)
    extends FeedReader
  {
    protected val log = Logging(context.system, this)

    /** reads data from raw bytes received from the drone
      */
    def read(raw: Array[Byte]) = {
      val data = readRaw(raw).asInstanceOf[feed.Data]
      log.info(s"Data read from feed $feed: $data")
      data
    }

    def receive = {
      case ReadRawAndForward(arr, to) => to.foreach(_ ! DataForwarder.Forward(feed, read(arr)))
    }
  }
}

trait FeedNotifier extends Actor{
  type NotifyFeed <: DataFeed

  def forwarder: ActorRef
  def notifyFeed: NotifyFeed

  def on_? : Boolean

  def notifyForwarder(data: NotifyFeed#Data) = if(on_?) forwarder ! Forward[NotifyFeed](notifyFeed, data)

}

case class FeedNotifierProps(props: Props, startupTimeout: Timeout){ def get = props }

object FeedNotifierProps{
  def apply(clazz: Class[_], params: Any*)(startup: Timeout) =
    new FeedNotifierProps(Props(clazz, params: _*), startup)
}

class ForwarderLazyRef(getF: => ActorRef){
  lazy val get = getF
}