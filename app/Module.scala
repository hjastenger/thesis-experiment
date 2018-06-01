import com.google.inject.AbstractModule
import org.jitsi.sctp4j.Sctp

class Module extends AbstractModule {

  override def configure(): Unit = {
    Sctp.init()
  }
}
