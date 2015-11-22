package akkaawaitissue

import akka.actor.{ActorRef, ActorSystem, Props, Actor}
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.Await

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


class HeavyResourceAwaitingActor(sharedResource: ActorRef) extends Actor {

  implicit val timeout = Timeout(10.hours)
  val log = LoggerFactory.getLogger( getClass )

  def receive: Receive = {
    case "consume" =>
      val s = sender
      log.info(s"going to block...")
      val blockedFuture = sharedResource ? "produce"
      Await.result(blockedFuture, Duration.Inf)
      log.info(s"awaiting completed")
      s ! "consumed"

    case other =>
      log.info(s"unknown message -- [ $other ]")
  }
}


class HeavyResourceNonAwaitingActor(sharedResource: ActorRef) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(10.hours)
  val log = LoggerFactory.getLogger( getClass )

  def receive: Receive = {
    case "consume" =>
      val s = sender
      log.info(s"not going to block...")
      val blockedFuture = sharedResource ? "produce"
      blockedFuture.foreach {
        case "done" =>
          log.info(s"non-awaiting completed")
          s ! "non-waiting-consumed"
      }

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
  val sharedResource = context.system.actorOf(Main.heavyResourceProps)
  var blockers = Set.empty[ActorRef]
  var nonBlockers = Set.empty[ActorRef]

  def receive: Receive = {
    case "new" =>
      log.info(s"adding blocker (current total: ${blockers.size})...")
      val blocker = context.system.actorOf(Main.awaitingProps(sharedResource))
      blocker ! "consume"
      blockers = blockers + blocker
      log.info(s"added (current total: ${blockers.size})...")

    case "new-no-await" =>
      log.info(s"adding non-blocker (current total: ${nonBlockers.size})...")
      val nonBlocker = context.system.actorOf(Main.nonAwaitingProps(sharedResource))
      nonBlocker ! "consume"
      nonBlockers += nonBlocker
      log.info(s"added (current total: ${nonBlockers.size})...")

    case "show" =>
      log.info(s"current blockers (total: ${blockers.size}) -- [ $blockers ]")

    case "consumed" =>
      blockers -= sender

    case "non-waiting-consumed" =>
      nonBlockers -= sender

    case other =>
      echor forward other
  }

}


object Main {

  val echoProps = Props( new EchoActor )
  val heavyResourceProps = Props( new HeavyResourceProducerActor )
  def awaitingProps(resource: ActorRef) = Props( new HeavyResourceAwaitingActor( resource ) )
  def nonAwaitingProps(resource: ActorRef) = Props( new HeavyResourceNonAwaitingActor( resource ) )

  val routerProps = Props( new Router )

  val system = ActorSystem.create("blockers-demo")

  val router = system.actorOf( routerProps )

}
