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
 * Created by bastien on 05/01/2015.
 */
object Main {
  val system = ActorSystem("WritersSystem")
  val allErrorsWriter = system.actorOf(WriterActor.props(new File("result.csv")), name = "allErrorsWriter")
  val error500Writer = system.actorOf(WriterActor.props(new File("500-errors.csv")), name = "error500Writer")
  val error404AnnuaireWriter = system.actorOf(WriterActor.props(new File("404-errors-lannuaire.csv")), name = "error404AnnuaireWriter")
  val error404VDDWriter = system.actorOf(WriterActor.props(new File("404-errors-vdd.csv")), name = "error404VDDWriter")
  val error404OthersWriter = system.actorOf(WriterActor.props(new File("404-errors-autres.csv")), name = "error404OthersWriter")
  val cyclicRedirectWriter = system.actorOf(WriterActor.props(new File("cyclic-redirect-errors.csv")), name = "cyclicRedirectWriter")

  val threadPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
  implicit val ec = ExecutionContext.fromExecutorService(threadPool)


  var running: Boolean = true

  def main(args: Array[String]) {

    val webCrawler: ModulableWebCrawler = initWebCrawler

    val startTime = System.currentTimeMillis()

    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode < 200 || crawledPage.errorCode >= 400).subscribe(crawledPage => writeError(crawledPage, allErrorsWriter))
    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode == 500).subscribe(crawledPage => writeError(crawledPage, error500Writer))
    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode == 404 && isAnnuairePage(crawledPage)).subscribe(crawledPage => writeError(crawledPage, error404AnnuaireWriter))
    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode == 404 && isVDDPage(crawledPage)).subscribe(crawledPage => writeError(crawledPage, error404VDDWriter))
    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode == 404 && !isAnnuairePage(crawledPage) && !isVDDPage(crawledPage)).subscribe(crawledPage => writeError(crawledPage, error404OthersWriter))
    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode == -2).subscribe(crawledPage => writeError(crawledPage, cyclicRedirectWriter))

    webCrawler.addObservable().count(crawledPage => true).doOnCompleted({
      val duration: Long = System.currentTimeMillis() - startTime
      println("CrawlingDone in: " + duration.toString + "ms")
      flushAndClose()
    }).foreach(nb => println("numberOfCrawledPages: " + nb))

    webCrawler.startCrawling(System.getProperty("startUrl"))
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

  def writeError(crawledPage: CrawledPage, writer: ActorRef): Unit = {
    writer ! MessageToWrite(crawledPage.errorCode + " , " + crawledPage.url + " , " + crawledPage.refererUrl + "\n")
  }

  def flushAndClose(): Unit = {
    implicit val timeout = Timeout(5 seconds)

    val sequence: Future[List[Any]] = Future.sequence(List(allErrorsWriter ? flushAndCloseMessage,
      error500Writer ? flushAndCloseMessage,
      error404AnnuaireWriter ? flushAndCloseMessage,
      error404VDDWriter ? flushAndCloseMessage,
      error404OthersWriter ? flushAndCloseMessage,
      cyclicRedirectWriter ? flushAndCloseMessage))
    Await.result(sequence, 5 seconds)
    system.shutdown()
    threadPool.shutdown()
  }
}
