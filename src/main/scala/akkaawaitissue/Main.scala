package akkaawaitissue

import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.{Promise, Await}

class HeavyResourceProducerActor extends Actor {

  val log = LoggerFactory.getLogger( getClass )

  def receive: Receive = {
    case "produce" =>
      val s = sender
      Thread.sleep(10 * 60 * 1000)
      s ! "done"

    case other =>
      log.info(s"unknown message -- [ $other ]")
  }
}


class HeavyResourceAwaitingActor extends Actor {

  implicit val timeout = Timeout(10.hours)
  val log = LoggerFactory.getLogger( getClass )
  val blocked = context.system.actorOf(Main.heavyResourceProps)

  def receive: Receive = {
    case "consume" =>
      val s = sender
      log.info(s"going to block...")
      val blockedFuture = blocked ? "produce"
      Await.result(blockedFuture, Duration.Inf)
      log.info(s"awaiting completed")
      s ! "consumed"

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
      val blocker = context.system.actorOf(Main.awaitingProps)
      blocker ! "consume"
      blockers = blockers + blocker
      log.info(s"added (current total: ${blockers.size})...")

    case "show" =>
      log.info(s"current blockers (total: ${blockers.size}) -- [ $blockers ]")

    case "consumed" =>
      blockers = blockers - sender

    case other =>
      echor forward other
  }

}

object Main {

  val echoProps = Props( new EchoActor )
  val awaitingProps = Props( new HeavyResourceAwaitingActor )
  val heavyResourceProps = Props( new HeavyResourceProducerActor )
  val routerProps = Props( new Router )

  val system = ActorSystem.create("blockers-demo")

  val router = system.actorOf( routerProps )

}
