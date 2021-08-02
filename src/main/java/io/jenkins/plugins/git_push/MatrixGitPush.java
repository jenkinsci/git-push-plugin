package io.jenkins.plugins.git_push;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import java.io.IOException;

/** @author Réda Housni Alaoui */
@Extension(optional = true)
public class MatrixGitPush implements MatrixAggregatable {
  /** For a matrix project, push should only happen once. */
  public MatrixAggregator createAggregator(
      MatrixBuild build, Launcher launcher, BuildListener listener) {
    return new MatrixAggregator(build, launcher, listener) {
      @Override
      public boolean endBuild() throws InterruptedException, IOException {
        GitPush gitPush = build.getParent().getPublishersList().get(GitPush.class);
        if (gitPush != null) {
          return gitPush.perform(build, launcher, listener);
        }
        return true;
      }
    };
  }
}
