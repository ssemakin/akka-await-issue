package akkaawaitissue

import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import org.slf4j.LoggerFactory
import scala.concurrent.duration.Duration
import scala.concurrent.{Promise, Await}

class BlockingActor extends Actor {

  val log = LoggerFactory.getLogger( getClass )

  val neverEndingPromise = Promise[String]()
  val neverEndingFuture = neverEndingPromise.future

  def receive: Receive = {
    case "block" =>
      val s = sender
      log.info(s"going to block forever...")
      Await.result(neverEndingFuture, Duration.Inf)
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
  val routerProps = Props( new Router )

  val system = ActorSystem.create("blockers")

  val router = system.actorOf( routerProps )

}
