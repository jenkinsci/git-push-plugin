package io.jenkins.plugins.git_push;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/** @author Réda Housni Alaoui */
public class GitPush extends Recorder implements Serializable {

  private final String targetBranch;
  private final String targetRepo;

  @DataBoundConstructor
  public GitPush(String targetBranch, String targetRepo) {
    this.targetBranch = targetBranch;
    this.targetRepo = targetRepo;
  }

  public String getTargetBranch() {
    return targetBranch;
  }

  public String getTargetRepo() {
    return targetRepo;
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
      ObjectId remoteRev = git.revParse(remoteRepo + "/" + remoteBranch);
      git.merge().setRevisionToMerge(remoteRev).execute();
      git.push().to(remoteURI).ref("HEAD:" + remoteBranch).tags(true).execute();
      git.fetch_().from(remoteURI, remote.getFetchRefSpecs()).execute();
    } catch (GitException e) {
      e.printStackTrace(listener.error("Failed to push to " + remoteRepo));
      return false;
    }

    return true;
  }

  @Extension
  public static class Descriptor extends BuildStepDescriptor<Publisher> {

    @Override
    public String getDisplayName() {
      return "Git Push";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    public FormValidation doCheckTargetBranch(@QueryParameter String targetBranch) {
      return checkFieldNotEmpty(targetBranch);
    }

    public FormValidation doCheckTargetRepo(
        @AncestorInPath AbstractProject<?, ?> project, @QueryParameter String targetRepo) {

      FormValidation validation = checkFieldNotEmpty(targetRepo);
      if (validation.kind != FormValidation.Kind.OK) {
        return validation;
      }

      SCM scm = project.getScm();
      if (!(scm instanceof GitSCM)) {
        return FormValidation.warning(
            "Project not currently configured to use Git; cannot check remote repository");
      }

      GitSCM gitSCM = (GitSCM) scm;
      if (gitSCM.getRepositoryByName(targetRepo) == null) {
        return FormValidation.error(
            "No remote repository configured with name '" + targetRepo + "'");
      }

      return FormValidation.ok();
    }

    private FormValidation checkFieldNotEmpty(String value) {
      value = StringUtils.strip(value);

      if (value == null || value.equals("")) {
        return FormValidation.error("This field is required");
      }
      return FormValidation.ok();
    }
  }

  private static class GitPushUnsupportedCommand extends UnsupportedCommand {
    @Override
    public boolean determineSupportForJGit() {
      // Do not know why we exactly need that. Inspired by
      // https://github.com/jenkinsci/git-plugin/blob/b95bffa7579c91cb79616b5a1e45feea52e4f70b/src/main/java/hudson/plugins/git/GitPublisher.java#L189
      return false;
    }
  }
}
