package feh.tec.util

import akka.actor.Actor
import akka.event.Logging._
import java.util.{Calendar, Date}
import java.text.SimpleDateFormat
import feh.util._
import java.io.{IOException, FileNotFoundException, File}
import feh.util.FileUtils._
import com.typesafe.config.ConfigFactory

object ToFileLogger{
  def configPath = "feh.tec.util.log.file"
}

class ToFileLogger extends Actor{

  def config = ConfigOverride.config getOrElse ConfigFactory.load()

  lazy val file =
    if(config.hasPath(ToFileLogger.configPath)) config.getString(ToFileLogger.configPath).file
    else null

  def write_? = file != null

  def receive = {
    case InitializeLogger(_) if write_? ⇒
      try {
        if(!file.exists()) file.createNewFile()
        writeToFile("\n" + "="*20 + " New Session [" + Calendar.getInstance().getTime + "] " + "="*20 + "\n\n")
        sender ! LoggerInitialized
      }
      catch {
        case thr: IOException => throw new LoggerInitializationException(s"error on accessing $file: " + thr.getMessage)
      }
    case InitializeLogger(_) ⇒ sender ! LoggerInitialized // no file writing
    case msg: Error if write_?     => error(msg)   |> writeToFile
    case msg: Warning if write_?   => warning(msg) |> writeToFile
    case msg: Info if write_?      => info(msg)    |> writeToFile
    case msg: Debug if write_?     => debug(msg)   |> writeToFile
    case msg: LogEvent if !write_? =>
  }


  private def writeToFile(str: String) {
    file.withOutputStream(FileUtils.File.write.utf8(str + "\n"), append = true)
  }
  // from StdOutLogger

  private val date = new Date()
  private val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS")
  private val errorFormat = "[ERROR] [%s] [%s] [%s] %s%s"
  private val errorFormatWithoutCause = "[ERROR] [%s] [%s] [%s] %s"
  private val warningFormat = "[WARN] [%s] [%s] [%s] %s"
  private val infoFormat = "[INFO] [%s] [%s] [%s] %s"
  private val debugFormat = "[DEBUG] [%s] [%s] [%s] %s"

  def timestamp(event: LogEvent): String = synchronized {
    date.setTime(event.timestamp)
    dateFormat.format(date)
  }

  def error(event: Error) = {
    val f = if (event.cause == Error.NoCause) errorFormatWithoutCause else errorFormat
    f.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message,
      stackTraceFor(event.cause))
  }

  def warning(event: Warning) =
    warningFormat.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message)

  def info(event: Info) =
    infoFormat.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message)

  def debug(event: Debug) =
    debugFormat.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message)
}
