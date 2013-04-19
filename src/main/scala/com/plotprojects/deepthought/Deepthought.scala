package com.plotprojects.deepthought

import akka.actor.{Props, ActorRef, Actor}
import akka.pattern.ask
import spray.routing._
import authentication._
import authentication.UserPass
import spray.http._
import collection.mutable
import scala.concurrent.duration._
import concurrent.Future
import scala.Tuple2
import scala.Some


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ServiceActor extends Actor with DeepthoughtService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

  override val deepthoughtActor = context.actorOf(Props[DeepthoughtActor], "deepthought")
}


// this trait defines our service behavior independently from the service actor
abstract trait DeepthoughtService extends HttpService {

  val deepthoughtActor: ActorRef

  val authenticator:  UserPassAuthenticator[String] = (up: Option[UserPass]) => Future.successful(
    up.map(_.user)
  )

  val myRoute =
    authenticate(BasicAuth[String](authenticator, "Deepthought beta")) { username =>
      path("stats") {
        get {
          complete {
            deepthoughtActor.ask(Statistics(username))(60 seconds).mapTo[Tuple2[Long, Long]].map(t =>
              t._1 + " searches and " + t._2 + " teaches"
            )
          }
        }
      } ~
      path("learn") {
        put {
          formFields('query, 'answer) { (question, answer) =>
            complete {
              deepthoughtActor.ask(Learn(username, question, answer))(60 seconds).map(_ => "Success")
            }
          }
        }
      } ~
      path("search") {
        post {
          formField('query) { query =>
            complete {
              deepthoughtActor.ask(Search(username, query))(60 seconds).mapTo[String]
            }
          }
        }
      } ~
      path("resetstats") {
        post {
          complete {
            deepthoughtActor.ask(ResetStatistics(username))(60 seconds).map(_ => "Success")
          }
        }
      }
    }
}

case class ResetStatistics(user: String)
case class Statistics(user: String)
case class Search(user: String, query: String)
case class Learn(user: String, query: String, answer: String)

class DeepthoughtActor extends Actor {

  val queries = mutable.Map.empty[String, Long]
  val tought  = mutable.Map.empty[String, Long]
  val answers = mutable.Map.empty[String, String]

  answers.put("life universe everything", "42")
  answers.put("recursion", "recursion")

  def receive = {
    case Statistics(user: String) => sender ! (queries.get(user).getOrElse(0L), tought.get(user).getOrElse(0L))
    case Search(user: String, query: String) => {
      queries.put(user, queries.get(user).getOrElse(0L) + 1)
      sender ! answers.get(query).getOrElse("I'm sorry Dave, I'm afraid I don't know that.")
    }
    case Learn(user, query, answer) => {
      answers.put(query, answer)
      tought.put(user, tought.get(user).getOrElse(0L) + 1)
      sender ! Unit
    }
    case ResetStatistics(user) => {
      queries.put(user, 0L)
      tought.put(user, 0L)
      sender ! Unit
    }
  }
}
