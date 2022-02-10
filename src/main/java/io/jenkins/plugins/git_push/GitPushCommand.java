package io.jenkins.plugins.git_push;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;

/** @author Réda Housni Alaoui */
public class GitPushCommand {

  private final GitSCM scm;
  private final Run<?, ?> run;
  private final TaskListener listener;
  private final FilePath workspace;

  public GitPushCommand(GitSCM scm, Run<?, ?> run, TaskListener listener, FilePath workspace) {
    this.scm = scm;
    this.run = run;
    this.listener = listener;
    this.workspace = workspace;
  }

  public void call(String targetBranch, String targetRepo)
      throws IOException, InterruptedException, Failure {
    EnvVars environment = run.getEnvironment(listener);

    GitClient git =
        scm.createClient(listener, environment, run, workspace, new GitPushUnsupportedCommand());

    RemoteConfig remote = scm.getRepositoryByName(targetRepo);
    if (remote == null) {
      throw new AbortException("No repository found for target repo name '" + targetRepo + "'");
    }

    remote = scm.getParamExpandedRepo(environment, remote);
    URIish remoteURI = remote.getURIs().get(0);

    try {
      git.fetch_().from(remoteURI, remote.getFetchRefSpecs()).execute();
      ObjectId remoteRev = git.revParse(targetRepo + "/" + targetBranch);
      git.merge().setRevisionToMerge(remoteRev).execute();
      git.push().to(remoteURI).ref("HEAD:" + targetBranch).tags(true).execute();
      git.fetch_().from(remoteURI, remote.getFetchRefSpecs()).execute();
    } catch (GitException e) {
      throw new Failure("Failed to push to " + targetRepo, e);
    }
  }

  public static class Failure extends Exception {
    public Failure(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
