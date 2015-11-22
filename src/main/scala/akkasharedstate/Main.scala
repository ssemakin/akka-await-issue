package akkasharedstate

import akka.actor.{ActorSystem, Props, ActorRef, Actor}
import org.slf4j.LoggerFactory

import scala.util.Random

case class SharedMutable(var a00: Long,
                         var a01: Long,
                         var a02: Long,
                         var a03: Long,
                         var a04: Long,
                         var a05: Long,
                         var a06: Long,
                         var a07: Long,
                         var a08: Long,
                         var a09: Long) {
  def toSet = Set(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09)
  def toList = List(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09)
  def toImmutable: SharedImmutable = {
    SharedImmutable( a00, a01, a02, a03, a04, a05, a06, a07, a08, a09 )
  }

}

case class SharedImmutable(a00: Long,
                           a01: Long,
                           a02: Long,
                           a03: Long,
                           a04: Long,
                           a05: Long,
                           a06: Long,
                           a07: Long,
                           a08: Long,
                           a09: Long) {
  def toSet = Set(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09)
  def toList = List(a00, a01, a02, a03, a04, a05, a06, a07, a08, a09)
}

class ActorWithSharedState( writersCount: Int, checker: ActorRef ) extends Actor {

  val log = LoggerFactory.getLogger(getClass)

  val random = new Random( System.currentTimeMillis() % 1000 )
  val state = SharedMutable(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  var writers = Set.empty[ActorRef]

  def receive: Receive = {
    case "start" =>
      writers = (for { i <- 1 to writersCount } yield context.system.actorOf(Main.writerProps( checker ))).toSet
      self ! "write"

    case "write" =>
      for { writer <- writers } {
        writer ! Main.Update( random.nextLong(), state, writers )
        Thread.sleep( random.nextInt( 100 ) )
      }
//      self ! "start"

    case other =>
      log.info(s"echo -- $other")
  }

}

class WritingActor(checker: ActorRef) extends Actor {

  val log = LoggerFactory.getLogger(getClass)
  val random = new Random( System.currentTimeMillis() % 10 )

  def receive: Receive = {
    case Main.Update( n, s, writers ) =>
//      for { i <- 1 to 1000 } {
        s.a00 = n
        s.a01 = n
        s.a02 = n
        s.a03 = n
        s.a04 = n
        s.a05 = n
        s.a06 = n
        s.a07 = n
        s.a08 = n
        s.a09 = n
        checker ! Main.CheckIntegrity(s.toImmutable)
        checker ! Main.CheckContent(n, s.toImmutable)
//      }
      writers.toIndexedSeq(random.nextInt(writers.size)) ! Main.Update( random.nextLong(), s, writers )

    case other =>
      log.warn(s"unknown message received")
  }

}

class CheckingActor extends Actor {

  val log = LoggerFactory.getLogger(getClass)

  def receive: Receive = {
    case Main.CheckContent( n, s ) =>
      if( !s.toList.forall(x => x == n) ) {
        log.error(s"all must be [ $n ], actual --\n [ $s ]\n [ ${s.toList} ]")
        System.exit( -1 )
      }

    case Main.CheckIntegrity( s ) =>
      if( s.toSet.size != 1 ) {
        log.error(s"all must be the same, actual --\n [ $s ]\n [ ${s.toList} ]")
        System.exit( -1 )
      }

  }
}


object Main {

  case class Update(n: Long, s: SharedMutable, writers: Set[ActorRef])
  case class CheckContent(n: Long, s: SharedImmutable)
  case class CheckIntegrity(s: SharedImmutable)

  val checkerProps = Props( new CheckingActor )
  def writerProps(checker: ActorRef) = Props( new WritingActor(checker) )
  def statefulProps(writersCount: Int, checker: ActorRef) = Props( new ActorWithSharedState(writersCount, checker) )

  val system = ActorSystem.create("stateful-demo")
  val checker = system.actorOf( checkerProps )
  val stateful = system.actorOf( statefulProps( 100, checker ) )

}
