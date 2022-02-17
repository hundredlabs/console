package web.auth

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.{BaseGitHubProvider, GitHubProfileParser, GitHubProvider}
import com.mohiva.play.silhouette.impl.providers.oauth2.GitHubProvider._
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Base GitHub OAuth2 Provider.
  *
  * @see https://developer.github.com/v3/oauth/
  */
trait BaseGitHubProvider extends OAuth2Provider {

  /**
    * The content type to parse a profile from.
    */
  override type Content = JsValue

  /**
    * The provider ID.
    */
  override val id = ID

  /**
    * Defines the URLs that are needed to retrieve the profile data.
    */
  override protected val urls = Map("api" -> settings.apiURL.getOrElse(API))

  /**
    * A list with headers to send to the API.
    *
    * Without defining the accept header, the response will take the following form:
    * access_token=e72e16c7e42f292c6912e7710c838347ae178b4a&scope=user%2Cgist&token_type=bearer
    *
    * @see https://developer.github.com/v3/oauth/#response
    */
  override protected val headers = Seq(HeaderNames.ACCEPT -> "application/json")

  /**
    * Builds the social profile.
    *
    * @param authInfo The auth info received from the provider.
    * @return On success the build social profile, otherwise a failure.
    */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHttpHeaders(HeaderNames.AUTHORIZATION -> s"Bearer ${authInfo.accessToken}").get()
      .flatMap { response =>
        val json = response.json
        (json \ "message").asOpt[String] match {
          case Some(msg) =>
            val docURL = (json \ "documentation_url").asOpt[String]
            throw new ProfileRetrievalException(SpecifiedProfileError.format(id, msg, docURL))
        case _ =>
         profileParser.parse(json, authInfo)

          }

      }
  }
}

case class GithubProfileEmail(email: String, primary: Boolean, verified: Boolean)

/**
  * The profile parser for the common social profile.
  */
class GitHubProfileParser(httpLayer: HTTPLayer) extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  implicit val ec: ExecutionContext = httpLayer.executionContext
  implicit val emailJsonFmt = Json.format[GithubProfileEmail]

  private def getDefaultSocialProfile(json: JsValue) = Future.successful {
    val userID = (json \ "id").as[Long]
    val fullName = (json \ "name").asOpt[String]
    val avatarUrl = (json \ "avatar_url").asOpt[String]
    val email = (json \ "email").asOpt[String].filter(!_.isEmpty)


    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID.toString),
      fullName = fullName,
      avatarURL = avatarUrl,
      email = email)
  }
  /**
    * Parses the social profile.
    *
    * @param json     The content returned from the provider.
    * @param authInfo The auth info to query the provider again for additional data.
    * @return The social profile from given result.
    */
  override def parse(json: JsValue, authInfo: OAuth2Info) = {
    for {
     profile <- getDefaultSocialProfile(json)
     withEmail <- if(profile.email.isEmpty){
       httpLayer.url(GithubOAuthProvider.EMAIL_API)
         .withHttpHeaders(HeaderNames.AUTHORIZATION -> s"Bearer ${authInfo.accessToken}")
         .get()
         .map{ emailResponse =>

           val emails = emailResponse.json.as[Seq[GithubProfileEmail]]
           val primaryEmail = emails.find(_.primary).map(_.email)
           val profileWithEmail = profile.copy(email = primaryEmail)
           profileWithEmail
         }
     } else Future.successful(profile)
    } yield withEmail
  }
}

/**
  * The GitHub OAuth2 Provider.
  *
  * @param httpLayer     The HTTP layer implementation.
  * @param stateHandler  The state provider implementation.
  * @param settings      The provider settings.
  */
class GithubOAuthProvider(
                      protected val httpLayer: HTTPLayer,
                      protected val stateHandler: SocialStateHandler,
                      val settings: OAuth2Settings)
  extends BaseGitHubProvider with CommonSocialProfileBuilder {

  /**
    * The type of this class.
    */
  override type Self = GitHubProvider

  /**
    * The profile parser implementation.
    */
  override val profileParser = new GitHubProfileParser(httpLayer)

  /**
    * Gets a provider initialized with a new settings object.
    *
    * @param f A function which gets the settings passed and returns different settings.
    * @return An instance of the provider initialized with new settings.
    */
  override def withSettings(f: (Settings) => Settings) = new GitHubProvider(httpLayer, stateHandler, f(settings))
}

/**
  * The companion object.
  */
object GithubOAuthProvider {

  /**
    * The error messages.
    */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s, doc URL: %s"

  /**
    * The GitHub constants.
    */
  val ID = "github"
  val API = "https://api.github.com/user"
  val EMAIL_API = "https://api.github.com/user/emails"
}
