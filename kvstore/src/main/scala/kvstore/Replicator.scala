package kvstore

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import scala.concurrent.duration._

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher
  
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]
  
  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }
  
  private case object Resend
  
  context.system.scheduler.schedule(0.1.seconds, 0.1.seconds, context.self, Resend)
  
  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case rep @ Replicate(key, valueOption, id) => {
      var seq = nextSeq
      acks += (seq -> (context.sender, rep))
      replica ! Snapshot(key, valueOption, seq)
    }
    case SnapshotAck(key, seq) => {
      acks.get(seq) match {
        case None => {}
        case Some((sender, Replicate(key, _, id))) => {
	      acks -= seq
	      sender ! Replicated(key, id)
        }
      }
    }
    case Resend => {
      acks foreach { a => { 
    	  var (seq: Long, (target: ActorRef, Replicate(key: String, valueOption: Option[String], _))) = a
    	  replica ! Snapshot(key, valueOption, seq)
        }
      }
    }
  }
}