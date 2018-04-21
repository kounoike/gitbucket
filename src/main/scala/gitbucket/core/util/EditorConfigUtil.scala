package gitbucket.core.util

import editorconfig.JGitProvider
import org.eclipse.jgit.api.Git
import org.editorconfig.core.EditorConfig
import collection.JavaConverters._

object EditorConfigUtil {
  def readEditorConfig(git: Git, rev: String, path: String) = {
    val econfig: EditorConfig = new EditorConfig(new JGitProvider(git, rev))
    econfig
      .getProperties(path)
      .asScala
      .map { outPair =>
        (outPair.getKey(), outPair.getVal())
      }
      .toMap
  }
}
