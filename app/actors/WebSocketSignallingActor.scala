package actors

import Connection.DataChannel
import akka.actor._
import akka.dispatch.MessageDispatcher
import play.api.libs.json.{JsObject, JsValue, Json}

object WebSocketSignallingActor {
  def props(out: ActorRef) = Props(new WebSocketSignallingActor(out))
}

class WebSocketSignallingActor(out: ActorRef) extends Actor with ActorLogging {
  log.info("actor created!")

  implicit val system: ActorSystem = ActorSystem()
  implicit val executionContext: MessageDispatcher = system.dispatchers.lookup("my-dispatcher")

  override def receive: Receive = {
    case as: JsValue =>
      val dc: DataChannel = new DataChannel((as \ "sdp").as[JsValue])
      val answer = dc.answer
      val answerJson = Json.toJson(answer.toString)
      val returnJson: JsObject = as.as[JsObject] + ("answer" -> answerJson)

      executionContext.execute(dc)
      out ! returnJson
    case _ =>
      log.error("WebSocketSignallingActor receive: did not match anything")
  }

  override def postStop(): Unit = {
    super.postStop()
  }
}
