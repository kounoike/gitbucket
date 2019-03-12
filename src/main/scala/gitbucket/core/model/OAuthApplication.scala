package gitbucket.core.model

trait OAuthApplicationComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  lazy val OAuthApplications = TableQuery[OAuthApplications]

  class OAuthApplications(tag: Tag) extends Table[OAuthApplication](tag, "OAUTH_APPLICATION") {
    val applicationId = column[Int]("APP_ID", O AutoInc)
    val applicationName = column[String]("APP_NAME")
    val description = column[String]("APP_DESCRIPTION")
    val homepageUrl = column[String]("HOMEPAGE_URL")
    val callbackUrl = column[String]("CALLBACK_URL")
    val clientId = column[String]("CLIENT_ID")
    val clientSecret = column[String]("CLIENT_SECRET")
    def * =
      (applicationId, applicationName, description.?, homepageUrl, callbackUrl, clientId, clientSecret) <> (OAuthApplication.tupled, OAuthApplication.unapply)

  }
}

case class OAuthApplication(
  applicationId: Int = 0,
  applicationName: String,
  description: Option[String],
  homepageUrl: String,
  callbackUrl: String,
  clientId: String,
  clientSecret: String
)
