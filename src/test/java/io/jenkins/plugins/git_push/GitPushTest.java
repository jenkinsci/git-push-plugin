package io.jenkins.plugins.git_push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.plugins.git.extensions.impl.UserIdentity;
import hudson.tasks.Builder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/** @author Réda Housni Alaoui */
public class GitPushTest {

  private static final PersonIdent IDENTITY = new PersonIdent("John Doe", "john@example.com");

  @Rule public JenkinsRule jenkins = new JenkinsRule();
  @Rule public TemporaryFolder originGitRepoDir = new TemporaryFolder();
  @Rule public TemporaryFolder noneJenkinsGitRepoDir = new TemporaryFolder();

  private Git noneJenkinsGitRepo;
  private FreeStyleProject project;

  @Before
  public void beforeEach() throws IOException, GitAPIException {
    Git.init()
        .setBare(true)
        .setDirectory(originGitRepoDir.getRoot())
        .setInitialBranch("master")
        .call();

    Git.cloneRepository()
        .setURI(originGitRepoDir.getRoot().getAbsolutePath())
        .setDirectory(noneJenkinsGitRepoDir.getRoot())
        .call();
    Files.createFile(noneJenkinsGitRepoDir.getRoot().toPath().resolve("first.txt"));
    noneJenkinsGitRepo = Git.open(noneJenkinsGitRepoDir.getRoot());
    noneJenkinsGitRepo.add().addFilepattern("first.txt").call();
    noneJenkinsGitRepo.commit().setMessage("First commit").setCommitter(IDENTITY).call();
    noneJenkinsGitRepo.push().call();

    GitSCM scm =
        new GitSCM(
            Collections.singletonList(
                new UserRemoteConfig(
                    originGitRepoDir.getRoot().getAbsolutePath(), "origin", "", null)),
            Collections.singletonList(new BranchSpec("master")),
            null,
            null,
            Collections.singletonList(new DisableRemotePoll()));

    scm.getExtensions().add(new UserIdentity("John Doe", "john@example.com"));

    project = jenkins.createFreeStyleProject();
    project.setScm(scm);
    project.save();
  }

  @After
  public void afterEach() {
    noneJenkinsGitRepo.close();
  }

  @Test
  public void without_it_no_commit_is_pushed() throws Exception {
    project.getBuildersList().add(new CommitBuilder());
    project.save();

    FreeStyleBuild build = project.scheduleBuild2(0).get();
    jenkins.assertBuildStatus(Result.SUCCESS, build);

    CommitAction commitAction = build.getAction(CommitAction.class);
    assertThat(commitAction).isNotNull();

    try (Git origin = Git.open(originGitRepoDir.getRoot())) {
      assertThatThrownBy(
              () ->
                  origin
                      .getRepository()
                      .parseCommit(ObjectId.fromString(commitAction.commit.name())))
          .isInstanceOf(MissingObjectException.class);
    }
  }

  @Test
  public void it_pushes_commits() throws Exception {
    project.getBuildersList().add(new CommitBuilder());
    project.getPublishersList().add(new GitPush("master", "origin"));
    project.save();

    FreeStyleBuild build = project.scheduleBuild2(0).get();
    jenkins.assertBuildStatus(Result.SUCCESS, build);

    CommitAction commitAction = build.getAction(CommitAction.class);
    assertThat(commitAction).isNotNull();

    try (Git origin = Git.open(originGitRepoDir.getRoot())) {
      RevCommit commit =
          origin.getRepository().parseCommit(ObjectId.fromString(commitAction.commit.name()));
      assertThat(commit.getParentCount()).isEqualTo(1);
    }
  }

