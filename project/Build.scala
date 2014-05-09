import java.io.File
import sbt.File
import sbt.ScalaVersion
import sbtassembly.Plugin._
import sbt._
import Keys._
import sbtassembly.Plugin.AssemblyKeys._
import sbtunidoc.Plugin._
import org.sbtidea.SbtIdeaPlugin._

object  Build extends sbt.Build {

  val ScalaVersion = "2.10.3"
  val Version = "0.2"
  val MatlabPath = sys.env.getOrElse("MATLAB_HOME", sys.error("set MATLAB_HOME environment variable"))

  import Resolvers._
  import Dependencies._

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "feh.tec.drone",
    version       := Version,
    scalaVersion  := ScalaVersion,
//    scalacOptions ++= Seq("-explaintypes"),
//    scalacOptions ++= Seq("-deprecation"),
    scalacOptions in (Compile, doc) ++= Seq("-diagrams")
//     mainClass in Compile := Some("")
  )

  // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

  object Resolvers{
    object Release{
      lazy val sonatype = "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
      lazy val spray = "spray" at "http://repo.spray.io/"
    }

    object Snapshot{
      lazy val sonatype = "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots"
      lazy val eulergui = "eulergui" at "http://eulergui.sourceforge.net/maven2"
    }

    def sonatype = Resolvers.Release.sonatype :: Resolvers.Snapshot.sonatype :: Nil
  }

  object Dependencies{
    lazy val shapeless = "com.chuusai" % "shapeless_2.10.2" % "2.0.0-M1"

    object akka{
      lazy val akkaVersion = "2.3.2"

      lazy val actor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
      lazy val remote = "com.typesafe.akka" %% "akka-remote" % akkaVersion
    }


    object scala{
      lazy val compiler = "org.scala-lang" % "scala-compiler" % ScalaVersion
      lazy val swing = "org.scala-lang" % "scala-swing" % ScalaVersion
      lazy val reflectApi = "org.scala-lang" % "scala-reflect" % ScalaVersion
    }

    object Apache{
      lazy val ioCommons = "commons-io" % "commons-io" % "2.4"
    }

    object spray{
      lazy val json = "io.spray" %%  "spray-json" % "1.2.5"
    }

    object Tests{
      lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.10.1" % "test"
      lazy val specs2 = "org.specs2" %% "specs2" % "2.2.2" % "test"
    }

    object jung{
      lazy val JungVersion = "2.0.1"

      lazy val jung2 = "net.sf.jung" % "jung2" % JungVersion
      lazy val api = "net.sf.jung" % "jung-api" % JungVersion
      lazy val graph = "net.sf.jung" % "jung-graph-impl" % JungVersion
      lazy val visualization = "net.sf.jung" % "jung-visualization" % JungVersion
      lazy val algorithms = "net.sf.jung" % "jung-algorithms" % JungVersion

      def all = jung2 :: api :: graph :: visualization :: algorithms :: Nil
    }


    object feh{
      lazy val util = "feh" %% "util" % "1.0.2"

      object utils{
        lazy val compiler = "feh.util" %% "scala-compiler-utils" % "0.1"
      }

      object dsl{
        lazy val swing = "feh.dsl" %% "swing" % "1.1"
        lazy val graphviz = "feh.dsl" %% "graphviz" % "0.1"
      }
    }

    object Bundle{
      lazy val version = "0.7"
      lazy val breeze = Seq(
        resolvers ++= Resolvers.sonatype,
        libraryDependencies ++= Seq(
          "org.scalanlp" % "breeze_2.10" % version,
          "org.scalanlp" % "breeze-natives_2.10" % version
        )
      )
    }

  }

  // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

  lazy val buildServerJar = TaskKey[Unit]("build-server-jar")

  // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

  lazy val serverBuildingSettings = assemblySettings ++ Seq(
    jarName in assembly := "matlab-server.jar",
    outputPath in assembly <<= (baseDirectory in Compile, jarName in assembly) map {
      (base , jar) =>
        val dir = base / "dist"
        if(dir.exists()) IO.delete(dir)
        IO.createDirectory(dir)
        dir / jar
    },
    mergeStrategy in assembly <<= (mergeStrategy in assembly, resourceDirectory in Compile){
      (old, resources) =>
        { // do not include mathworks jars
          case path if path.startsWith("com" + File.separator + "mathworks") => MergeStrategy.discard
          case x => old(x)
        }
    }
//    buildServerJar <<= (state, streams) map { (state, s) =>
//      val extracted = Project.extract(state)
//      val newState = extracted append (Seq(
//        resourceDirectory in Compile <<= (resourceDirectory in Compile) { ServerResourceDirectory.fromResource }
//      ), state)
//      Project.runTask(assembly, newState)
//    }
    )

  object ServerResourceDirectory{
    def fromResource(dir: File): File = file(dir.getPath + "-server")
  }

  // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

  lazy val root = Project(
    id = "root",
    base = file("."),
    settings = buildSettings ++ unidocSettings ++ {
      name := "drone-root"
    }
  ).settings(ideaExcludeFolders := ".idea" :: ".idea_modules" :: Nil)
   .aggregate(control, matlab)

  lazy val control = Project(
    id = "drone-control",
    base = file("control"),
    settings = buildSettings ++ Seq(
//      resolvers ++= Seq(Release.scalaNLP, Snapshot.scalaTools),
      libraryDependencies ++= Seq(akka.actor, feh.util /*scalala*/),
      initialCommands in console :=
        """
          |import akka.actor.ActorSystem
          |import feh.tec.drone.control.{EmulatorTest, TacticalPlanner}
          |import scala.concurrent.duration._
          |implicit val asys = ActorSystem.create()
          |lazy val core = new EmulatorTest.Core
          |def sendWaypoint(c: (Double, Double, Double)) = core.tacticalPlanner ! TacticalPlanner.SetWaypoints(c)
        """.stripMargin
    ) ++ Bundle.breeze
  ) dependsOn matlab

  lazy val matlab = Project(
    id = "matlab-connection",
    base = file("matlab"),
    settings = buildSettings ++ serverBuildingSettings ++ Seq(
      libraryDependencies ++= Seq(feh.util, akka.actor, akka.remote),
      unmanagedBase := file(MatlabPath + "/java/jar"),
      initialCommands in console :=
        """
          |import scala.concurrent.duration._
          |import feh.tec.matlab.server.Default.system._
          |import feh.tec.matlab._
          |val cl = new MatlabSimClient(actorSelection(server.Default.path))
          |val sim = new DroneSimulation(QuadModel.Drone, cl, 10 millis)
        """.stripMargin
    )
  )



}
