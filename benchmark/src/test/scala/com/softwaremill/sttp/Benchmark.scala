package com.softwaremill.sttp

import com.google.common.net.HttpHeaders.CONTENT_TYPE
import com.google.common.net.MediaType
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import org.mockserver.integration.{ClientAndProxy, ClientAndServer}
import org.scalameter.api._
import org.mockserver.integration.ClientAndProxy.startClientAndProxy
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.Header.header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Benchmark extends Bench.LocalTime {
  val Port = 8080
  val Host = "localhost"
  val Protocol = "http"

  var mockServer: ClientAndServer = _
  var proxy: ClientAndProxy = _

  val numReq = Gen.range("numReq")(100, 300, 100)

  def stubResponse(path: String, body: String) = {
    mockServer.when(request()
      .withMethod("GET")
      .withPath(path)
    ).respond(response()
      .withStatusCode(200)
      .withHeaders(
        header(CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString)
      ).withBody(body)
    )
  }

  def beforeAfter[T](using: Using[T]): Benchmark.Using[T] = {
    using beforeTests {
      mockServer = startClientAndServer(Port)
      proxy = startClientAndProxy(Port + 10)
      stubResponse("/", "response")
    } afterTests {
      proxy.stop()
      mockServer.stop()
    }
  }

  performance of "sttp" in {
    measure method "HttpURLConnectionBackend" in {
      beforeAfter(using(numReq)) in { x =>
        implicit val backend = HttpURLConnectionBackend()
        val request = sttp.get(uri"http://localhost:$Port")
        (1 to x).foreach { _ =>
          request.send().unsafeBody
        }
      }
    }

    measure method "AkkaHttpBackend" in {
      beforeAfter(using(numReq)) in { x =>
        import scala.concurrent.ExecutionContext.Implicits.global
        implicit val backend = AkkaHttpBackend()
        val request = sttp.get(uri"http://localhost:$Port")
        val responses = (1 to x).map { _ =>
          request.send().map(_.unsafeBody)
        }
        Await.result(Future.sequence(responses), 60.seconds)
      }
    }
  }
}
