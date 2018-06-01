package controllers

import Connection.DataChannel
import javax.inject._
import actors._
import akka.NotUsed
import akka.actor._
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.Timeout
import models.SDP
import play.api.Logger
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * This class creates the actions and the websocket needed.
  */
@Singleton
class HomeController @Inject() (cc: ControllerComponents)
                    (implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer)
  extends AbstractController(cc) with SameOriginCheck {

  val logger = play.api.Logger(getClass)

  def lander = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def websocket: WebSocket = WebSocket.accept[JsValue, JsValue] { _ =>
    ActorFlow.actorRef { out =>
      WebSocketSignallingActor.props(out)
    }
    //    Flow[JsValue].map { msg =>
    //      logger.info(msg.toString())
    //      if((msg \ "type").as[String] == "offer") {
    //        logger.info("Received an offer")
    //        val dc: DataChannel = new DataChannel((msg \ "sdp").as[JsValue])
    //        val answer = dc.answer
    //        logger.info("Sending back answer: " + answer.toString)
    //        val answerJson = Json.toJson(answer.toString)
    //        logger.info("result: "+answerJson)
    //        val returnJson: JsObject = msg.as[JsObject] + ("answer" -> answerJson)
    //
    //        ec.execute(dc)
    //
    //        returnJson
    //      } else {
    //        val timestamp: Long = System.currentTimeMillis
    //        val returnJson: JsObject = msg.as[JsObject] + ("time_received" -> Json.toJson(timestamp))
    //
    //        returnJson
    //      }
    //    }
  }
}

trait SameOriginCheck {

  def logger: Logger

  /**
    * Checks that the WebSocket comes from the same origin.  This is necessary to protect
    * against Cross-Site WebSocket Hijacking as WebSocket does not implement Same Origin Policy.
    *
    * See https://tools.ietf.org/html/rfc6455#section-1.3 and
    * http://blog.dewhurstsecurity.com/2013/08/30/security-testing-html5-websockets.html
    */
  def sameOriginCheck(rh: RequestHeader): Boolean = {
    rh.headers.get("Origin") match {
      case Some(originValue) if originMatches(originValue) =>
        logger.debug(s"originCheck: originValue = $originValue")
        true

      case Some(badOrigin) =>
        logger.error(s"originCheck: rejecting request because Origin header value ${badOrigin} is not in the same origin")
        false

      case None =>
        logger.error("originCheck: rejecting request because no Origin header found")
        false
    }
  }

  /**
    * Returns true if the value of the Origin header contains an acceptable value.
    *
    * This is probably better done through configuration same as the allowedhosts filter.
    */
  def originMatches(origin: String): Boolean = {
    origin.contains("localhost:9000") || origin.contains("localhost:19001")
  }

}