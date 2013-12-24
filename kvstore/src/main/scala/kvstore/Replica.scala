package kvstore

import akka.actor.{ OneForOneStrategy, Props, ActorRef, Actor }
import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart
import scala.annotation.tailrec
import akka.pattern.{ ask, pipe }
import akka.actor.Terminated
import scala.concurrent.duration._
import akka.actor.PoisonPill
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.util.Timeout

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  
  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]
  
  val persister = context.actorOf(persistenceProps, "persister")
  
  private def add(key: String, value: String) = kv = kv + (key -> value)
  private def remove(key: String) = kv = kv - key
  
  private def get(key: String, id: Long) = GetResult(key, kv.get(key), id)
  
  private case class Timeout(id: Long)
  private case object Resend
  val resendTask = context.system.scheduler.schedule(0.1.seconds, 0.1.seconds, context.self, Resend)
  arbiter ! Join
  
  def receive = {
    case JoinedPrimary   => context.become(leader(Map.empty, Map.empty))
    case JoinedSecondary => context.become(replica(Map.empty[Long, (ActorRef, String, Option[String])]))
  }
  
  type Persists = Map[Long, (ActorRef, String, Option[String])]
  type Replicates = Map[Long, (ActorRef, Set[ActorRef])]
  
  def leader(persists: Persists, replicates: Replicates): Receive = {
    case Replicas(replicas) => {
      val currentReplicas = secondaries.keySet
      val addedReplicas = replicas -- currentReplicas - context.self
      val removedReplicas = currentReplicas -- replicas
      val replicatorsToRemove = removedReplicas map secondaries
      
      replicators --= replicatorsToRemove
      secondaries --= removedReplicas
      
      replicatorsToRemove foreach { _ ! PoisonPill }
      
      addedReplicas.foreach(r => {
        val replicator = context.actorOf(Props(new Replicator(r)))
        replicators += replicator
        secondaries += (r -> replicator)
        kv foreach { case (k, v) => replicator ! Replicate(k, Some(v), 42) }
      })
      
      val filteredReplicates = replicates map { 
        case (id, (sender, targets)) => (id, (sender, replicators -- targets))
      }
      
      filteredReplicates foreach { 
        case (id, (sender, targets)) => if (targets.isEmpty) sender ! OperationAck(id)
      }
      
      context.become(leader(persists, filteredReplicates filter { 
        case (_, (_, targets)) => !targets.isEmpty 
      }))
    }
    case Insert(key, value, id) => { 
      add(key, value)
      
      persister ! Persist(key, Some(value), id)
      replicators foreach { _ ! Replicate(key, Some(value), id) }
      
      context.system.scheduler.scheduleOnce(1.second, context.self, Timeout(id))
      
      context.become(
          leader(
              persists + (id -> (context.sender, key, Some(value))),
              if (replicators.isEmpty) replicates else replicates + (id -> (context.sender, replicators))))
    }
    case Remove(key, id) => { 
      remove(key)
      
      persister ! Persist(key, None, id)
      replicators foreach { _ ! Replicate(key, None, id) }
      
      context.system.scheduler.scheduleOnce(1.second, context.self, Timeout(id))
      
      context.become(
          leader(
              persists + (id -> (context.sender, key, None)),
              if (replicators.isEmpty) replicates else replicates + (id -> (context.sender, replicators))))
    }
    case Get(key, id) => context.sender ! get(key, id)
    case Persisted(_, id) => persists.get(id) match {
        case Some((sender, _, _)) => {
          if (!replicates.contains(id))
            sender ! OperationAck(id)
          context.become(leader(persists - id, replicates))
        }
        case None => {}
    }
    case Timeout(id) => (persists.get(id), replicates.get(id)) match {
      case (Some((sender, _, _)), _) => {
        sender ! OperationFailed(id)
        context.become(leader(persists - id, replicates - id))
      }
      case (_, Some((sender, _))) => {
    	sender ! OperationFailed(id)
        context.become(leader(persists - id, replicates - id))
      }
      case (None, None) => {}
    }
    case Resend => persists.foreach(p => {
      val (id, (_, key, valueOption)) = p
      persister ! Persist(key, valueOption, id)
    })
    case Replicated(key, id) => replicates.get(id) match {
      case Some((sender, targets)) => {
        val filteredTargets = targets - context.sender
        if (filteredTargets.isEmpty && !persists.contains(id))
          sender ! OperationAck(id)
        if (filteredTargets.isEmpty)
          context.become(leader(persists, replicates - id))
        else
          context.become(leader(persists, replicates + (id -> (sender, filteredTargets))))
      }
      case None => {}
    }
  }
  
  var currSeq = 0L
  
  private def ack(key: String, seq: Long) = {
    currSeq = Math.max(seq + 1, currSeq)
    SnapshotAck(key, seq)
  }
  
  def replica(persists: Map[Long, (ActorRef, String, Option[String])]): Receive = {
    case Snapshot(key, valueOption, seq) => { 
      if (seq < currSeq)
    	  context.sender ! ack(key, seq)
    	else if (seq > currSeq) {}
    	else {
    	  valueOption match {
    	  	case Some(v) => add(key, v)
    	  	case None => remove(key) 
    	  }
    	  persister ! Persist(key, valueOption, seq)
    	  context.become(replica(persists + (seq -> (context.sender, key, valueOption)))) 
       }
    }
    case Get(key, id) => context.sender ! get(key, id)
    case Persisted(key, seq) => persists.get(seq) match {
        case Some((sender, _, _)) => {
          sender ! ack(key, seq)
          context.become(replica(persists - seq))
        }
        case None => {}
    }
    case Resend => persists.foreach(p => {
      val (seq, (_, key, valueOption)) = p
      persister ! Persist(key, valueOption, seq)
    })
  }
}
