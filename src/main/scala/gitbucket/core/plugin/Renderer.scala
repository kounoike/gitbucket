package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService
import gitbucket.core.util.JGitUtil.{ContentInfo, RepositoryInfo}
import gitbucket.core.view.{Markdown, helpers}
import gitbucket.core.view.helpers.urlLink
import play.twirl.api.Html

/**
 * A render engine to render content to HTML.
 */
trait Renderer {

  /**
   * Render the given request to HTML.
   */
  def render(request: RenderRequest): Html

  def isEditable: Boolean = false
}

trait EditableRenderer extends Renderer {
  override def isEditable: Boolean = true

}

object ImageRenderer extends Renderer {
  override def render(request: RenderRequest): Html = {
    Html(s"""<img src="${request.rawPath}">""")
  }
}

object SVGRenderer extends Renderer {
  override def render(request: RenderRequest): Html = {
    Html(request.fileContent)
  }
}

object MarkdownRenderer extends EditableRenderer {
  override def render(request: RenderRequest): Html = {
    import request._
    Html(
      Markdown.toHtml(
        markdown = fileContent,
        repository = repository,
        enableWikiLink = enableWikiLink,
        enableRefsLink = enableRefsLink,
        enableAnchor = enableAnchor,
        enableLineBreaks = false
      )(context)
    )
  }
}

object DefaultRenderer extends EditableRenderer {
  override def render(request: RenderRequest): Html = {
    Html(s"""<tt><pre class="plain">${urlLink(request.fileContent)}</pre></tt>""")
  }
}

case class RenderRequest(
  filePath: List[String],
  fileContent: String,
  branch: String,
  repository: RepositoryService.RepositoryInfo,
  enableWikiLink: Boolean,
  enableRefsLink: Boolean,
  enableAnchor: Boolean,
  context: Context
) {
  val rawPath = s"""${helpers.url(repository)(context)}/raw/${branch}/${filePath.mkString("/")}"""
}

case class GitRenderRequest(
  filePath: List[String],
  contentInfo: ContentInfo,
  branch: String,
  repository: RepositoryService.RepositoryInfo,
  enableWikiLink: Boolean,
  enableRefsLink: Boolean,
  enableAnchor: Boolean,
  context: Context
) {
  val rawPath = s"""${helpers.url(repository)(context)}/raw/${branch}/${filePath.mkString("/")}"""
}
