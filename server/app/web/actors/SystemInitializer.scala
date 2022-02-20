package web.actors

import java.io.IOException

import akka.actor.{Actor, ActorLogging, PoisonPill}
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.Inject
import web.actors.SystemInitializer.InitSystem
import web.models.{BetaSignupRequest, CredentialInfo, Member}
import web.services.{AuthTokenService, AuthenticateService, DefaultKeyPairGenerator, MemberService, SecretStore}
import scalikejdbc._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class SystemInitializer @Inject()(memberService: MemberService,
                                  secretStore: SecretStore,
                                  authenticateService: AuthenticateService,
                                  authTokenService: AuthTokenService,
                                  passwordHasherRegistry: PasswordHasherRegistry) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case InitSystem =>

      try {
        DB readOnly { implicit session =>
          val tbls = sql"""SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name"""
            .map(_.string("table_name"))
            .list()
            .apply()


          if(tbls.size < 10){
            throw new IOException("Database not yet initialized...")
          }


          secretStore.init()
          val newMember = BetaSignupRequest("admin", "admin", new DefaultKeyPairGenerator().getRandomString(5), false)
          val loginInfo = LoginInfo(CredentialsProvider.ID, newMember.email)
          val authInfo = passwordHasherRegistry.current.hash(newMember.password)
          val credInfo = CredentialInfo(newMember.email, authInfo.hasher, authInfo.password, authInfo.salt)
          val member = Member(newMember)
          val result = for {
            hasMember <- memberService.findByEmail("admin")
            _ <- hasMember match {
              case None => memberService
                .save(member, secretStore).flatMap(id => {
                authenticateService
                  .addAuthenticateMethod(id, loginInfo, credInfo)
                  .flatMap(_ => authTokenService.create(id))
              })
              case Some(value) => Future.successful(value.id.getOrElse(0L))
            }
          } yield hasMember

          result.onComplete {
            case Failure(exception) => exception.printStackTrace()
            case Success(optMember) =>
              println("> Welcome to Gigahex! \uD83D\uDC4B")
              if(optMember.isEmpty){
                println(s"> username: ${newMember.email}, password: ${newMember.password}")
              }
              println("> Visit http://localhost:9080 to get started!")

            self ! PoisonPill
          }
        }
      } catch {
        case e: Exception =>
         context.system.scheduler.scheduleOnce(500.millis, self, InitSystem)
        //init
      }

  }

}

object SystemInitializer  {
  case object InitSystem
}