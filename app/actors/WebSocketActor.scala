package actors

import akka.actor._
import play.api.libs.json.{JsObject, JsValue, Json}

object WebSocketActor {
  def props(out: ActorRef) = Props(new WebSocketActor(out))
}

class WebSocketActor(out: ActorRef) extends Actor with ActorLogging {
  log.info("actor created!")

  override def receive: Receive = {
    case as: JsValue =>
      val timestamp: Long = System.currentTimeMillis
      val returnJson: JsObject = as.as[JsObject] + ("time_received" -> Json.toJson(timestamp))

      out ! returnJson
    case _ =>
      log.error("received value did not match anything")
  }

  override def postStop(): Unit = {
    super.postStop()
  }
}
