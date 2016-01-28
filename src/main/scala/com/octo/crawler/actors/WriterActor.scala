package com.octo.crawler.actors

import java.io.{File, PrintWriter}

import akka.actor.{Actor, Props}

/**
 * Created by bastien on 05/01/2015.
 */
class WriterActor(val file: File) extends Actor {
  val writer = new PrintWriter(file)
  val buffer = new StringBuilder();

  override def receive = {
    case MessageToWrite(message) => buffer.append(message)
    case flushAndCloseMessage => {
      writer.write(buffer.toArray)
      writer.flush()
      writer.close()
      sender ! true
    }
  }
}

object WriterActor {
  def props(file: File): Props = Props(new WriterActor(file))
}
case object flushAndCloseMessage

case class MessageToWrite(message: String)