package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  property("min2LowThenHigh") = forAll {a : Int =>
    val h = insert(4, insert(3, empty))
    findMin(h) == 3
  }
  
  property("min2HighThenLow") = forAll {a : Int =>
    val h = insert(3, insert(4, empty))
    findMin(h) == 3
  }
  
  property("empty is empty") = forAll {a: Int =>
    isEmpty(empty)
  }
  
  property("removing last makes empty") = forAll {a: Int =>
    isEmpty(deleteMin(insert(a, empty)))
  }
  
  property("HLH sandwich") = forAll {a: Int =>
  	val h = insert(4, insert(3, insert(4, empty)))
  	findMin(h) == 3
  }
  
  property("LHL sandwich") = forAll {a: Int =>
  	val h = insert(3, insert(4, insert(3, empty)))
  	findMin(h) == 3
  }
  
  property("3 ascending") = forAll {a: Int =>
  	val h = insert(4, insert(3, insert(2, empty)))
  	findMin(h) == 2
  }
  
  property("3 descending") = forAll {a: Int =>
  	val h = insert(4, insert(5, insert(6, empty)))
  	findMin(h) == 4
  }
  
  property("left empty meld") = forAll {a: Int =>
  	val h = meld(empty, insert(-2, empty))
  	findMin(h) == -2
  }
  
  property("right empty meld") = forAll {a: Int =>
  	val h = meld(insert(-2, empty), empty)
  	findMin(h) == -2
  }
  
  property("right lower meld") = forAll {a: Int =>
  	val h = meld(insert(-2, empty), insert(-3, empty))
  	findMin(h) == -3
  }
  
  property("left lower meld") = forAll {a: Int =>
  	val h = meld(insert(-2, empty), insert(1, empty))
  	findMin(h) == -2
  }
  
  property("HLM delete all") = forAll {a: Int =>
  	val h = insert(4, insert(3, insert(5, empty)))
  	findMin(h) == 3 && findMin(deleteMin(h)) == 4
  }
  
  lazy val genHeap: Gen[H] = ???

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
