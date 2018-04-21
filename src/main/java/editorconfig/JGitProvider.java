package editorconfig;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.editorconfig.core.provider.StreamProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JGitProvider implements StreamProvider {
    private Git git;
    private Repository repo;
    private ObjectId id;
    private RevTree revTree;

    public JGitProvider(Git git, String revStr) throws IOException {
        this.git = git;
        repo = git.getRepository();
        id = repo.resolve(revStr);
    }

    private RevTree getRevTree() throws IOException {
        if(revTree == null) {
            ObjectReader reader = repo.newObjectReader();
            try{
                RevWalk revWalk = new RevWalk(reader);
                RevCommit commit = revWalk.parseCommit(id);
                revTree = commit.getTree();
            }
            finally{
                reader.close();
            }
        }
        return revTree;
    }

    @Override
    public String getParent(String filePath) {
        int sepPos = filePath.lastIndexOf('/');
        if(sepPos > -1) {
            return filePath.substring(0, filePath.lastIndexOf('/'));
        }
        else{
            return null;
        }
    }

    @Override
    public String combinePath(String dirPath, String filePath) {
        return dirPath + "/" + filePath;
    }

    @Override
    public InputStream openStream(String path) {
        if (path.startsWith("/")){
            path = path.substring(1);
        }
        ObjectReader reader = this.repo.newObjectReader();
        try{
            TreeWalk treeWalk = TreeWalk.forPath(reader, path, getRevTree());
            if (treeWalk != null) {
                return reader.open(treeWalk.getObjectId(0)).openStream();
            }
            else {
                return null;
            }
        } catch (IncorrectObjectTypeException e) {
            return null;
        } catch (CorruptObjectException e) {
            return null;
        } catch (MissingObjectException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            reader.close();
        }
    }
}
