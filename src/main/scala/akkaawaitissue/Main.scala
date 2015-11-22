package akkaawaitissue

import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.{Promise, Await}

class BlockedActor extends Actor {

  val log = LoggerFactory.getLogger( getClass )

 
  def receive: Receive = {
    case "hello" =>
      val s = sender
      Thread.sleep(10 * 60 * 1000)
      s ! "hi"

    case other =>
      log.info(s"unknown message -- [ $other ]")
  }
}


class BlockingActor extends Actor {

  val log = LoggerFactory.getLogger( getClass )
  val blocked = context.system.actorOf(Main.blockedProps)
  //val neverEndingPromise = Promise[String]()
  //val neverEndingFuture = neverEndingPromise.future

  def receive: Receive = {
    case "block" =>
      val s = sender
      log.info(s"going to block forever...")
      implicit val timeout = Timeout(10.hours)
      val blockedFuture = blocked ? "hello"
      Await.result(blockedFuture, Duration.Inf)
      log.error(s"and how is that???")
      s ! "unblocked"

    case other =>
      log.info(s"unknown message -- [ $other ]")
  }
}

class EchoActor extends Actor {

  val log = LoggerFactory.getLogger( getClass )

  def receive: Receive = {
    case msg =>
      log.info(s"received -- [ $msg ]")
  }
}

class Router extends Actor {

  val log = LoggerFactory.getLogger( getClass )
  val echor = context.system.actorOf(Main.echoProps)
  var blockers = Set.empty[ActorRef]

  def receive: Receive = {
    case "new" =>
      log.info(s"adding another blocker (current total: ${blockers.size})...")
      val blocker = context.system.actorOf(Main.blockingProps)
      blocker ! "block"
      blockers = blockers + blocker

    case "show" =>
      log.info(s"current blockers (total: ${blockers.size}) -- [ $blockers ]")

    case "unblocked" =>
      blockers = blockers - sender

    case other =>
      echor forward other
  }

}

object Main {

  val echoProps = Props( new EchoActor )
  val blockingProps = Props( new BlockingActor )
  val blockedProps = Props( new BlockedActor )
  val routerProps = Props( new Router )

  val system = ActorSystem.create("blockers")

  val router = system.actorOf( routerProps )

  def main(args: Array[String]):Unit = {
    for(_ <- 1 to 1000) yield {
      router ! "new"
    }
  }
}
