name := "ScalaCrawlerModuleDemo"

version := "1.0"

scalaVersion := "2.11.4"

mainClass in Compile := Some("com.octo.crawler.Main")

lazy val scalaCrawlerVersion = "f86d76a"
lazy val root = Project("root", file(".")) dependsOn ScalaCrawlerModule
lazy val ScalaCrawlerModule = RootProject(uri("https://github.com/BastienF/ScalaCrawler.git#" + scalaCrawlerVersion))