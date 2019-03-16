package gitbucket.core.model

trait OAuthAccessTokenComponent { self: Profile =>
  import profile.api._

  lazy val OAuthAccessTokens = TableQuery[OAuthAccessTokens]

  class OAuthAccessTokens(tag: Tag) extends Table[OAuthAccessToken](tag, "OAUTH_ACCESS_TOKEN") {
    val oauthAccessTokenId = column[Int]("OAUTH_ACCESS_TOKEN_ID", O AutoInc)
    val userName = column[String]("USER_NAME")
    val oauthTokenHash = column[String]("OAUTH_TOKEN_HASH")
    val appId = column[Int]("OAUTH_APP_ID")
    def * = (oauthAccessTokenId, userName, oauthTokenHash, appId) <> (OAuthAccessToken.tupled, OAuthAccessToken.unapply)
  }
}
case class OAuthAccessToken(
  oauthAccessTokenId: Int = 0,
  userName: String,
  oauthTokenHash: String,
  appId: Int
)
