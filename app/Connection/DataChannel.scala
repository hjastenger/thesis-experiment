package Connection

import java.io.{IOException, UnsupportedEncodingException}
import java.lang.Thread.sleep
import java.math.BigInteger
import java.net._
import java.nio.ByteBuffer
import java.security.{MessageDigest, SecureRandom}
import java.util
import java.util.{Arrays, Random}

import org.jitsi.sctp4j._
import com.bitbreeds.webrtc.common.SignalUtil
import com.bitbreeds.webrtc.dtls.{DtlsMuxStunTransport, KeyStoreInfo, WebrtcDtlsServer}
import play.api.libs.json.JsValue
import models.SDP
import com.bitbreeds.webrtc.peerconnection.IceCandidate
import com.bitbreeds.webrtc.peerconnection.UserData
import com.bitbreeds.webrtc.stun.BindingService
import javax.sdp.SessionDescription
import org.apache.commons.codec.binary.Hex
import org.bouncycastle.crypto.tls.{DTLSServerProtocol, DTLSTransport}
import org.jitsi.service.neomedia.RawPacket
import play.Play
import play.api.libs.json._

class DataChannel(offer: JsValue) extends Runnable with SctpDataCallback with SctpSocket.NotificationListener {
  val logger = play.api.Logger(getClass)
  val localAddress: String = sys.env.getOrElse("ip_address", InetAddress.getLocalHost.getHostAddress)

  var running: Boolean = true
  var ordered: Boolean = true

  private val STUN_BINDING = 0
  private val DTLS_HANDSHAKE = 1
  private val SCTP = 2

  var mode: Int = STUN_BINDING

  private val DEFAULT_WAIT_MILLIS = 60000
  private val DEFAULT_MTU = 1500
  private val DEFAULT_BUFFER_SIZE = 20000

  logger.info("Initializing DataChannel")
  var UDPSocket: DatagramSocket = new DatagramSocket()
  this.UDPSocket.setReceiveBufferSize(2000000)
  this.UDPSocket.setSendBufferSize(2000000)
  var transport: DTLSTransport = null
  var sctpSocket: SctpSocket = Sctp.createSocket(5000)

  logger.info("UDP socket started: Port("+this.UDPSocket.getLocalPort+"), Address:("+ this.localAddress+")")

  val random: Random = new Random
  val number: Int = random.nextInt(1000000)
  val iceCandidate: IceCandidate = new IceCandidate(BigInteger.valueOf(number), this.UDPSocket.getLocalPort, localAddress, 2122252543L)
  logger.info("Created ICE candidate: "+ iceCandidate.toString)

  val userData: UserData = createLocalUser
  val spd = new SDP(offer, userData)
  val answer: SessionDescription = spd.createAnswer(iceCandidate)
  var sender: SocketAddress = null
  private val bindingService = new BindingService
  val keyStoreInfo: KeyStoreInfo = new KeyStoreInfo(
    Play.application().classloader().getResource("ws2.jks").getPath,
    "websocket",
    "websocket")

  val dtlsServer = new WebrtcDtlsServer(keyStoreInfo)
  val serverProtocol = new DTLSServerProtocol(new SecureRandom)

  /**
    * Message type used to acknowledge WebRTC data channel allocation on SCTP
    * stream ID on which <tt>MSG_OPEN_CHANNEL</tt> message arrives.
    */
  private val MSG_CHANNEL_ACK = 0x2

  private val MSG_CHANNEL_ACK_BYTES: Array[Byte] = Array(MSG_CHANNEL_ACK.toByte)

  /**
    * Message with this type sent over control PPID in order to open new WebRTC
    * data channel on SCTP stream ID that this message is sent.
    */
  private val MSG_OPEN_CHANNEL = 0x3

  /**
    * Payload protocol id that identifies binary data in WebRTC data channel.
    */
  private val WEB_RTC_PPID_BIN = 53

  /**
    * Payload protocol id for control data. Used for <tt>WebRtcDataStream</tt>
    * allocation.
    */
  private val WEB_RTC_PPID_CTRL = 50

  /**
    * Payload protocol id that identifies text data UTF8 encoded in WebRTC data
    * channels.
    */
  private val WEB_RTC_PPID_STRING = 51

  def onSctpNotification(socket: SctpSocket, notification: SctpNotification) {
    logger.info("onSctpNotification")
    if(notification.sn_type == SctpNotification.SCTP_STREAM_RESET_EVENT) {
      this.running = false
      this.sctpSocket.close()
      this.transport.close()
      this.UDPSocket.close()
      logger.info("Received SctpNotification: " + notification.toString)
    } else {
      logger.info(notification.toString)
    }
  }

