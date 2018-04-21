package gitbucket.core.util

import java.nio.charset.StandardCharsets

import editorconfig.JGitResource
import org.ec4j.core.model.Version
import org.ec4j.core.{EditorConfigConstants, EditorConfigLoader, ResourcePropertiesService}
import org.eclipse.jgit.api.Git

import collection.JavaConverters._

object EditorConfigUtil {
  def readEditorConfig(git: Git, rev: String, path: String) = {
    val resourcePropertiesService = ResourcePropertiesService
      .builder()
      .configFileName(EditorConfigConstants.EDITORCONFIG)
      .loader(EditorConfigLoader.of(Version.CURRENT))
      .keepUnset(true)
      .build()

    val jgitResource: JGitResource = new JGitResource(git, rev, path, StandardCharsets.UTF_8)
    resourcePropertiesService.queryProperties(jgitResource)
  }
}
