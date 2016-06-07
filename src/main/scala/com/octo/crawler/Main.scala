package com.octo.crawler

import java.io.File
import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.octo.crawler.actors.{MessageToWrite, WriterActor, flushAndCloseMessage}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Created by Bastien Fiorentino on 05/01/2015.
 */
object Main {
  val system = ActorSystem("WritersSystem")

  val threadPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
  implicit val ec = ExecutionContext.fromExecutorService(threadPool)

  var running: Boolean = true

  def main(args: Array[String]) {

    val webCrawler: ModulableWebCrawler = initWebCrawler

    val startTime = System.currentTimeMillis()

    val writers = createErrorWriterOnPredicate(webCrawler, crawledPage => crawledPage.errorCode < 200 || crawledPage.errorCode >= 400, "result.csv") ::
      createErrorWriterOnPredicate(webCrawler, crawledPage => crawledPage.errorCode == 500, "500-errors.csv") ::
      createErrorWriterOnPredicate(webCrawler, crawledPage => crawledPage.errorCode == 404 && isAnnuairePage(crawledPage), "404-errors-lannuaire.csv") ::
      createErrorWriterOnPredicate(webCrawler, crawledPage => crawledPage.errorCode == 404 && isVDDPage(crawledPage), "404-errors-vdd.csv") ::
      createErrorWriterOnPredicate(webCrawler, crawledPage => crawledPage.errorCode == 404 && !isAnnuairePage(crawledPage) && !isVDDPage(crawledPage), "404-errors-autres.csv") ::
      createErrorWriterOnPredicate(webCrawler, crawledPage => crawledPage.errorCode == -2, "cyclic-redirect-errors.csv") :: Nil

    webCrawler.addObservable().count(crawledPage => true).doOnCompleted({
      val duration: Long = System.currentTimeMillis() - startTime
      println("CrawlingDone in: " + duration.toString + "ms")
      flushAndClose(writers)
    }).foreach(nb => println("numberOfCrawledPages: " + nb))

    webCrawler.startCrawling(System.getProperty("startUrl"))
  }

  private def createErrorWriterOnPredicate(webCrawler: ModulableWebCrawler, predicate: (CrawledPage) => Boolean, fileName: String): ActorRef = {
    val errorWriter = system.actorOf(WriterActor.props(new File(fileName)), name = fileName)
    webCrawler.addObservable().filter(predicate).subscribe(crawledPage => writeError(crawledPage, errorWriter))
    errorWriter
  }

  def writeError(crawledPage: CrawledPage, writer: ActorRef): Unit = {
    writer ! MessageToWrite(crawledPage.errorCode + " , " + crawledPage.url + " , " + crawledPage.refererUrl + "\n")
  }

  def initWebCrawler: ModulableWebCrawler = {
    def httpBasicAuthFormatter(httpBasicAuth: String): (String, String) = {
      if (httpBasicAuth == null || httpBasicAuth.isEmpty)
        ("", "")
      else {
        val basicAuthSplited: Array[String] = httpBasicAuth.split(":")
        (basicAuthSplited(0), basicAuthSplited(1))
      }
    }

    val httpBasicAuth: (String, String) = httpBasicAuthFormatter(System.getProperty("basicAuth"))

    val proxyPortString: String = System.getProperty("proxyPort")
    val proxyPort: Int = if (proxyPortString == null || proxyPortString.isEmpty) 0 else proxyPortString.toInt

    val webCrawler: ModulableWebCrawler = new ModulableWebCrawler(System.getProperty("hosts").split(",").toSet,
      System.getProperty("depth").toInt, System.getProperty("retryNumber").toInt, httpBasicAuth._1, httpBasicAuth._2,
      System.getProperty("proxyUrl"), proxyPort, threadPool, false)
    webCrawler
  }

  def isVDDPage(crawledPage: CrawledPage): Boolean = {
    crawledPage.url.contains("/vosdroits/")
  }

  def isAnnuairePage(crawledPage: CrawledPage): Boolean = {
    crawledPage.url.startsWith("https://lannuaire.")
  }

  def flushAndClose(writers: List[ActorRef]): Unit = {
    implicit val timeout = Timeout(5 seconds)

    val sequence: Future[List[Any]] = Future.sequence(writers.map(_ ? flushAndCloseMessage))
    Await.result(sequence, 5 seconds)
    system.shutdown()
    threadPool.shutdown()
  }
}
