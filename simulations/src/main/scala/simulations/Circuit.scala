package simulations

import common._

class Wire {
  private var sigVal = false
  private var actions: List[Simulator#Action] = List()

  def getSignal: Boolean = sigVal
  
  def setSignal(s: Boolean) {
    if (s != sigVal) {
      sigVal = s
      actions.foreach(action => action())
    }
  }

  def addAction(a: Simulator#Action) {
    actions = a :: actions
    a()
  }
}

abstract class CircuitSimulator extends Simulator {

  val InverterDelay: Int
  val AndGateDelay: Int
  val OrGateDelay: Int

  def probe(name: String, wire: Wire) {
    wire addAction {
      () => afterDelay(0) {
        println(
          "  " + currentTime + ": " + name + " -> " +  wire.getSignal)
      }
    }
  }

  def inverter(input: Wire, output: Wire) {
    def invertAction() {
      val inputSig = input.getSignal
      afterDelay(InverterDelay) { output.setSignal(!inputSig) }
    }
    input addAction invertAction
  }

  def andGate(a1: Wire, a2: Wire, output: Wire) {
    def andAction() 
    {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(AndGateDelay) { output.setSignal(a1Sig & a2Sig) }
    }
    
    a1 addAction andAction
    a2 addAction andAction
  }

  //
  // to complete with orGates and demux...
  //

  def orGate(a1: Wire, a2: Wire, output: Wire) {
    def orAction() 
    {
      val a1sig = a1.getSignal
      val a2sig = a2.getSignal
      afterDelay(AndGateDelay) { output.setSignal(a1sig || a2sig) }
    }
    
    a1 addAction orAction
    a2 addAction orAction
  }
  
  def orGate2(a1: Wire, a2: Wire, output: Wire) {
    val b1 = new Wire
    inverter(a1, b1)
    
    val b2 = new Wire
    inverter(a2, b2)
    
    val preOutput = new Wire
    andGate(b1, b2, preOutput)
    
    inverter(preOutput, output)
  }

  def demux(in: Wire, c: List[Wire], out: List[Wire]) {
	  ???
//    def andChainBuilder(controls: List[Wire], wireNum: Int): Wire = {
//      controls match {
//        case c :: Nil => c
//        case c1 :: c2 :: Nil => 
//        case c :: cs => {
//          val controlLen = controls.length
//          val wire = new Wire
//          
//          wire
//        } 
//      }
//    }
//    
//    def aux(in: Wire, c: List[Wire], wireNum: Int): Wire = {
//      c match {
//        case Nil => in
//        case _ => andChainBuilder(c, wireNum)
//      }
//    }
//    
//    out match {
//      case Nil => Unit
//      case o :: os => {
//    	andGate(in, aux(in, c, out.length - 1), o)
//        demux(in, c, os)
//      }
//    }
	}
}

object Circuit extends CircuitSimulator {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  def andGateExample {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    probe("in1", in1)
    probe("in2", in2)
    probe("out", out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    in1.setSignal(true)
    run

    in2.setSignal(true)
    run
  }

  //
  // to complete with orGateExample and demuxExample...
  //
}

object CircuitMain extends App {
  // You can write tests either here, or better in the test class CircuitSuite.
  Circuit.andGateExample
}
