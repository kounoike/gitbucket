package editorconfig;

import org.ec4j.core.Resource;
import org.ec4j.core.ResourcePath;
import org.ec4j.core.model.Ec4jPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

public class JGitResource implements Resource {
    Git git;
    Repository repo;
    String revStr;

    Ec4jPath path;
    Charset encoding;

    private static String removeInitialSlash(Ec4jPath path) {
        return Ec4jPath.Ec4jPaths.root().relativize(path).toString();
    }

    public JGitResource(Git git, String revStr, String path, Charset encoding){
        System.out.println("JGitResource:" + path);
        this.git = git;
        this.repo = git.getRepository();
        this.path = Ec4jPath.Ec4jPaths.of("/" + path);
        this.revStr = revStr;
        this.encoding = encoding;
    }

    public JGitResource(Git git, String revStr, Ec4jPath path, Charset encoding){
        System.out.println("JGitResource(Ec4jPath):" + path);
        this.git = git;
        this.repo = git.getRepository();
        this.path = path;
        this.revStr = revStr;
        this.encoding = encoding;
    }

    private RevTree getRevTree() throws IOException {
        ObjectReader reader = repo.newObjectReader();
        try {
            RevWalk revWalk = new RevWalk(reader);
            ObjectId id = repo.resolve(revStr);
            RevCommit commit = revWalk.parseCommit(id);
            return commit.getTree();
        } finally {
            reader.close();
        }
    }

    @Override
    public boolean exists() {
        ObjectReader reader = repo.newObjectReader();
        try {
            TreeWalk treeWalk = TreeWalk.forPath(reader, removeInitialSlash(path), getRevTree());
            if (treeWalk != null){
                System.out.println("exists found!" + path);
                return true;
            }
            else {
                System.out.println("exists not found!" + path);
                return false;
            }
        } catch (IOException e) {
            System.out.println("exists exception!" + path);
            return false;
        } finally {
            reader.close();
        }
    }

    @Override
    public ResourcePath getParent() {
        Ec4jPath parent = path.getParentPath();
        return parent == null ? null : new JGitResourcePath(git, revStr, path.getParentPath(), encoding);
    }

    @Override
    public Ec4jPath getPath() {
        return path;
    }

    @Override
    public RandomReader openRandomReader() throws IOException {
        System.out.println("OpenRandomReader!" + path.toString());
        return Resources.StringRandomReader.ofReader(openReader());
    }

    @Override
    public Reader openReader() throws IOException {
        System.out.println("OpenReader!" + path.toString());
        ObjectReader reader = repo.newObjectReader();
        try {
            TreeWalk treeWalk = TreeWalk.forPath(reader, removeInitialSlash(path), getRevTree());
            return new InputStreamReader(reader.open(treeWalk.getObjectId(0)).openStream(), encoding);
        } finally {
            reader.close();
        }
    }
}
