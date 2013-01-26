package skiis

import java.util.concurrent.Executors

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

import scala.collection._
import scala.collection.mutable.ArrayBuffer

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class QueueSuite extends WordSpec with ShouldMatchers {
  import Skiis._

  def fizzBuzz(x: Int) = (x % 3, x % 5) match {
    case (0,0) => "FizzBuzz"
    case (0,_) => "Fizz"
    case (_,0) => "Buzz"
    case _ => x toString
  }

  def consumer[T](ts: Iterator[T], sleep: Int = 0) = new Runnable {
    val elements = new ArrayBuffer[T]()
    var started = false
    var completed = false
    override def run() = {
      synchronized { started = true }
      ts foreach { t => synchronized { elements += t; if (sleep > 0) Thread.sleep(sleep) } }
      synchronized { completed = true; notifyAll() }
    }
    def waitUntilCompleted() = synchronized { while (!completed) wait() }
  }

  "Skiis.Queue" should {
    implicit val context = Skiis.DefaultContext

    "push elements to consumers" in {
      val queue = new Skiis.Queue[Int](10)
      val fizz = queue parMap fizzBuzz

      // start the consumer on a separate thread
      val c = consumer(fizz.toIterator)
      new Thread(c).start()

      // push elements
      for (i <- 1 to 100) { queue += i }
      queue.close()

      c.waitUntilCompleted()
      c.synchronized {
        c.started should be === true
        c.elements.size should be === 100
        c.completed should be === true
      }
    }

    "block and resume producer when pushing elements to slow consumers" in {
      for (size <- 1 to 10) {
        val queue = new Skiis.Queue[Int](size)
        val fizz = queue parMap fizzBuzz

        // start the consumer on a separate thread
        val c = consumer(fizz.toIterator, sleep = 1)
        new Thread(c).start()

        // push elements
        for (i <- 1 to 100) { queue += i }
        queue.close()

        c.waitUntilCompleted()
        c.synchronized {
          c.started should be === true
          c.elements.size should be === 100
          c.completed should be === true
        }
      }
    }
  }
}