  @Test
  public void it_pushes_tags() throws Exception {
    project.getBuildersList().add(new CommitBuilder());
    project.getBuildersList().add(new TagBuilder());
    project.getPublishersList().add(new GitPush("master", "origin"));
    project.save();

    FreeStyleBuild build = project.scheduleBuild2(0).get();
    jenkins.assertBuildStatus(Result.SUCCESS, build);

    CommitAction commitAction = build.getAction(CommitAction.class);
    assertThat(commitAction).isNotNull();
    TagAction tagAction = build.getAction(TagAction.class);
    assertThat(tagAction).isNotNull();

    try (Git origin = Git.open(originGitRepoDir.getRoot())) {
      ObjectId commitId = ObjectId.fromString(commitAction.commit.name());
      List<Ref> tags = origin.getRepository().getRefDatabase().getRefsByPrefix(R_TAGS);
      assertThat(tags)
          .anySatisfy(
              ref -> {
                assertThat(ref.getName()).isEqualTo(R_TAGS + tagAction.tagName);
                try {
                  assertThat(origin.getRepository().getRefDatabase().peel(ref).getPeeledObjectId())
                      .isEqualTo(commitId);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  @Test
  public void it_create_merge_commit_if_needed() throws Exception {
    project
        .getBuildersList()
        .add(
            new CommitBuilder()
                .gitDir(noneJenkinsGitRepoDir.getRoot().getAbsolutePath())
                .push(true)
                .publishCommitAction(false));
    project.getBuildersList().add(new CommitBuilder());
    project.getPublishersList().add(new GitPush("master", "origin"));
    project.save();

    FreeStyleBuild build = project.scheduleBuild2(0).get();
    jenkins.assertBuildStatus(Result.SUCCESS, build);

    CommitAction commitAction = build.getAction(CommitAction.class);
    assertThat(commitAction).isNotNull();

    try (Git origin = Git.open(originGitRepoDir.getRoot())) {
      ObjectId commitId = ObjectId.fromString(commitAction.commit.name());
      assertThatCode(() -> origin.getRepository().parseCommit(commitId)).doesNotThrowAnyException();

      Ref master = origin.getRepository().getRefDatabase().findRef(R_HEADS + "master");
      assertThat(master).isNotNull();

      RevCommit masterHead = origin.getRepository().parseCommit(master.getObjectId());
      assertThat(masterHead).isNotNull();
      assertThat(masterHead.getParentCount()).isEqualTo(2);
    }
  }

  private static class CommitBuilder extends Builder {

    private String gitDir;
    private boolean push;
    private boolean publishCommitAction = true;

    CommitBuilder() {}

    CommitBuilder gitDir(String gitDir) {
      this.gitDir = gitDir;
      return this;
    }

    CommitBuilder push(boolean push) {
      this.push = push;
      return this;
    }

    CommitBuilder publishCommitAction(boolean publishCommitAction) {
      this.publishCommitAction = publishCommitAction;
      return this;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
        throws IOException {
      Path finalGitDir;
      if (gitDir == null) {
        finalGitDir = Paths.get(build.getWorkspace().getRemote());
      } else {
        finalGitDir = Paths.get(gitDir);
      }

      String fileName = UUID.randomUUID() + ".txt";
      Files.createFile(finalGitDir.resolve(fileName));
      try (Git git = Git.open(finalGitDir.toFile())) {
        git.add().addFilepattern(fileName).call();
        RevCommit commit = git.commit().setMessage("Add " + fileName).setCommitter(IDENTITY).call();
        if (publishCommitAction) {
          build.addAction(new CommitAction(commit));
        }
        if (push) {
          git.push().call();
        }
      } catch (GitAPIException e) {
        throw new RuntimeException(e);
      }
      return true;
    }
  }

  private static class TagBuilder extends Builder {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
        throws IOException {
      Path workspacePath = Paths.get(build.getWorkspace().getRemote());
      try (Git workspaceGit = Git.open(workspacePath.toFile())) {
        String tagName = UUID.randomUUID().toString();
        workspaceGit.tag().setName(tagName).call();
        build.addAction(new TagAction(tagName));
      } catch (GitAPIException e) {
        throw new RuntimeException(e);
      }
      return true;
    }
  }

  private static class TestAction implements Action {

    @Override
    public String getIconFileName() {
      return null;
    }

    @Override
    public String getDisplayName() {
      return null;
    }

    @Override
    public String getUrlName() {
      return null;
    }
  }

  private static class TagAction extends TestAction {

    public final String tagName;

    private TagAction(String tagName) {
      this.tagName = tagName;
    }
  }

  private static class CommitAction extends TestAction {

    public final RevCommit commit;

    CommitAction(RevCommit commit) {
      this.commit = commit;
    }
  }
}
