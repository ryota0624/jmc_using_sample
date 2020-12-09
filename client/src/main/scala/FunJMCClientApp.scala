import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import org.slf4j.LoggerFactory
import pureconfig._
import pureconfig.generic.auto._
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.Try

case class FunJMCClientConfig(
    host: String,
    port: Int
)

case class RequestId(batchIndex: Long, requestSequenceNumber: Int)

object FunJMCClientApp {
  def main(args: Array[String]): Unit = {
    LoggerFactory.getLogger(getClass)

    val Array(
      count,
      numberOfIntervalSecond,
      executingDuration,
      numberOfParallels
    ) =
      Try(args.map(_.toInt))
        .filter(array => array.length >= 4)
        .getOrElse(Array(10, 1, 10, 2))

    implicit val timeout: Timeout = executingDuration.seconds + 30.seconds

    implicit val system: ActorSystem[Requester.Msg] =
      ActorSystem(
        Requester.behavior(
          count,
          executingDuration,
          numberOfIntervalSecond,
          numberOfParallels
        ),
        "fun-jmc-client"
      )

    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext
    system.log.info(
      s"${count}回のリクエストを${numberOfIntervalSecond}秒ごとに${numberOfParallels}並列で${executingDuration}秒間実行します"
    )
    system.ask[Requester.Completed](Requester.Start).onComplete { _ =>
      system.terminate();
    }
  }
}

object Requester {
  trait Msg
  final case class Completed()

  private case object TimerKey
  case class Start(replayTo: ActorRef[Completed]) extends Msg

  private case object Timeout extends Msg
  private case class RequestCompleted(
      id: RequestId,
      response: Try[HttpResponse]
  ) extends Msg

  def behavior(
      count: Int,
      executingDuration: Int,
      numberOfIntervalSecond: Int,
      numberOfParallels: Int
  ): Behaviors.Receive[Msg] = {
    Behaviors.receiveMessage[Msg] {
      case Start(replayTo) =>
        started(
          count,
          executingDuration,
          numberOfIntervalSecond,
          numberOfParallels,
          replayTo
        )
      case Timeout =>
        Behaviors.empty
      case _ => Behaviors.ignore

    }
  }

  private def started(
      count: Int,
      executingDuration: Int,
      numberOfIntervalSecond: Int,
      numberOfParallels: Int,
      completedReplayTo: ActorRef[Completed]
  ): Behavior[Msg] =
    Behaviors.withTimers(timer =>
      Behaviors.setup(ctx => {
        implicit val mat: Materializer = Materializer(ctx.system)
        val config =
          ConfigSource
            .fromConfig(ctx.system.settings.config.getConfig("fun-jmc-client"))
            .loadOrThrow[FunJMCClientConfig]
        timer.startSingleTimer(
          TimerKey,
          Timeout,
          executingDuration.seconds + 1.seconds
        )

        val poolClientFlow =
          Http()(ctx.system).cachedHostConnectionPool[RequestId](
            config.host,
            config.port,
            ConnectionPoolSettings(system = ctx.system)
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
              Source.fromIterator(() =>
                range.map(RequestId(index, _)).toIterator
              )
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
            case (response, requestId) =>
              ctx.self ! RequestCompleted(requestId, response)
          }))
          .run()
        active(
          0,
          occurredTimeout = false,
          count * executingDuration,
          completedReplayTo,
          cancel.cancel
        )
      })
    )

  private def active(
      completedRequestCount: Int,
      occurredTimeout: Boolean,
      totalCompletedCountFinally: Int,
      completedReplayTo: ActorRef[Completed],
      cancel: () => Boolean
  ): Behavior[Msg] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage[Msg] {
        case Timeout =>
          cancel()
          active(
            completedRequestCount,
            occurredTimeout = true,
            totalCompletedCountFinally,
            completedReplayTo,
            cancel
          )
        case RequestCompleted(id, _) =>
          ctx.log.debug(s"completed request ${id}")
          val nextCompletedRequestCount = completedRequestCount + 1
          if (nextCompletedRequestCount == totalCompletedCountFinally) {
            completedReplayTo ! Completed()
            Behaviors.empty
          } else {
            active(
              nextCompletedRequestCount,
              occurredTimeout,
              totalCompletedCountFinally,
              completedReplayTo,
              cancel
            )
          }
      }
    }
  }
}
