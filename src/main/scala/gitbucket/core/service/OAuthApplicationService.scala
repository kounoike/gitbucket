package gitbucket.core.service

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.{Account, OAuthAccessToken, OAuthApplication}
import gitbucket.core.util.StringUtil

trait OAuthApplicationService {
  private def makeRandomHexString(size: Int): String = {
    val bytes = new Array[Byte](size)
    OAuthApplicationService.secureRandom.nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  def getOAuthApplications()(implicit s: Session): List[OAuthApplication] = {
    OAuthApplications.sortBy(_.applicationId).list
  }

  def getOAuthApplication(clientId: String)(implicit s: Session): Option[OAuthApplication] = {
    OAuthApplications.filter(_.clientId === clientId.bind).firstOption
  }

  def getOAuthApplication(clientId: String, clientSecret: String)(implicit s: Session): Option[OAuthApplication] = {
    OAuthApplications.filter(t => t.clientId === clientId.bind && t.clientSecret === clientSecret.bind).firstOption
  }

  def insertOAuthApplication(
    applicationName: String,
    description: Option[String],
    homepageUrl: String,
    callbackUrl: String
  )(implicit s: Session): Unit = {
    val clientId = makeRandomHexString(10)
    val clientSecret = makeRandomHexString(20)
    OAuthApplications insert OAuthApplication(
      applicationName = applicationName,
      description = description,
      homepageUrl = homepageUrl,
      callbackUrl = callbackUrl,
      clientId = clientId,
      clientSecret = clientSecret
    )
  }

  private def tokenToHash(token: String): String = StringUtil.sha1(token)

  /**
   * @return (TokenId, Token)
   */
  def generateOAuthAccessToken(userName: String, app: OAuthApplication)(implicit s: Session): String = {
    var token: String = null
    var hash: String = null

    do {
      token = makeRandomHexString(20)
      hash = tokenToHash(token)
    } while (OAuthAccessTokens.filter(_.oauthTokenHash === hash.bind).exists.run)

    val newToken = OAuthAccessToken(userName = userName, appId = app.applicationId, oauthTokenHash = hash)
    val tokenId = (OAuthAccessTokens returning OAuthAccessTokens.map(_.oauthAccessTokenId)) insert newToken

    token
  }

  def generateCode(userName: String, state: String, app: OAuthApplication): String = {
    val code = makeRandomHexString(10)
    OAuthApplicationService.sessionMap.put(code, OAuthSession(userName, state, app))
    code
  }

  def getOAuthSession(code: String): Option[OAuthSession] = {
    Option(OAuthApplicationService.sessionMap.get(code))
  }

  def deleteOAuthSession(code: String): Unit = {
    OAuthApplicationService.sessionMap.remove(code)
  }

  def getAccountByOAuthAccessToken(token: String)(implicit s: Session): Option[Account] =
    Accounts
      .join(OAuthAccessTokens)
      .filter {
        case (ac, t) =>
          (ac.userName === t.userName) && (t.oauthTokenHash === tokenToHash(token).bind) && (ac.removed === false.bind)
      }
      .map { case (ac, t) => ac }
      .firstOption
}

case class OAuthSession(
  userName: String,
  state: String,
  app: OAuthApplication
)

object OAuthApplicationService extends OAuthApplicationService {
  private val secureRandom = new SecureRandom()

  private val sessionMap: ConcurrentHashMap[String, OAuthSession] = new ConcurrentHashMap[String, OAuthSession]()
}