  @Override
  override def onSctpPacket(data: Array[Byte], sid: Int, ssn: Int, tsn: Int, ppid: Long, context: Int, flags: Int): Unit = {
    logger.info("DataChannel with SSID: " + sid)
    val buffer = ByteBuffer.wrap(data)

    /* 1 byte unsigned integer */
    val messageType = 0xFF & buffer.get
    val reliability = 0xFF & buffer.get(1)

    if (messageType == MSG_OPEN_CHANNEL) {
      /* 2 bytes unsigned integer */
      val protocolLength = 0xFFFF & buffer.getShort

      logger.info("Received 'MSG_OPEN_CHANNEL', transmitting 'MSG_CHANNEL_ACK")
      if(reliability == 0x00) {
        logger.info("DATA_CHANNEL_RELIABLE (0x00)")
      } else if(reliability == 0x80) {
        this.ordered = false
        logger.info("DATA_CHANNEL_RELIABLE_UNORDERED (0x80)")
      } else if(reliability == 0x01) {
        logger.info("DATA_CHANNEL_PARTIAL_RELIABLE_REXMIT (0x01)")
      } else if(reliability == 0x81) {
        this.ordered = false
        logger.info("DATA_CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED (0x81)")
      } else if(reliability == 0x02) {
        logger.info("DATA_CHANNEL_PARTIAL_RELIABLE_TIMED (0x02)")
      } else if(reliability == 0x82) {
//        Currently Chrome is not adhering to the standard supplied by the WebRTC standard
//        adding maxPacketLifeTime to the MSG_OPEN_CHANNEL won't result in the appropriate
//        byte being added to the packge. Safari is also emiting different behavour. No
//        notification is in place to inform the browser of the ice gathering state. Complete
//        will therefor never fire, while this same behaviour does exist in chrome and firefox.
        this.ordered = false
        logger.info("DATA_CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED (0x82)")
      }
      logger.info("Reliability parameter: " + reliability)
      val ack = MSG_CHANNEL_ACK_BYTES
      try {
        sctpSocket.accept()
        if (sctpSocket.send(ack, true, sid, WEB_RTC_PPID_CTRL) != ack.length) logger.error("Failed to send open channel confirmation")
      }
        catch {
        case e: IOException =>
          System.out.println("ioexception my man")
      }
    }
    else if (ppid == WEB_RTC_PPID_STRING || ppid == WEB_RTC_PPID_BIN) { //            WebRtcDataStream channel;
      if (ppid == WEB_RTC_PPID_STRING) {
        var str = ""
        val charsetName = "UTF-8"
        try {
          str = new String(data, charsetName)
          val json: JsValue = Json.parse(str)
          val timestamp: Long = System.currentTimeMillis
//          val metadata = (json \ "metadata").as[JsObject] + ("time_acquired" -> Json.toJson(timestamp))
//          val metadata = ((json \ "metadata").get)
//          logger.info("Metadata:")
          //          val something = json.as[JsObject] + ("metadata" -> metadata)

          val returnJson: JsObject = json.as[JsObject] + ("time_acquired" -> Json.toJson(timestamp))
          val result: Array[Byte] = returnJson.toString().getBytes("UTF-8")
          logger.info("sid of: " + sid)
          logger.info("PPID: " + ppid)
          sctpSocket.send(result, 0, result.length, this.ordered, sid, ppid.toInt, 0)
        } catch {
          case ex: IOException =>
            if (ex.isInstanceOf[UnsupportedEncodingException]) logger.error("Unsupported charset encoding/name " + charsetName, ex)
            else logger.error("caught something while responding to the datachannel message: " + ex)
            str = null
        }
        logger.info("PPID of" + ppid)
        logger.info("received the following string: " + str)
      }
    }
    else {
      logger.error("Unexpected ctrl msg type: " + messageType)
      logger.warn("Got message on unsupported PPID: " + ppid)
    }
  }

  override def run(): Unit = {
    val bt = new Array[Byte](DEFAULT_BUFFER_SIZE)

    logger.info("DataChannel running")
    while (this.running) {
      if (this.mode == STUN_BINDING) {
        logger.info("Connection mode is: 'STUN_BINDING'")
        logger.info("Listening for binding on: " + this.UDPSocket.getLocalSocketAddress + " - " + this.UDPSocket.getPort)
        sleep(5) //No reason to hammer on this


        val packet = new DatagramPacket(bt, 0, bt.length)
        this.UDPSocket.receive(packet)
        val currentSender = packet.getSocketAddress
        sender = currentSender
        logger.info("Current sender: " + currentSender.toString)

        val data = util.Arrays.copyOf(packet.getData, packet.getLength)
        logger.info("Received data: " + Hex.encodeHexString(data) + " on " + UDPSocket.getLocalSocketAddress + " - " + UDPSocket.getPort)

        val out = bindingService.processBindingRequest(data, userData.getUserName, userData.getPassword, currentSender.asInstanceOf[InetSocketAddress])

        val outData = ByteBuffer.wrap(out)
        logger.info("Sending: " + Hex.encodeHexString(outData.array) + " to " + currentSender)

        val pc = new DatagramPacket(out, 0, out.length)
        pc.setSocketAddress(sender)
        UDPSocket.send(pc)
        this.mode = DTLS_HANDSHAKE

      } else if (this.mode == DTLS_HANDSHAKE) {
        logger.info("Connection mode is: 'DTLS_HANDSHAKE'")

        UDPSocket.connect(sender)
        logger.info("Connecting DTLS mux")
        val muxStunTransport = new DtlsMuxStunTransport(userData, UDPSocket, DEFAULT_MTU)
        this.transport = serverProtocol.accept(dtlsServer, muxStunTransport)

        sctpSocket.setLink(new NetworkLink() {
          override def onConnOut(s: SctpSocket, packet: Array[Byte]): Unit = {
            transport.send(packet, 0, packet.length)
          }
        })

        sctpSocket.listen()
        sctpSocket.setDataCallback(this)
        sctpSocket.setNotificationListener(this)

        this.mode = SCTP
      } else if (this.mode == SCTP) {
        logger.info("Connection mode is: 'SCTP'")

        val buf = new Array[Byte](transport.getReceiveLimit)
        val length = transport.receive(buf, 0, buf.length, DEFAULT_WAIT_MILLIS)

        if (length >= 0) {
          val handled = util.Arrays.copyOf(buf, length)
          val send = Array(new RawPacket(handled, 0, handled.length))
          for (s <- send) {
            if (s != null) sctpSocket.onConnIn(s.getBuffer, s.getOffset, s.getLength)
          }
        }
      }
    }
  }

  private def createLocalUser = {
    val myUser = Hex.encodeHexString(SignalUtil.randomBytes(4))
    val myPass = Hex.encodeHexString(SignalUtil.randomBytes(16))
    new UserData(myUser, myPass)
  }
}
