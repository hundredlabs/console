package web.controllers.proxy

import controllers.AssetsFinder
import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.libs.ws.WSClient
import play.api.mvc.{ControllerComponents, InjectedController}

import scala.concurrent.ExecutionContext

class SparkProxyController @Inject()(
    ws: WSClient,
                                     components: ControllerComponents)(
                                      implicit
                                      assets: AssetsFinder,
                                      ex: ExecutionContext
                                    ) extends InjectedController
  with I18nSupport {

  def master(path: String) = Action.async{ request =>
  val path = request.path
    path match {
      case x if x.endsWith("master") =>
        ws.url("http://localhost:8080").get().map{ response =>
         val contentBody = response.body.replaceAll("/static","/proxy/spark/static")
          Ok(views.html.master(play.twirl.api.Html(contentBody)))
        }

      case x if x.endsWith("css") =>
        val fileName = x.substring(x.indexOf("static"))
        ws.url(s"http://localhost:8080/${fileName}").get().map{ response =>
          Ok(response.body).as("text/css")
        }

      case x if x.endsWith("js") =>
        val fileName = x.substring(x.indexOf("static"))
        ws.url(s"http://localhost:8080/${fileName}").get().map{ response =>
          Ok(response.body).as("application/javascript")
        }

      case x if x.endsWith("png") =>
        val fileName = x.substring(x.indexOf("static"))
        ws.url(s"http://localhost:8080/${fileName}").get().map{ response =>
          Ok(response.bodyAsBytes).as("image/png")
        }

    }
//    ws.url("http://localhost:8080").get().map{ response =>
//      Ok(views.html.master(play.twirl.api.Html(response.body)))
//    }
  }

}
