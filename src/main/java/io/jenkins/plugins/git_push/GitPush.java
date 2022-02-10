package io.jenkins.plugins.git_push;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/** @author Réda Housni Alaoui */
public class GitPush extends Recorder implements Serializable {

  private String targetBranch;
  private String targetRepo;

  @DataBoundConstructor
  public GitPush() {
    // Only needed to mark the constructor with @DataBoundConstructor
  }

  @DataBoundSetter
  public void setTargetBranch(String targetBranch) {
    this.targetBranch = targetBranch;
  }

  public String getTargetBranch() {
    return targetBranch;
  }

  @DataBoundSetter
  public void setTargetRepo(String targetRepo) {
    this.targetRepo = targetRepo;
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
    try {
      new GitPushCommand(gitSCM, build, listener, build.getWorkspace())
          .call(environment.expand(targetBranch), environment.expand(targetRepo));
    } catch (GitPushCommand.Failure e) {
      e.printStackTrace(listener.error(e.getMessage()));
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

    public FormValidation doCheckTargetRepo(@QueryParameter String targetRepo) {
      return checkFieldNotEmpty(targetRepo);
    }

    private FormValidation checkFieldNotEmpty(String value) {
      value = StringUtils.strip(value);

      if (value == null || value.equals("")) {
        return FormValidation.error("This field is required");
      }
      return FormValidation.ok();
    }
  }
}
