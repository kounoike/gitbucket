package gitbucket.core.plugin

import org.json4s._
import gitbucket.core.util.Directory._
import org.apache.commons.io.FileUtils

object PluginRepository {
  implicit val formats = DefaultFormats

  def parsePluginJson(json: String): Seq[PluginMetadata] = {
    org.json4s.jackson.JsonMethods.parse(json).extract[Seq[PluginMetadata]]
  }

  val IndexFileName = "plugins.json"

  lazy val LocalRepositoryDir = new java.io.File(PluginHome, ".repository")
  lazy val LocalRepositoryIndexFile = new java.io.File(LocalRepositoryDir, IndexFileName)

  lazy val OnlineRepositoryDir = new java.io.File(PluginHome, ".onlinerepository")
  lazy val OnlineRepositoryIndexFile = new java.io.File(OnlineRepositoryDir, "plugins.json")

  val OnlineRepositoryUrlBase = "https://github.com/kounoike/gitbucket-plugin-farm-test"
  def getOnlineReleaseAssetUrl(version: String, asset: String): String = {
    s"""${OnlineRepositoryUrlBase}/releases/download/${version}/${asset}"""
  }

  def getPlugins(): Seq[PluginMetadata] = {
    (if(LocalRepositoryIndexFile.exists){
      parsePluginJson(FileUtils.readFileToString(LocalRepositoryIndexFile, "UTF-8"))
    } else Nil) ++ (if(OnlineRepositoryIndexFile.exists){
      parsePluginJson(FileUtils.readFileToString(OnlineRepositoryIndexFile, "UTF-8"))
    } else Nil)
  }

}

// Mapped from plugins.json
case class PluginMetadata(
  id: String,
  name: String,
  description: String,
  versions: Seq[VersionDef],
  default: Boolean = false
){
  lazy val latestVersion: VersionDef = versions.last
}

case class VersionDef(
  version: String,
  url: String,
  range: String,
  jarFileName: Option[String]
){
  lazy val file = url.substring(url.lastIndexOf("/") + 1)
}

