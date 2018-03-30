package gitbucket.core.servlet

import java.io.{File, FileOutputStream}

import akka.event.Logging
import com.typesafe.config.ConfigFactory
import gitbucket.core.GitBucketCoreModule
import gitbucket.core.plugin.{PluginRegistry, PluginRepository}
import gitbucket.core.service.{ActivityService, SystemSettingsService}
import gitbucket.core.util.DatabaseConfig
import gitbucket.core.util.Directory._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.JDBCUtil._
import gitbucket.core.model.Profile.profile.blockingApi._
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.manager.JDBCVersionManager
import javax.servlet.{ServletContextEvent, ServletContextListener}

import org.apache.commons.io.{FileUtils, IOUtils}
import org.slf4j.LoggerFactory
import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.github.zafarkhaja.semver.{Version => Semver}
import gigahorse.support.asynchttpclient.Gigahorse
import scala.concurrent._
import scala.concurrent.duration._

import scala.collection.JavaConverters._


/**
 * Initialize GitBucket system.
 * Update database schema and load plug-ins automatically in the context initializing.
 */
class InitializeListener extends ServletContextListener with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[InitializeListener])

  // ActorSystem for Quartz scheduler
  private val system = ActorSystem("job", ConfigFactory.parseString(
    """
      |akka {
      |  quartz {
      |    schedules {
      |      Daily {
      |        expression = "0 0 0 * * ?"
      |      }
      |    }
      |  }
      |}
    """.stripMargin))

  override def contextInitialized(event: ServletContextEvent): Unit = {
    val dataDir = event.getServletContext.getInitParameter("gitbucket.home")
    if(dataDir != null){
      System.setProperty("gitbucket.home", dataDir)
    }
    org.h2.Driver.load()

    Database() withTransaction { session =>
      val conn = session.conn
      val manager = new JDBCVersionManager(conn)

      // Check version
      checkVersion(manager, conn)

      // Run normal migration
      logger.info("Start schema update")
      new Solidbase().migrate(conn, Thread.currentThread.getContextClassLoader, DatabaseConfig.liquiDriver, GitBucketCoreModule)

      // Rescue code for users who updated from 3.14 to 4.0.0
      // https://github.com/gitbucket/gitbucket/issues/1227
      val currentVersion = manager.getCurrentVersion(GitBucketCoreModule.getModuleId)
      val databaseVersion = if(currentVersion == "4.0"){
        manager.updateVersion(GitBucketCoreModule.getModuleId, "4.0.0")
        "4.0.0"
      } else currentVersion

      val gitbucketVersion = GitBucketCoreModule.getVersions.asScala.last.getVersion
      if(databaseVersion != gitbucketVersion){
        throw new IllegalStateException(s"Initialization failed. GitBucket version is ${gitbucketVersion}, but database version is ${databaseVersion}.")
      }

      // Install bundled plugins
      extractBundledPlugins(gitbucketVersion)

      // Download plugins
      downloadPlugins(gitbucketVersion)

      // Load plugins
      logger.info("Initialize plugins")
      PluginRegistry.initialize(event.getServletContext, loadSystemSettings(), conn)
    }

    // Start Quartz scheduler
    val scheduler = QuartzSchedulerExtension(system)

    scheduler.schedule("Daily", system.actorOf(Props[DeleteOldActivityActor]), "DeleteOldActivity")
  }

  private def checkVersion(manager: JDBCVersionManager, conn: java.sql.Connection): Unit = {
    logger.info("Check version")
    val versionFile = new File(GitBucketHome, "version")

    if(versionFile.exists()){
      val version = FileUtils.readFileToString(versionFile, "UTF-8")
      if(version == "3.14"){
        // Initialization for GitBucket 3.14
        logger.info("Migration to GitBucket 4.x start")

        // Backup current data
        val dataMvFile = new File(GitBucketHome, "data.mv.db")
        if(dataMvFile.exists) {
          FileUtils.copyFile(dataMvFile, new File(GitBucketHome, "data.mv.db_3.14"))
        }
        val dataTraceFile = new File(GitBucketHome, "data.trace.db")
        if(dataTraceFile.exists) {
          FileUtils.copyFile(dataTraceFile, new File(GitBucketHome, "data.trace.db_3.14"))
        }

        // Change form
        manager.initialize()
        manager.updateVersion(GitBucketCoreModule.getModuleId, "4.0.0")
        conn.select("SELECT PLUGIN_ID, VERSION FROM PLUGIN"){ rs =>
          manager.updateVersion(rs.getString("PLUGIN_ID"), rs.getString("VERSION"))
        }
        conn.update("DROP TABLE PLUGIN")
        versionFile.delete()

        logger.info("Migration to GitBucket 4.x completed")

      } else {
        throw new Exception("GitBucket can't migrate from this version. Please update to 3.14 at first.")
      }
    }
  }

  private def extractBundledPlugins(gitbucketVersion: String): Unit = {
    logger.info("Extract bundled plugins")
    val cl = Thread.currentThread.getContextClassLoader
    try {
      using(cl.getResourceAsStream("plugins/plugins.json")){ pluginsFile =>
        if(pluginsFile != null){
          val pluginsJson = IOUtils.toString(pluginsFile, "UTF-8")

          FileUtils.forceMkdir(PluginRepository.LocalRepositoryDir)
          FileUtils.write(PluginRepository.LocalRepositoryIndexFile, pluginsJson, "UTF-8")

          val plugins = PluginRepository.parsePluginJson(pluginsJson)
          plugins.foreach { plugin =>
            plugin.versions.sortBy { x => Semver.valueOf(x.version) }.reverse.zipWithIndex.foreach { case (version, i) =>
              val file = new File(PluginRepository.LocalRepositoryDir, version.file)
              if(!file.exists) {
                logger.info(s"Copy ${plugin} to ${file.getAbsolutePath}")
                FileUtils.forceMkdirParent(file)
                using(cl.getResourceAsStream("plugins/" + version.file), new FileOutputStream(file)){ case (in, out) => IOUtils.copy(in, out) }

                if(plugin.default && i == 0){
                  logger.info(s"Enable ${file.getName} in default")
                  FileUtils.copyFile(file, new File(PluginHome, version.file))
                }
              }
            }
          }
        }
      }
    } catch {
      case e: Exception => logger.error("Error in extracting bundled plugin", e)
    }
  }

  private def downloadPlugins(gitbucketVersion: String): Unit = {
    Gigahorse.withHttp(Gigahorse.config) { http =>
      FileUtils.forceMkdir(PluginRepository.OnlineRepositoryDir)
      val url = PluginRepository.getOnlineReleaseAssetUrl(gitbucketVersion, PluginRepository.IndexFileName)
      logger.info(s"""Start downloading ${url} to ${PluginRepository.OnlineRepositoryIndexFile}""")
      val r = Gigahorse.url(url).get
      val f = http.download(r, PluginRepository.OnlineRepositoryIndexFile)
      Await.result(f, 30.seconds)
      logger.info("plugins.json downloaded")

      val pluginsJson = FileUtils.readFileToString(PluginRepository.OnlineRepositoryIndexFile, "UTF-8")

      val plugins = PluginRepository.parsePluginJson(pluginsJson)

      plugins.foreach { plugin =>
        val ver = plugin.versions.head

        logger.info(s"""downloading ${ver.url}""")
        val r = Gigahorse.url(ver.url).get
        val f = http.download(r, new File(PluginRepository.OnlineRepositoryDir, ver.jarFileName.get))
        Await.result(f, 30.seconds)
        logger.info(s"""download completed: ${ver.jarFileName.get}""")
      }
    }
  }

  override def contextDestroyed(event: ServletContextEvent): Unit = {
    // Shutdown Quartz scheduler
    system.terminate()
    // Shutdown plugins
    PluginRegistry.shutdown(event.getServletContext, loadSystemSettings())
    // Close datasource
    Database.closeDataSource()
  }

}

class DeleteOldActivityActor extends Actor with SystemSettingsService with ActivityService {

  private val logger = Logging(context.system, this)

  def receive = {
    case s: String => {
      loadSystemSettings().activityLogLimit.foreach { limit =>
        if(limit > 0){
          Database() withTransaction { implicit session =>
            val rows = deleteOldActivities(limit)
            logger.info(s"Deleted ${rows} activity logs")
          }
        }
      }
    }
  }
}
