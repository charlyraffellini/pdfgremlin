package example

import org.python.util.PythonInterpreter

object PyTest extends App {

  import java.io.FileOutputStream
  import io.github.cloudify.scala.spdf._
  import java.io._

  val code = scala.io.Source.fromFile("/Users/carlos.raffellini/src/pdfgremlin/src/main/scala/example/Hello.scala").mkString

  val interpreter = new PythonInterpreter()

  // Set a variable with the content you want to work with
  interpreter.set("code", code)

  // Simple use Pygments as you would in Python
//  interpreter.exec("from pygments import highlight\n" + "from pygments.lexers import PythonLexer\n" + "from pygments.formatters import HtmlFormatter\n" + "\nresult = highlight(code, PythonLexer(), HtmlFormatter())")
  interpreter.exec(
    """
      |from pygments.styles import get_all_styles
      |styles = list(get_all_styles())
      |result = styles
      |""".stripMargin)

  // Get the result that has been set in a variable
  val highlightedHtml = interpreter.get("result", classOf[Array[String]])
  System.out.println(highlightedHtml.mkString("\n"))
}




object ToDelete {
  object TrustAllSslCertificates {

    import scala.util.DynamicVariable

    import java.net.{InetAddress, Socket}
    import java.security.cert.X509Certificate
    import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLContext, SSLSession, SSLSocketFactory, TrustManager, X509TrustManager}

    def trustAllSslCertificatesIn[A](f: ⇒ A): A = {
      val allHostsValid: HostnameVerifier = (hostname: String, session: SSLSession) => true

      val trustAllSslSocketFactory: SSLSocketFactory = {
        val sc: SSLContext = SSLContext.getInstance("TLS")
        sc.init(null, Array[TrustManager](new X509TrustManager {
          def getAcceptedIssuers: Array[X509Certificate] = {
            null
          }

          def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = {}

          def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
        }), new java.security.SecureRandom)

        sc.getSocketFactory
      }

      DynamicHostnameVerifier.withCustomVerifier(allHostsValid) {
        DynamicSSLSocketFactory.withCustomFactory(trustAllSslSocketFactory) {
          f
        }
      }
    }

    //JVm -> GlobalSSLSocketFactory -> dyn var -> jvm default
    // dyn var .withValue(my thing) {
    // Jvm -> GlobalSSLSocketFactory -> dyn var -> my thing -> wahey
    //}
    // other threads
    // JVm -> GlobalSSLSocketFactory -> dyn var -> jvm default

    object DynamicSSLSocketFactory {
      def withCustomFactory[A](factory: SSLSocketFactory)(f: ⇒ A): A =
        dynamicSSLSocketFactory.withValue(factory)(f)

      private val dynamicSSLSocketFactory: DynamicVariable[SSLSocketFactory] =
        new DynamicVariable[SSLSocketFactory](HttpsURLConnection.getDefaultSSLSocketFactory)

      HttpsURLConnection.setDefaultSSLSocketFactory(new SSLSocketFactory {
        def getDefaultCipherSuites: Array[String] =
          dynamicSSLSocketFactory.value.getDefaultCipherSuites

        def getSupportedCipherSuites: Array[String] =
          dynamicSSLSocketFactory.value.getSupportedCipherSuites

        def createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
          dynamicSSLSocketFactory.value.createSocket(s, host, port, autoClose)

        def createSocket(host: String, port: Int): Socket =
          dynamicSSLSocketFactory.value.createSocket(host, port)

        def createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
          dynamicSSLSocketFactory.value.createSocket(host, port, localHost, localPort)

        def createSocket(host: InetAddress, port: Int): Socket =
          dynamicSSLSocketFactory.value.createSocket(host, port)

        def createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
          dynamicSSLSocketFactory.value.createSocket(address, port, localAddress, localPort)
      })
    }

    object DynamicHostnameVerifier {
      def withCustomVerifier[A](verifier: HostnameVerifier)(f: ⇒ A): A = {
        dynamicHostNameVerifier.withValue(verifier)(f)
      }

      private val dynamicHostNameVerifier: DynamicVariable[HostnameVerifier] =
        new DynamicVariable[HostnameVerifier](HttpsURLConnection.getDefaultHostnameVerifier)

      HttpsURLConnection.setDefaultHostnameVerifier((hostname: String, session: SSLSession) => {
        dynamicHostNameVerifier.value.verify(hostname, session)
      })
    }
  }



}