import FunJMCServerApp.counterRef
import akka.actor.typed.{ActorRef, ActorSystem, Props, SpawnProtocol}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class Counter(count: Int) {
  def increment(): Counter = Counter(count + 1)
}

case class CounterId(id: UUID) {
  override def toString: String = id.toString
}

object CounterId {
  def apply(): CounterId = CounterId(UUID.randomUUID())
  def apply(str: String): CounterId = CounterId(UUID.fromString(str))
}

object Counter {
  def zero: Counter = Counter(0)
}

object CounterActor {

  def serviceKey: ServiceKey[Message] =
    ServiceKey[CounterActor.Message]("Counter")
  trait Message

  case class Increment(replayTo: ActorRef[ShowResult]) extends Message
  case object Reset extends Message
  case object Logging extends Message
  case class Show(replayTo: ActorRef[ShowResult]) extends Message

  case class ShowResult(count: Int)

  def behavior(counter: Counter = Counter.zero): Behaviors.Receive[Message] =
    Behaviors.receive[Message]({ (ctx, msg) =>
      msg match {
        case Increment(replayTo) =>
          val nextCounter = counter.increment()
          replayTo ! ShowResult(nextCounter.count)
          behavior(nextCounter)
        case Show(replayTo) =>
          replayTo ! ShowResult(counter.count)
          Behaviors.same
        case Reset =>
          behavior(Counter.zero)
        case Logging =>
          ctx.log.info(s"${ctx.self.path.toString}: ${counter.count}")
          Behaviors.same
      }
    })

}

object CounterRoute {
  implicit val timeout: Timeout = 10.seconds

  def apply(
      spawnRef: ActorRef[SpawnProtocol.Command]
  )(implicit system: ActorSystem[Nothing]): Route = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    pathEndOrSingleSlash {
      post {
        val id = CounterId()
        onComplete(
          spawnRef
            .ask[ActorRef[CounterActor.Message]](
              SpawnProtocol
                .Spawn(CounterActor.behavior(), counterRef(id), Props.empty, _)
            )
            .flatMap(ref =>
              system.receptionist
                .ask[Receptionist.Registered](
                  Receptionist.register(
                    CounterActor.serviceKey,
                    ref,
                    _
                  )
                )
            )
        ) {
          case Failure(exception) =>
            failWith(
              exception
            )
          case Success(_) =>
            complete(
              HttpEntity(
                ContentTypes.`text/plain(UTF-8)`,
                id.toString
              )
            )
        }
      }
    } ~ path("dump") {
      get {
        for {
          res <- system.receptionist.ask[Receptionist.Listing](
            Receptionist.find(CounterActor.serviceKey, _)
          )
        } yield {
          val set = res.allServiceInstances[CounterActor.Message](
            CounterActor.serviceKey
          )
          set.foreach(_ ! CounterActor.Logging)
        }
        complete(
          HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            "ok"
          )
        )
      }
    } ~ path("all") {
      get {
        onComplete {
          for {
            res <- system.receptionist.ask[Receptionist.Listing](
              Receptionist.find(CounterActor.serviceKey, _)
            )
            responses <- {
              val set = res.allServiceInstances[CounterActor.Message](
                CounterActor.serviceKey
              )
              Future.sequence(
                set.map(ref =>
                  ref
                    .ask(CounterActor.Show)
                    .map(result => ref.path.name -> result.count)
                )
              )
            }
          } yield responses
        } {
          case Failure(exception) =>
            failWith(exception)
          case Success(value) =>
            complete(
              HttpEntity(
                ContentTypes.`text/plain(UTF-8)`,
                value.toMap.toString()
              )
            )
        }
      }
    } ~ path(Segment) { idStr =>
      val id = CounterId(idStr)
      post {
        onComplete {
          for {
            refSet <- system.receptionist.ask[Receptionist.Listing](
              Receptionist.find(CounterActor.serviceKey, _)
            )
            res <- {
              val set = refSet.serviceInstances[CounterActor.Message](
                CounterActor.serviceKey
              )
              val ref = set
                .find(ref => ref.path.name == counterRef(id))
                .getOrElse {
                  throw new IllegalArgumentException(
                    s"counter id ${id.toString} is invalid"
                  )
                }

              ref.ask[CounterActor.ShowResult](CounterActor.Increment)
            }
          } yield {
            res
          }
        } {
          case Success(showResult) =>
            complete(
              HttpEntity(
                ContentTypes.`text/plain(UTF-8)`,
                showResult.count.toString
              )
            )
          case Failure(exception) =>
            failWith(
              exception
            )
        }
      }
    }
  }
}
