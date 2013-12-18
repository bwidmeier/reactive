/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))
  
  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = { 
    case Insert(requester, id, elem) => root ! Insert(requester, id, elem)
    case Contains(requester, id, elem) => root ! Contains(requester, id, elem)
    case Remove(requester, id, elem) => root ! Remove(requester, id, elem)
    case GC => ???
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = ???

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal
 
  
  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = { 
    case Insert(requester, id, e) => handleInsert(requester, id, e)
    case Contains(requester, id, e) => handleContains(requester, id, e)
    case Remove(requester, id, e) => handleRemove(requester, id, e)
    case CopyTo(node) => ???
    }

  def handleInsert(requester: ActorRef, id: Int, e: Int): Unit = {
    if (e == elem) {
      removed = false
      requester ! OperationFinished(id)
    }
    else if (e < elem)
      subtrees.get(Left) match {
      	case None => {
      	  subtrees += (Left -> context.actorOf(props(e, false)))
      	  requester ! OperationFinished(id)
      	}
      	case Some(a) => a ! Insert(requester, id, e)
      }
    else
      subtrees.get(Right) match {
      	case None => {
	      subtrees += (Right -> context.actorOf(props(e, false)))
	      requester ! OperationFinished(id)
	    }
	    case Some(a) => a ! Insert(requester, id, e)
      }
  }
  
  def handleContains(requester: ActorRef, id: Int, e: Int): Unit = {
    if (e == elem)
      requester ! ContainsResult(id, !removed)
    else if (e < elem)
      subtrees.get(Left) match {
      	case None => requester ! ContainsResult(id, false)
      	case Some(a) => a ! Contains(requester, id, e)
      }
    else
      subtrees.get(Right) match {
      	case None => requester ! ContainsResult(id, false)
      	case Some(a) => a ! Contains(requester, id, e)
      }
  }
  
  def handleRemove(requester: ActorRef, id: Int, e: Int): Unit = {
    if (e == elem) {
      removed = true
      requester ! OperationFinished(id)
    }
    else if (e < elem)
      subtrees.get(Left) match {
      	case None => OperationFinished(id)
      	case Some(a) => a ! Remove(requester, id, e)
      }
    else
      subtrees.get(Right) match {
      	case None => OperationFinished(id)
      	case Some(a) => a ! Remove(requester, id, e)
      }
  }
  
  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

}