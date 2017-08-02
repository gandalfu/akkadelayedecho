package com.example

import akka.actor.{ Actor, ActorSystem, Props, ActorLogging }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.StdIn

object EchoServer {

  case class Bid(userId: String, offer: Int)

  case object GetBids

  case class Bids(bids: List[Bid])

  class Mirror extends Actor with ActorLogging {
    val r = scala.util.Random

    def receive = {
      case request: String => {
        import scala.concurrent.ExecutionContext.Implicits.global

        val delay = 1000 + r.nextInt(6000)
        context.system.scheduler.scheduleOnce(delay milliseconds, sender(), request)
      }
      case _ => log.info("Invalid message")
    }
  }

  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val echoServer = system.actorOf(Props[Mirror], "mirror")

    val route =
      path("") {
        post {
          extractRequest { rq =>
            implicit val timeout: Timeout = 8.seconds
            // query the actor for the current auction state
            val response: Future[String] = (echoServer ? rq.entity.dataBytes.toString).mapTo[String]
            complete(response)

          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}