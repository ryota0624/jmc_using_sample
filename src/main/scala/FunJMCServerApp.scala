import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import pureconfig._
import pureconfig.generic.auto._

case class FunJMCServerConfig(
    host: String,
    port: Int
)

class FunJMCServerApp {}

object FunJMCServerApp {
  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "fun-m")
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
      }

    val bindingFuture = Http().newServerAt(config.host, config.port).bind(route)

    println(
      s"Server online at http://${config.host}:${config.port}/\nPress `exit` to stop..."
    )

    var input = StdIn.readLine()
    while (input != "exit") {
      input = StdIn.readLine()
    }
    system.log.info("pressed `exit.` start shutdown.")
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
