package models

import java.security.MessageDigest

import com.bitbreeds.webrtc.dtls.CertUtil
import com.bitbreeds.webrtc.peerconnection.{IceCandidate, PeerDescription, UserData}
import com.bitbreeds.webrtc.signaling.SDPUtil
import javax.sdp.{MediaDescription, SdpFactory, SessionDescription}
import play.Play
import play.api.libs.json.JsValue

/** ICE Lite implementation requirements: https://tools.ietf.org/html/rfc5245#page-24
  *
  * @param offer
  */

class SDP(offer: JsValue, userData: UserData) {



  val logger = play.api.Logger(getClass)
  val factory: SdpFactory = SdpFactory.getInstance()
  val fingerPrint: String = CertUtil.getCertFingerPrint(
    Play.application().classloader().getResource("ws2.jks").getPath,
    "websocket",
    "websocket")

//  val fingerPrint = "sha-256 C9:E2:48:09:47:C8:CC:B3:51:A8:A1:C5:AA:63:51:26:50:1D:FF:76:AE:EF:CB:31:0C:E7:41:21:5A:11:FA:D5"

  logger.info("Initializing offer.")

  logger.info("Creating SDP using javax.sdp.factory")
  val sdp: SessionDescription = factory.createSessionDescription(offer.as[String])
  val mdp: MediaDescription = sdp.getMediaDescriptions(true).get(0).asInstanceOf[MediaDescription]

  val pwd: String = mdp.getAttribute("ice-pwd")
  val user: String = mdp.getAttribute("ice-ufrag")
  val mid: String = mdp.getAttribute("mid")

  val remotePeer: PeerDescription = new PeerDescription(new UserData(user, pwd), mid, sdp)



  def createAnswer(candidate: IceCandidate): SessionDescription = {
    SDPUtil.createSDP(
      candidate, this.userData.getUserName, this.userData.getPassword, this.fingerPrint, this.mid
    )
  }
}
