package io.jenkins.plugins.git_push;

import com.google.common.collect.ImmutableSet;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** @author Réda Housni Alaoui */
public class GitPushStep extends Step {

  private GitSCM gitScm;
  private String targetBranch;
  private String targetRepo;

  @DataBoundConstructor
  public GitPushStep() {
    // Only needed to mark the constructor with @DataBoundConstructor
  }

  @DataBoundSetter
  public void setGitScm(GitSCM gitScm) {
    this.gitScm = gitScm;
  }

  public GitSCM getGitScm() {
    return gitScm;
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
  public StepExecution start(StepContext context) {
    return new Execution(context, gitScm, targetBranch, targetRepo);
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

    private static final long serialVersionUID = 1L;

    private transient GitSCM gitScm;
    private String targetBranch;
    private String targetRepo;

    protected Execution(
        @Nonnull StepContext context,
        @Nonnull GitSCM gitScm,
        @Nonnull String targetBranch,
        @Nonnull String targetRepo) {
      super(context);
      this.gitScm = gitScm;
      this.targetBranch = targetBranch;
      this.targetRepo = targetRepo;
    }

    @Override
    protected Void run() throws Exception {
      if (gitScm == null) {
        throw new AbortException("gitScm is missing");
      }

      new GitPushCommand(
              gitScm,
              getContext().get(Run.class),
              getContext().get(TaskListener.class),
              getContext().get(FilePath.class))
          .call(targetBranch, targetRepo);

      return null;
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
      outputStream.writeObject(targetBranch);
      outputStream.writeObject(targetRepo);
    }

    private void readObject(ObjectInputStream inputStream)
        throws IOException, ClassNotFoundException {
      gitScm = null;
      targetBranch = (String) inputStream.readObject();
      targetRepo = (String) inputStream.readObject();
    }
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getDisplayName() {
      return "Git Push";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return ImmutableSet.of(Run.class, TaskListener.class, FilePath.class);
    }

    @Override
    public String getFunctionName() {
      return "gitPush";
    }
  }
}
