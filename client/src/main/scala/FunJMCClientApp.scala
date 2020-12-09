import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.{Sink, Source}
import org.slf4j.LoggerFactory
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContextExecutor
import pureconfig._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Try}

case class FunJMCClientConfig(
    host: String,
    port: Int
)

case class RequestId(batchIndex: Long, requestSequenceNumber: Int)

object FunJMCClientApp {
  def main(args: Array[String]): Unit = {
    LoggerFactory.getLogger(getClass)

    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "fun-jmc-client")
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    val Array(
      count,
      numberOfIntervalSecond,
      executingDuration,
      numberOfParallels
    ) =
      Try(args.map(_.toInt))
        .filter(array => array.length >= 3)
        .getOrElse(Array(10, 1, 3, 2))

    system.log.info(
      s"${count}回のリクエストを${numberOfIntervalSecond}秒ごとに${numberOfParallels}並列で${executingDuration}秒間実行します"
    )
    val config =
      ConfigSource
        .fromConfig(system.settings.config.getConfig("fun-jmc-client"))
        .loadOrThrow[FunJMCClientConfig]

    val poolClientFlow =
      Http().cachedHostConnectionPool[RequestId](
        config.host,
        config.port,
        ConnectionPoolSettings(system = system)
          .withMaxOpenRequests(numberOfParallels)
      )

    val source =
      Source.tick(
        0.second,
        numberOfIntervalSecond.second,
        Range(0, count)
      )

    val cancel = source.zipWithIndex
      .flatMapConcat({
        case (range, index) =>
          Source.fromIterator(() => range.map(RequestId(index, _)).toIterator)
      })
      .map({ id =>
        val uri = s"http://${config.host}/increment"
        (HttpRequest(
          method = HttpMethods.POST,
          uri = uri
        ) -> id)
      })
      .via(poolClientFlow)
      .to(Sink.foreach[(Try[HttpResponse], RequestId)]({
        case (Failure(exception), _) =>
          system.log.debug(s"occurred request exception", exception)
        case (t, _) =>
          system.log.info(s"request completed ${t.toString}", t.toString)
      }))
      .run()

    cancel.cancel();

    requestsF.onComplete { _ =>
      system.terminate();
    }
  }
}

object Requester {
  trait Msg
  final case class Completed()

  private case object TimerKey
  private case object Timeout extends Msg

  def behavior(
      completed: ActorRef[Completed],
      after: FiniteDuration
  ): Behavior[Msg] =
    Behaviors.withTimers(timer =>
      Behaviors.setup(_ => {
        timer.startSingleTimer(TimerKey, Timeout, after)
        active(completed)
      })
    )

  private def active(
      completed: ActorRef[Completed]
  ): Behavior[Msg] = {
    Behaviors.receiveMessage[Msg] {
      case Timeout =>
        // 終了処理
        completed ! Completed()
        Behaviors.empty
    }
  }
}
