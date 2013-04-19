package com.plotprojects.deepthought

import spray.can.server.SprayCanHttpServerApp
import akka.actor.Props


object Boot extends App with SprayCanHttpServerApp {

  // create and start our service actor
  val service = system.actorOf(Props[ServiceActor], "service")

  // create a new HttpServer using our handler tell it where to bind to
  newHttpServer(service) ! Bind(interface = "0.0.0.0", port = 80)

}
