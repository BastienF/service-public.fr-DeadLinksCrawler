name := "service-public.fr-DeadLinksCrawler"

version := "1.0"

scalaVersion := "2.11.4"

mainClass in Compile := Some("com.octo.crawler.Main")

lazy val scalaCrawlerVersion = "929f973"
lazy val root = Project("root", file(".")) dependsOn ScalaCrawlerModule
lazy val ScalaCrawlerModule = RootProject(uri("https://github.com/BastienF/ScalaCrawler.git#" + scalaCrawlerVersion))
