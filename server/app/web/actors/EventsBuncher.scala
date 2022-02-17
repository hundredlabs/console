package web.actors

import akka.actor.{ActorRef, LoggingFSM, Props}
import com.gigahex.commons.events.{ApplicationStarted, SparkEvents}
import web.actors.EventsBuncher.Enqueue

import scala.concurrent.duration._

sealed trait State
case object Idle   extends State
case object Active extends State

sealed trait Data
case object NotReady                             extends Data
case class BatchEvents(events: Seq[SparkEvents]) extends Data

class EventsBuncher(metricsMachine: ActorRef) extends LoggingFSM[State, Data] {

  startWith(Idle, NotReady)

  when(Idle) {
    case Event(Enqueue(e), NotReady) if e.isInstanceOf[ApplicationStarted] =>
      metricsMachine ! e
      stay.using(BatchEvents(events = Vector.empty))
  }

  onTransition {
    case Active -> Idle =>
      stateData match {
        case BatchEvents(events) =>
          log.info(s"Flushing ${events.size} events")
          metricsMachine ! BatchEvents(events.sortWith{
            case (l,r) => l.time < r.time
          })
        case _ => log.info(s"transition from active to idle - ${stateData}")

      }
  }

  when(Active, stateTimeout = 1.second) {
    case Event(StateTimeout, eq: BatchEvents) => goto(Idle).using(eq.copy(events = Vector.empty))
  }

  whenUnhandled {
    case Event(Enqueue(e), b @ BatchEvents(evs)) =>
      goto(Active).using(b.copy(events = evs :+ e))

    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

}

object EventsBuncher {
  def props(metricsMachine: ActorRef): Props = Props(new EventsBuncher(metricsMachine))
  case class Enqueue(e: SparkEvents)
}
