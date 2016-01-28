package com.octo.crawler

import java.util.concurrent.{ExecutorService, Executors}

/**
 * Created by bastien on 05/01/2015.
 */
object Main {

  var running: Boolean = true

  def main(args: Array[String]) {
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
    val threadPool: ExecutorService = Executors.newFixedThreadPool(10)
    val webCrawler: ModulableWebCrawler = new ModulableWebCrawler(System.getProperty("hosts").split(",").toSet,
      System.getProperty("depth").toInt, System.getProperty("retryNumber").toInt, httpBasicAuth._1, httpBasicAuth._2,
      System.getProperty("proxyUrl"), proxyPort, threadPool, false)

    val startTime = System.currentTimeMillis()

    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode < 200 || crawledPage.errorCode >= 400).subscribe(crawledPage => handleCrawledPage(crawledPage))

    webCrawler.addObservable().count(crawledPage => true).doOnCompleted({
      threadPool.shutdown()
      val duration: Long = System.currentTimeMillis() - startTime
      println("CrawlingDone in: " + duration.toString + "ms")

    }).foreach(nb => println("numberOfCrawledPages: " + nb))

    webCrawler.startCrawling(System.getProperty("startUrl"))
  }

  def handleCrawledPage(crawledPage: CrawledPage): Unit = {
    println(crawledPage.errorCode + ": " + crawledPage.url + " , " + crawledPage.refererUrl)
  }
}
