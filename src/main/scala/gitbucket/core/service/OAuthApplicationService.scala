package gitbucket.core.service

import java.security.SecureRandom

import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.OAuthApplication

trait OAuthApplicationService {
  def makeRandomHexString(size: Int): String = {
    val bytes = new Array[Byte](size)
    OAuthApplicationService.secureRandom.nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  def getOAuthApplications()(implicit s: Session): List[OAuthApplication] = {
    OAuthApplications.sortBy(_.applicationId).list
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
}

object OAuthApplicationService extends OAuthApplicationService {
  private val secureRandom = new SecureRandom()
}
