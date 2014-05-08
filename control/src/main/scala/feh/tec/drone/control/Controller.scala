package feh.tec.drone.control

import akka.actor._
import scala.reflect.runtime.universe._

/**
 * Establishes connection,
 * Translates messages from decision (or backup) component 
 * // maintains connection (watchdog) 
 */
trait Controller extends Actor{
  def ioControl: IOCommandChannel

  def watchdog: ActorRef
}

trait DataFeed{
  type Data
  def dataTag: TypeTag[Data]
  def name: String
//  def parseData: Array[Byte] => Data
}

/** For abstracting feed descriptions
 * overrides equals, so that children match
 */
protected[drone] trait AbstractDataFeed[A <: AbstractDataFeed[A]] extends DataFeed{
  self: A =>

  override def equals(obj: scala.Any) = obj.isInstanceOf[A]
}

/*sealed trait DataFeedRef[Data]{
  def buildData: PartialFunction[DataFeed, Data]
  def toList: List[DataFeed]
  def toSet: Set[DataFeed]
}

case class SingleFeedRef[Data, Feed <: DataFeed](feed: Feed, buildData: Feed => Data) extends DataFeedRef[Data]{
  def toList = feed :: Nil
  def toSet = Set(feed)
}

case class MultipleFeedsRef[Data](head: DataFeedRef[Data],
                                  tail: MultipleFeedsRef[Data]) extends DataFeedRef[Data]{
  lazy val feedsRef = {
    def rec(ref: DataFeedRef[Data]): List[SingleFeedRef[Data, DataFeed]] = ref match{
      case ref@SingleFeedRef(_, _) => ref :: Nil
      case MultipleFeedsRef(left, right) => rec(left) ::: rec(right)
    }
    rec(this)
  }
  lazy val refsMap = feedsRef.map(ref => ref.feed -> ref).toMap

  def toList = feedsRef.map(_.feed)
  def toSet = toList.toSet

//  def buildData = feed =>
//    feedsRef.find(_.feed == feed).map(_.buildData(feed)) getOrElse sys.error(s"feed $feed not supported by $this")
  def buildData = {
    case feed if refsMap.contains(feed) => refsMap(feed).buildData
  }
}

object FeedsRef{
  def create[Data](feeds: DataFeed*)(build: PartialFunction[DataFeed, Data]): DataFeedRef[Data] =
    if(feeds.size == 1) SingleFeedRef(feeds.head, build)
    else MultipleFeedsRef(SingleFeedRef(feeds.head, build), create())
}*/

trait IOChannel{
  def connected_? : Boolean
}

trait IOCommandChannel extends IOChannel{
  def write(b: Array[Byte])
}

trait IOFeedChannel extends IOChannel{
  def read(): Option[Array[Byte]]
}

object Controller{
  trait Req
  trait Resp
}

object Control{
  trait Message
  trait Response{
    def success: Boolean
    final def failure = !success
  }

  case object Start extends Message
  case object Stop extends Message

  type Success = Success.type
  case object Success extends Response { def success = true }
  case class Error(thr: Throwable) extends Response { def success = false }
  
/*
  def sendAndForwardResponses(msg: Control.Message, receivers: Seq[ActorRef], responseReceiver: ActorRef)
                            (implicit timeout: Timeout, execContext: ExecutionContext) =
    Future.sequence(receivers.map(receiver => (receiver ? msg).mapTo[Control.Response])) map {
      r =>
        if(r.forall(_.success)) responseReceiver ! Control.Success
        else r.withFilter(_.failure).foreach(responseReceiver !)
    }
*/
}


