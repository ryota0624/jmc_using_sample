import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.Source
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContextExecutor
import pureconfig._

import scala.util.{Failure, Try}

case class FunJMCClientConfig(
    host: String,
    port: Int
)

object FunJMCClientApp {
  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "fun-jmc-client")
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    val Array(count, numberOfParallels) =
      //   Try(args.map(_.toInt)).getOrElse
      (Array(10, 1))

    val config =
      ConfigSource
        .fromConfig(system.settings.config.getConfig("fun-jmc-client"))
        .loadOrThrow[FunJMCClientConfig]

    val poolClientFlow =
      Http().cachedHostConnectionPool[Int](
        config.host,
        config.port,
        ConnectionPoolSettings(system = system)
          .withMaxOpenRequests(numberOfParallels)
      )

    val source = Source.fromIterator(() => Range(0, count).toIterator)
    val requestsF = source
      .map({ int =>
        val uri = s"http://${config.host}/increment"
        system.log.info(s"request ${uri}")
        (HttpRequest(
          method = HttpMethods.POST,
          uri = uri
        ) -> int)
      })
      .via(poolClientFlow)
      .runForeach {
        case (Failure(exception), _) =>
          system.log.debug(s"occurred request exception", exception)
        case (t, _) =>
          system.log.info(s"request completed ${t.toString}", t.toString)

      }

    requestsF.onComplete { _ =>
      system.terminate();
    }
  }
}
