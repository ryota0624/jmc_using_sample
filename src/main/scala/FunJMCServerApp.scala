import FunJMCServerApp.counterRef
import akka.actor.typed._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.server.directives.LogEntry
import akka.stream.Attributes.LogLevels
import akka.util.Timeout
import org.slf4j.LoggerFactory
import pureconfig._
import pureconfig.generic.auto._

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

case class FunJMCServerConfig(
    host: String,
    port: Int
)

object IncrementalData {
  case class Data(str: String)
  object Data {
    def apply(): Data = Data(UUID.randomUUID().toString);
  }
}
class IncrementalData(
    @volatile private var data: mutable.ArrayBuffer[IncrementalData.Data] =
      mutable.ArrayBuffer()
) {
  def increment(): Unit =
    synchronized {
      data += IncrementalData.Data()
    }

  def clear(): Unit =
    synchronized {
      data = mutable.ArrayBuffer()
    }

  def size(): Int = data.length
}

class FunJMCServerApp(ctx: ActorContext[FunJMCServerApp.Command])
    extends AbstractBehavior[FunJMCServerApp.Command](ctx) {

  override def onMessage(
      msg: FunJMCServerApp.Command
  ): Behavior[FunJMCServerApp.Command] = {
    msg match {
      case FunJMCServerApp.SpawnCounter(id, replayTo) =>
        val ref = ctx.spawn(CounterActor.behavior(), counterRef(id))
        replayTo ! FunJMCServerApp.SpawnCounterResponse(ref)
        Behaviors.same

    }
  }
}

object SpawnCounterActor {
  def behavior(): Behavior[SpawnProtocol.Command] =
    Behaviors.logMessages(SpawnProtocol())
}

object FunJMCServerApp {
  def counterRef(id: CounterId): String = {
    s"counter-${id.toString}"
  }
  def apply(): Behavior[Command] =
    Behaviors.setup[FunJMCServerApp.Command](context =>
      new FunJMCServerApp(context)
    )

  trait Command
  case class SpawnCounter(
      id: CounterId,
      replayTo: ActorRef[SpawnCounterResponse]
  ) extends Command
  case class SpawnCounterResponse(ref: ActorRef[CounterActor.Message])
      extends Command

  def main(args: Array[String]): Unit = {

    implicit val timeout: Timeout = 10.seconds

    LoggerFactory.getLogger(getClass)

    val incrementalData = new IncrementalData()

    implicit val system: ActorSystem[SpawnProtocol.Command] =
      ActorSystem(SpawnCounterActor.behavior(), "fun-jmc")
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    val config =
      ConfigSource
        .fromConfig(system.settings.config.getConfig("fun-jmc"))
        .loadOrThrow[FunJMCServerConfig]

    def logging(
        request: HttpRequest
    )(result: RouteResult): Option[LogEntry] = {
      Some(LogEntry(s"${request.uri}", LogLevels.Info))
    }

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
      } ~ pathPrefix("counter")(
        logRequestResult(logging _)(CounterRoute(system))
      )

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
