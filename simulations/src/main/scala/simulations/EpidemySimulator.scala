package simulations

import math.random
import java.util.Dictionary
import scala.language.postfixOps

class EpidemySimulator extends Simulator {

	def rand(min: Int, max: Int) = (random * (max - min)).toInt + min
	def randomBelow(i: Int) = rand(0, i)
  

  protected[simulations] object SimConfig {
    val population: Int = 300
    val roomRows: Int = 8
    val roomColumns: Int = 8
    val maxDaysBetweenMove = 5
    val transmissionRate = 40
    val prevalenceRate = 1
    val timeToBecomeSick = 6
    val timeToRollForDeath = 14
    val deathChance = 25
    val timeToImmunity = 16
    val timeToReturnToHealth = 18
  }

  import SimConfig._

  val persons: List[Person] = 
    (0 until population) map { 
		i => new Person(i, i % 100 == 0)
    } toList

  def doesEventOccur(rate: Int) = {
    randomBelow(100) < rate
  }
  
  class Person (val id: Int, startInfected: Boolean) {
    var infected = false
    var sick = false
    var immune = false
    var dead = false

    var row: Int = randomBelow(roomRows)
    var col: Int = randomBelow(roomColumns)
    var daysToNextMove = rand(1, maxDaysBetweenMove)
    
    def addMod(a: Int, b: Int, mod: Int) = {
      val rawModResult = (a + b) % mod
      if (rawModResult >= 0) rawModResult else rawModResult + mod
    }
    
    def getNeighboringSpaces = {
      List((addMod(row, -1, roomRows), col),
          (addMod(row, 1, roomRows), col),
          (row, addMod(col, -1, roomColumns)),
          (row, addMod(col, 1, roomColumns)))
    }
    
    def takeRandom[T](source: List[T]): Option[T] = {
      source match {
        case Nil => None
        case _ => Some(source(randomBelow(source.length)))
      }
    }
    
    def isInfectionPresent(r: Int, c: Int): Boolean = {
      persons exists { p => p.infected && p.row == r && p.col == c }
    }
    
    def infect = {
      infected = true
      afterDelay(timeToBecomeSick){ sick = true }
      afterDelay(timeToRollForDeath) { dead = dead || doesEventOccur(deathChance) }
      afterDelay(timeToImmunity) { 
        if (!dead) {
          immune = true
          sick = false
        }
      }
      afterDelay(timeToReturnToHealth) {
        if (!dead){
          infected = false
          immune = false
        }
      }
    }
    
    def move(r: Int, c: Int) = {
      row = r
      col = c
      
      if (!infected && !immune && !dead && !sick &&
          doesEventOccur(transmissionRate) && isInfectionPresent(r, c))
        infect
    }
    
    def tickAction: Unit = {
      daysToNextMove = daysToNextMove - 1;
      if (daysToNextMove == 0 && !dead) {
        daysToNextMove = rand(1, maxDaysBetweenMove)
        val possibleSpaces = getNeighboringSpaces.filter { 
          case (r, c) => persons.forall { 
            p => p.row != r || p.col != c || !p.sick
          } 
        }
        takeRandom(possibleSpaces) match {
          case None => {}
          case Some((r, c)) => move(r, c)
        }
      }
      if (!dead)
        afterDelay(1) (tickAction)
    }
    
    if (startInfected)
      infect
    
    afterDelay(1) (tickAction)
  }
}