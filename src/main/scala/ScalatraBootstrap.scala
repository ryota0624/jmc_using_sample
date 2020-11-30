import javax.servlet.ServletContext
import org.scalatra._
// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON handling support from Scalatra
import org.scalatra.json._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new HealthCheckController, "/*")
  }
}

case class Health(ok: Boolean)

class HealthCheckController extends ScalatraServlet with JacksonJsonSupport {
  before() {
    contentType = formats("json")
  }

  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  get("/") {
    Health(ok = true)
  }

}
