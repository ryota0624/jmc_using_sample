import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import pureconfig._
import pureconfig.generic.auto._

import scala.collection.mutable

case class FunJMCServerConfig(
    host: String,
    port: Int
)

class FunJMCServerApp {}

class IncrementalData(
    @volatile private var idSeq: mutable.ArrayBuffer[String] =
      mutable.ArrayBuffer()
) {
  def increment(): Unit =
    synchronized {
      idSeq += UUID.randomUUID().toString
    }

  def clear(): Unit =
    synchronized {
      idSeq = mutable.ArrayBuffer()
    }

  def size(): Int = idSeq.length
}

object FunJMCServerApp {
  def main(args: Array[String]): Unit = {
    LoggerFactory.getLogger(getClass)

    val incrementalData = new IncrementalData()

    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "fun-jmc")
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    val config =
      ConfigSource
        .fromConfig(system.settings.config.getConfig("fun-jmc"))
        .loadOrThrow[FunJMCServerConfig]
    system.log.info(config.toString)

    val route =
      path("hello") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              "<h1>Say hello to akka-http</h1>"
            )
          )
        }
      } ~ path("size") {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            s"<h1>data size: ${incrementalData.size()}</h1>"
          )
        )
      } ~ path("increment") {
        post {
          system.log.info("called increment")
          incrementalData.increment();
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              s"<h1>data size: ${incrementalData.size()}</h1>"
            )
          )
        }
      } ~ path("clear") {
        post {
          incrementalData.clear();
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              s"<h1>cleared data</h1>"
            )
          )
        }
      }

    val bindingFuture = Http().newServerAt(config.host, config.port).bind(route)

    println(
      s"Server online at http://${config.host}:${config.port}/\nPress `exit` to stop..."
    )

    var input = StdIn.readLine()
    while (input != "exit") {
      input = StdIn.readLine()
    }
    println("pressed `exit.` start shutdown.")
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
