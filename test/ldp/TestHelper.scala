package test.ldp

import java.util.concurrent._

import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent._

object TestHelper {

  def time[A](body: => A): A = {
    val start = System.currentTimeMillis
    val r = body
    val time = System.currentTimeMillis - start
    r
  }

}

trait TestHelper extends BeforeAndAfterAll { self: Suite =>

  val executorService: ExecutorService = Executors.newFixedThreadPool(2)

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executorService)

  override def afterAll(): Unit = {
    super.afterAll()
    executorService.shutdown()
  }

}
