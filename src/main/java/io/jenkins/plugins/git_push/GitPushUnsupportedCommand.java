package io.jenkins.plugins.git_push;

import org.jenkinsci.plugins.gitclient.UnsupportedCommand;

/** @author Réda Housni Alaoui */
class GitPushUnsupportedCommand extends UnsupportedCommand {
  @Override
  public boolean determineSupportForJGit() {
    // Do not know why we exactly need that. Inspired by
    // https://github.com/jenkinsci/git-plugin/blob/b95bffa7579c91cb79616b5a1e45feea52e4f70b/src/main/java/hudson/plugins/git/GitPublisher.java#L189
    return false;
  }
}
