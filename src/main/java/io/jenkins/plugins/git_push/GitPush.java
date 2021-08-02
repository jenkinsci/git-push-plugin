package io.jenkins.plugins.git_push;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.tasks.Recorder;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.kohsuke.stapler.DataBoundConstructor;

/** @author Réda Housni Alaoui */
public class GitPush extends Recorder {

  private final String targetRepo;
  private final String targetBranch;

  @DataBoundConstructor
  public GitPush(String targetRepo, String targetBranch) {
    this.targetRepo = targetRepo;
    this.targetBranch = targetBranch;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, IOException {

    // during matrix build, the push back would happen at the very end only once for the whole
    // matrix,
    // not for individual configuration build.
    if (build.getClass().getName().equals("hudson.matrix.MatrixRun")) {
      return true;
    }

    Result buildResult = build.getResult();
    if (buildResult == null || buildResult.isWorseThan(Result.SUCCESS)) {
      listener.getLogger().println("Build did not succeed, so no pushing will occur.");
      return true;
    }

    SCM scm = build.getProject().getScm();

    if (!(scm instanceof GitSCM)) {
      return false;
    }

    GitSCM gitSCM = (GitSCM) scm;
    EnvVars environment = build.getEnvironment(listener);

    GitClient git =
        gitSCM.createClient(
            listener, environment, build, build.getWorkspace(), new GitPushUnsupportedCommand());

    String remoteRepo = environment.expand(targetRepo);
    String remoteBranch = environment.expand(targetBranch);

    RemoteConfig remote = gitSCM.getRepositoryByName(remoteRepo);
    if (remote == null) {
      throw new AbortException("No repository found for target repo name '" + remoteRepo + "'");
    }

    remote = gitSCM.getParamExpandedRepo(environment, remote);
    URIish remoteURI = remote.getURIs().get(0);

    try {
      git.fetch_().from(remoteURI, remote.getFetchRefSpecs()).execute();
      ObjectId head = git.revParse("HEAD");
      ObjectId remoteRev = git.revParse(remoteRepo + "/" + remoteBranch);
      if (!head.equals(remoteRev)) {
        git.merge().setRevisionToMerge(remoteRev).execute();
      } else {
        listener
            .getLogger()
            .println("No merge required. HEAD equals " + remoteRepo + "/" + remoteBranch);
      }
      git.push().to(remoteURI).ref("HEAD:" + remoteRepo).tags(true).execute();
    } catch (GitException e) {
      e.printStackTrace(listener.error("Failed to push to " + remoteRepo));
      return false;
    }

    return true;
  }

  private static class GitPushUnsupportedCommand extends UnsupportedCommand {
    @Override
    public boolean determineSupportForJGit() {
      return false;
    }
  }
}
