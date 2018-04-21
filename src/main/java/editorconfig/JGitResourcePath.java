package editorconfig;

import org.ec4j.core.Resource;
import org.ec4j.core.ResourcePath;
import org.ec4j.core.model.Ec4jPath;
import org.eclipse.jgit.api.Git;
import java.nio.charset.Charset;

public class JGitResourcePath implements ResourcePath {
    Git git;
    String revStr;
    Ec4jPath path;
    Charset encoding;

    public JGitResourcePath(Git git, String revStr, Ec4jPath path, Charset encoding){
        this.git = git;
        this.revStr = revStr;
        this.path = path;
        this.encoding = encoding;
    }


    @Override
    public ResourcePath getParent() {
        Ec4jPath parent = path.getParentPath();
        return parent == null ? null : new JGitResourcePath(git, revStr, parent, encoding);
    }

    @Override
    public Ec4jPath getPath() {
        return path;
    }

    @Override
    public boolean hasParent() {
        return path.getParentPath() != null;
    }

    @Override
    public Resource relativize(Resource resource) {
        if (resource instanceof JGitResource) {
            JGitResource jgitResource = (JGitResource) resource;
            return new JGitResource(git, revStr, path.relativize(jgitResource.path).toString(), encoding);
        } else {
            throw new IllegalArgumentException(
                this.getClass().getName() + ".relativize(Resource resource) can handle only instances of "
                    + JGitResource.class.getName());
        }
    }

    @Override
    public Resource resolve(String name) {
        if(path == null){
            return new JGitResource(git, revStr, name, encoding);
        }
        else {
            return new JGitResource(git, revStr, path.resolve(name), encoding);
        }
    }
}
