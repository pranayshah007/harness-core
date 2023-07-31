/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.UNREACHABLE_HOST;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.ADMIN;
import static io.harness.exception.WingsException.ADMIN_SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.git.Constants.EXCEPTION_STRING;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.shell.AuthenticationScheme.HTTP_PASSWORD;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.SshSessionFactory.generateTGTUsingSshConfig;
import static io.harness.shell.SshSessionFactory.getSSHSession;

import static software.wings.beans.yaml.YamlConstants.GIT_DEFAULT_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GIT_HELM_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GIT_TERRAFORM_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GIT_TERRAGRUNT_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GIT_TRIGGER_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.utils.SshDelegateHelperUtils.createSshSessionConfig;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GitClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.filesystem.FileIo;
import io.harness.git.ExceptionSanitizer;
import io.harness.git.GitFetchMetadataLocalThread;
import io.harness.git.UsernamePasswordCredentialsProviderWithSkipSslVerify;
import io.harness.git.model.ChangeType;
import io.harness.git.model.GitFile;
import io.harness.git.model.GitRepositoryType;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.SshFactory;
import io.harness.shell.ssh.client.jsch.JschConnection;

import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.yaml.GitCheckoutResult;
import software.wings.beans.yaml.GitCloneResult;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitCommitResult;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitFilesBetweenCommitsRequest;
import software.wings.misc.CustomUserGitConfigSystemReader;
import software.wings.service.intfc.yaml.GitClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.SystemReader;

/**
 * Created by anubhaw on 10/16/17.
 */

@OwnedBy(DX)
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
@BreakDependencyOn("software.wings.beans.GitConfig")
public class GitClientImpl implements GitClient {
  private static final int SOCKET_CONNECTION_READ_TIMEOUT_SECONDS = 60;

  @Inject GitClientHelper gitClientHelper;
  /**
   * factory for creating HTTP connections. By default, JGit uses JDKHttpConnectionFactory which doesn't work well with
   * proxy. See:
   * https://stackoverflow.com/questions/67492788/eclipse-egit-tfs-git-connection-authentication-not-supported
   */
  public static final HttpConnectionFactory connectionFactory = new HttpClientConnectionFactory();

  @VisibleForTesting
  synchronized GitCloneResult clone(GitConfig gitConfig, String gitRepoDirectory, String branch, boolean noCheckout) {
    try {
      if (new File(gitRepoDirectory).exists()) {
        deleteDirectoryAndItsContentIfExists(gitRepoDirectory);
      }
    } catch (IOException ioex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception while deleting repo: ", getMessage(ioex));
    }

    log.info(GIT_YAML_LOG_PREFIX + "cloning repo, Git repo directory :{}", gitRepoDirectory);

    CloneCommand cloneCommand = (CloneCommand) getAuthConfiguredCommand(Git.cloneRepository(), gitConfig);
    try (Git git = cloneCommand.setURI(gitConfig.getRepoUrl())
                       .setDirectory(new File(gitRepoDirectory))
                       .setBranch(isEmpty(branch) ? null : branch)
                       // if set to <code>true</code> no branch will be checked out, after the clone.
                       // This enhances performance of the clone command when there is no need for a checked out branch.
                       .setNoCheckout(noCheckout)
                       .call()) {
      return GitCloneResult.builder().build();
    } catch (GitAPIException ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Error in cloning repo: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in cloning repo", USER);
    }
  }

  private void updateRemoteOriginInConfig(GitConfig gitConfig, File gitRepoDirectory) {
    try (Git git = openGit(gitRepoDirectory, gitConfig.getDisableUserGitConfig())) {
      StoredConfig config = git.getRepository().getConfig();
      // Update local remote url if its changed
      String url = gitConfig.getRepoUrl();
      if (!config.getString("remote", "origin", "url").equals(url)) {
        config.setString("remote", "origin", "url", url);
        config.save();
        log.info(GIT_YAML_LOG_PREFIX + "Local repo remote origin is updated to : ", url);
      }
    } catch (IOException ioex) {
      log.error(GIT_YAML_LOG_PREFIX + "Failed to update repo url in git config", ioex);
    }
  }

  @Override
  public synchronized GitDiffResult diff(
      GitOperationContext gitOperationContext, boolean excludeFilesOutsideSetupFolder) {
    GitConfig gitConfig = gitOperationContext.getGitConfig();
    String startCommitIdStr = gitOperationContext.getGitDiffRequest().getLastProcessedCommitId();
    final String endCommitIdStr =
        StringUtils.defaultIfEmpty(gitOperationContext.getGitDiffRequest().getEndCommitId(), "HEAD");

    ensureRepoLocallyClonedAndUpdated(gitOperationContext);

    GitDiffResult diffResult = GitDiffResult.builder()
                                   .branch(gitConfig.getBranch())
                                   .repoName(gitConfig.getRepoUrl())
                                   .gitFileChanges(new ArrayList<>())
                                   .build();
    try (Git git = openGit(
             new File(gitClientHelper.getRepoDirectory(gitOperationContext)), gitConfig.getDisableUserGitConfig())) {
      git.checkout().setName(gitConfig.getBranch()).call();
      ((PullCommand) (getAuthConfiguredCommand(git.pull(), gitConfig))).call();
      Repository repository = git.getRepository();

      ObjectId endCommitId = requireNonNull(repository.resolve(endCommitIdStr));
      diffResult.setCommitId(endCommitId.getName());

      // Find oldest commit
      if (startCommitIdStr == null) {
        try (RevWalk revWalk = new RevWalk(repository)) {
          RevCommit headRevCommit = revWalk.parseCommit(endCommitId);
          revWalk.sort(RevSort.REVERSE);
          revWalk.markStart(headRevCommit);
          RevCommit firstCommit = revWalk.next();
          startCommitIdStr = firstCommit.getName();
        }
      }
      log.info(GIT_YAML_LOG_PREFIX + "startCommitIdStr =[{}], endCommitIdStr=[{}], endCommitId.name=[{}]",
          startCommitIdStr, endCommitIdStr, endCommitId.name());

      ObjectId endCommitTreeId = repository.resolve(endCommitIdStr + "^{tree}");
      ObjectId startCommitTreeId = repository.resolve(startCommitIdStr + "^{tree}");

      // ensure endCommitTreeId is after start commit
      final boolean commitsInOrder = ensureCommitOrdering(git, startCommitIdStr, endCommitIdStr, repository);
      if (!commitsInOrder) {
        throw new YamlException(String.format("Git diff failed. End Commit [%s] should be after start commit [%s]",
                                    endCommitIdStr, startCommitIdStr),
            ErrorCode.GIT_DIFF_COMMIT_NOT_IN_ORDER, ADMIN_SRE);
      }

      diffResult.setCommitTimeMs(getCommitTimeMs(endCommitIdStr, repository));
      diffResult.setCommitMessage(getCommitMessage(endCommitIdStr, repository));

      try (ObjectReader reader = repository.newObjectReader()) {
        CanonicalTreeParser startTreeIter = new CanonicalTreeParser();
        startTreeIter.reset(reader, startCommitTreeId);
        CanonicalTreeParser endTreeIter = new CanonicalTreeParser();
        endTreeIter.reset(reader, endCommitTreeId);

        List<DiffEntry> diffs = git.diff().setNewTree(endTreeIter).setOldTree(startTreeIter).call();
        addToGitDiffResult(diffs, diffResult, endCommitId, gitConfig, repository, excludeFilesOutsideSetupFolder,
            diffResult.getCommitTimeMs(), getTruncatedCommitMessage(diffResult.getCommitMessage()));
      }

    } catch (IOException | GitAPIException ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in getting commit diff", ADMIN_SRE);
    }
    return diffResult;
  }

  private String getTruncatedCommitMessage(String commitMessage) {
    if (isBlank(commitMessage)) {
      return commitMessage;
    }
    return commitMessage.substring(0, Math.min(commitMessage.length(), 500));
  }

  private Long getCommitTimeMs(String endCommitIdStr, Repository repository) throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      final RevCommit endCommit = revWalk.parseCommit(repository.resolve(endCommitIdStr));
      return endCommit != null ? endCommit.getCommitTime() * 1000L : null;
    }
  }

  private String getCommitMessage(String endCommitIdStr, Repository repository) throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      final RevCommit endCommit = revWalk.parseCommit(repository.resolve(endCommitIdStr));
      return endCommit != null ? endCommit.getFullMessage() : null;
    }
  }

  private boolean ensureCommitOrdering(Git git, String startCommitIdStr, String endCommitIdStr, Repository repository)
      throws IOException, GitAPIException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      final RevCommit startCommit = revWalk.parseCommit(repository.resolve(startCommitIdStr));
      final RevCommit endCommit = revWalk.parseCommit(repository.resolve(endCommitIdStr));
      Iterable<RevCommit> commits = git.log().addRange(startCommit, endCommit).call();
      // If iterator hasNext is false, it means startCommit is older than endCommit, return false
      // and vice versa
      return commits.iterator().hasNext();
    }
  }

  private List<GitFile> getGitFilesFromDiff(
      List<DiffEntry> diffs, Repository repository, GitRepositoryType gitRepositoryType) throws IOException {
    log.info(getGitLogMessagePrefix(gitRepositoryType)
        + "Get git files from diff. Total diff entries found : " + diffs.size());

    List<GitFile> gitFiles = new ArrayList<>();
    for (DiffEntry entry : diffs) {
      ObjectId objectId;
      String filePath;
      if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
        filePath = entry.getOldPath();
        objectId = entry.getOldId().toObjectId();
      } else {
        filePath = entry.getNewPath();
        objectId = entry.getNewId().toObjectId();
      }
      ObjectLoader loader = repository.open(objectId);
      String content = new String(loader.getBytes(), Charset.forName("utf-8"));
      gitFiles.add(GitFile.builder().filePath(filePath).fileContent(content).build());
    }

    return gitFiles;
  }

  @VisibleForTesting
  void addToGitDiffResult(List<DiffEntry> diffs, GitDiffResult diffResult, ObjectId headCommitId, GitConfig gitConfig,
      Repository repository, boolean excludeFilesOutsideSetupFolder, Long commitTimeMs, String commitMessage)
      throws IOException {
    log.info(GIT_YAML_LOG_PREFIX + "Diff Entries: {}", diffs);
    for (DiffEntry entry : diffs) {
      String content = null;
      String filePath;
      ObjectId objectId;
      if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
        filePath = entry.getOldPath();
        // we still want to collect content for deleted file, as it will be needed to decide yamlhandlerSubType in
        // many cases. so getting oldObjectId
        objectId = entry.getOldId().toObjectId();
      } else {
        filePath = entry.getNewPath();
        objectId = entry.getNewId().toObjectId();
      }

      if (excludeFilesOutsideSetupFolder && filePath != null && !filePath.startsWith(SETUP_FOLDER)) {
        log.info("Excluding file [{}] ", filePath);
        continue;
      }

      ObjectLoader loader = repository.open(objectId);
      content = new String(loader.getBytes(), StandardCharsets.UTF_8);
      GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                        .withCommitId(headCommitId.getName())
                                        .withChangeType(gitClientHelper.getChangeType(entry.getChangeType()))
                                        .withFilePath(filePath)
                                        .withFileContent(content)
                                        .withObjectId(objectId.name())
                                        .withAccountId(gitConfig.getAccountId())
                                        .withCommitTimeMs(commitTimeMs)
                                        .withCommitMessage(commitMessage)
                                        .build();
      diffResult.addChangeFile(gitFileChange);
    }
  }

  @VisibleForTesting
  synchronized GitCheckoutResult checkout(GitOperationContext gitOperationContext) throws GitAPIException, IOException {
    GitConfig gitConfig = gitOperationContext.getGitConfig();
    Git git =
        openGit(new File(gitClientHelper.getRepoDirectory(gitOperationContext)), gitConfig.getDisableUserGitConfig());
    try {
      if (isNotEmpty(gitConfig.getBranch())) {
        Ref ref = git.checkout()
                      .setCreateBranch(true)
                      .setName(gitConfig.getBranch())
                      .setUpstreamMode(SetupUpstreamMode.TRACK)
                      .setStartPoint("origin/" + gitConfig.getBranch())
                      .call();
      }

    } catch (RefAlreadyExistsException refExIgnored) {
      log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType())
          + "Reference already exist do nothing."); // TODO:: check gracefully instead of relying on Exception
    }

    String gitRef = gitConfig.getReference() != null ? gitConfig.getReference() : gitConfig.getBranch();
    if (StringUtils.isNotEmpty(gitRef)) {
      git.checkout().setName(gitRef).call();
    }

    return GitCheckoutResult.builder().build();
  }

  @VisibleForTesting
  String getHeadCommit(Git git) {
    try {
      ObjectId id = git.getRepository().resolve(Constants.HEAD);
      return id.getName();
    } catch (Exception ex) {
      throw new YamlException("Error in getting the head commit to the git", ex, USER_ADMIN);
    }
  }

  private List<GitFileChange> getFilesCommited(String gitCommitId, GitOperationContext gitOperationContext) {
    GitConfig gitConfig = gitOperationContext.getGitConfig();
    try (Git git = openGit(
             new File(gitClientHelper.getRepoDirectory(gitOperationContext)), gitConfig.getDisableUserGitConfig())) {
      ObjectId commitId = ObjectId.fromString(gitCommitId);
      RevCommit currentCommitObject = null;
      try (RevWalk revWalk = new RevWalk(git.getRepository())) {
        currentCommitObject = revWalk.parseCommit(commitId);
      }

      if (currentCommitObject == null) {
        String repoURL = StringUtils.defaultIfBlank(gitOperationContext.getGitConfig().getRepoUrl(), "");
        throw new GitClientException(
            String.format("No commit was found with the commitId (%s) in git repo (%s)", gitCommitId, repoURL), USER,
            null);
      }

      RevCommit parentCommit = currentCommitObject.getParent(0);

      ObjectId newCommitHead = git.getRepository().resolve(currentCommitObject.getName() + "^{tree}");
      ObjectId oldCommitHead = null;
      if (parentCommit != null) {
        oldCommitHead = git.getRepository().resolve(parentCommit.getName() + "^{tree}");
      }
      List<DiffEntry> diffs = getDiffEntries(git.getRepository(), git, newCommitHead, oldCommitHead);
      return getGitFileChangesFromDiff(diffs, git.getRepository(), gitConfig.getAccountId());
    } catch (Exception ex) {
      log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception: ", ex);
      throw new YamlException("Error in getting the files commited to the git", USER_ADMIN);
    }
  }

  private List<GitFileChange> getGitFileChangesFromDiff(List<DiffEntry> diffs, Repository repository, String accountId)
      throws IOException {
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    for (DiffEntry entry : diffs) {
      ObjectId objectId;
      String filePath;
      if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
        filePath = entry.getOldPath();
        objectId = entry.getOldId().toObjectId();
      } else {
        filePath = entry.getNewPath();
        objectId = entry.getNewId().toObjectId();
      }
      ObjectLoader loader = repository.open(objectId);
      String content = new String(loader.getBytes(), Charset.forName("utf-8"));
      gitFileChanges.add(GitFileChange.Builder.aGitFileChange()
                             .withAccountId(accountId)
                             .withFilePath(filePath)
                             .withFileContent(content)
                             .withChangeType(gitClientHelper.getChangeType(entry.getChangeType()))
                             .withSyncFromGit(false)
                             .withChangeFromAnotherCommit(false)
                             .build());
    }
    return gitFileChanges;
  }

  @VisibleForTesting
  void applyGitAddCommand(GitOperationContext gitOperationContext, List<String> filesToAdd, Git git) {
    /*
    We do not need to specifically git add every added/modified file. git add . will take care
    of this
     */
    if (isNotEmpty(filesToAdd)) {
      try {
        git.add().addFilepattern(".").call();
      } catch (GitAPIException ex) {
        log.error(format("Error in add/modify git operation connectorId:[%s]", gitOperationContext.getGitConnectorId())
                + " Exception: ",
            ex);
        throw new YamlException(
            format("Error in add/modify git operation connectorId:[%s]", gitOperationContext.getGitConnectorId()),
            ADMIN_SRE);
      }
    }
  }

  /*
      We need to ensure creation/deletion in the same order as the gitFileChanges but we do not
      want to git add each file individually because that slows down things but deleting is easy
      since we can just do git delete root_folder if some entity is deleted. Therefore, git rm is
      done here and just creation of files is done here and git add . should be done after this
      method call.
  */
  @VisibleForTesting
  void applyChangeSetOnFileSystem(
      String repoDirectory, GitConfig gitConfig, GitCommitRequest gitCommitRequest, List<String> filesToAdd, Git git) {
    gitCommitRequest.getGitFileChanges().forEach(gitFileChange -> {
      String filePath = repoDirectory + PATH_DELIMITER + gitFileChange.getFilePath();
      File file = new File(filePath);
      final ChangeType changeType = gitFileChange.getChangeType();
      switch (changeType) {
        case ADD:
        case MODIFY:
          try {
            log.info(
                getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Adding git file " + gitFileChange.toString());
            FileUtils.forceMkdir(file.getParentFile());
            FileUtils.writeStringToFile(file, gitFileChange.getFileContent(), UTF_8);
            filesToAdd.add(gitFileChange.getFilePath());
          } catch (IOException ex) {
            log.error(
                getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception in adding/modifying file to git " + ex);
            throw new YamlException("IOException in ADD/MODIFY git operation", ADMIN);
          }
          break;
        case RENAME:
          log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Old path:[{}], new path: [{}]",
              gitFileChange.getOldFilePath(), gitFileChange.getFilePath());
          String oldFilePath = repoDirectory + PATH_DELIMITER + gitFileChange.getOldFilePath();
          String newFilePath = repoDirectory + PATH_DELIMITER + gitFileChange.getFilePath();

          File oldFile = new File(oldFilePath);
          File newFile = new File(newFilePath);

          if (oldFile.exists()) {
            try {
              Path path = Files.move(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
              filesToAdd.add(gitFileChange.getFilePath());
              git.rm().addFilepattern(gitFileChange.getOldFilePath()).call();
            } catch (IOException | GitAPIException e) {
              log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception in renaming file " + e);
              throw new YamlException(
                  format("Exception in renaming file [%s]->[%s]", oldFile.toPath(), newFile.toPath()), ADMIN_SRE);
            }
          } else {
            log.warn(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "File doesn't exist. path: [{}]",
                gitFileChange.getOldFilePath());
          }
          break;
        case DELETE:
          File fileToBeDeleted = new File(repoDirectory + PATH_DELIMITER + gitFileChange.getFilePath());
          if (fileToBeDeleted.exists()) {
            try {
              git.rm().addFilepattern(gitFileChange.getFilePath()).call();
            } catch (GitAPIException e) {
              log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception in deleting file " + e);
              throw new YamlException(
                  format("Exception in deleting file [%s]", gitFileChange.getFilePath()), ADMIN_SRE);
            }
            log.info(
                getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Deleting git file " + gitFileChange.toString());
          } else {
            log.warn(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "File already deleted. path: [{}]",
                gitFileChange.getFilePath());
          }
          break;
        default:
          unhandled(changeType);
      }
    });
  }

  @Override
  public GitFetchFilesResult fetchFilesBetweenCommits(GitConfig gitConfig, GitFilesBetweenCommitsRequest gitRequest) {
    String gitConnectorId = gitRequest.getGitConnectorId();

    validateRequiredArgsForFilesBetweenCommit(gitRequest.getOldCommitId(), gitRequest.getNewCommitId());

    File lockFile = gitClientHelper.getLockObject(gitConnectorId);
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        log.info(new StringBuilder(128)
                     .append(" Processing Git command: FILES_BETWEEN_COMMITS ")
                     .append("Account: ")
                     .append(gitConfig.getAccountId())
                     .append(", repo: ")
                     .append(gitConfig.getRepoUrl())
                     .append(", newCommitId: ")
                     .append(gitRequest.getNewCommitId())
                     .append(", oldCommitId: ")
                     .append(gitRequest.getOldCommitId())
                     .append(", gitConnectorId: ")
                     .append(gitRequest.getGitConnectorId())
                     .toString());

        gitClientHelper.createDirStructureForFileDownload(gitConfig, gitConnectorId);
        cloneRepoForFilePathCheckout(gitConfig, StringUtils.EMPTY, gitConnectorId);
        checkoutGivenCommitForAllPaths(gitRequest.getNewCommitId(), gitConfig, gitConnectorId);
        List<GitFile> gitFilesFromDiff;

        try (Git git = openGit(new File(gitClientHelper.getFileDownloadRepoDirectory(gitConfig, gitConnectorId)),
                 gitConfig.getDisableUserGitConfig())) {
          Repository repository = git.getRepository();

          ObjectId newCommitHead = repository.resolve(gitRequest.getNewCommitId() + "^{tree}");
          ObjectId oldCommitHead = repository.resolve(gitRequest.getOldCommitId() + "^{tree}");

          List<DiffEntry> diffs = getDiffEntries(repository, git, newCommitHead, oldCommitHead);
          gitFilesFromDiff = getGitFilesFromDiff(diffs, repository, gitConfig.getGitRepoType());

        } catch (IOException | GitAPIException ex) {
          log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception: ", ex);
          throw new YamlException("Error in getting commit diff", USER_ADMIN);
        }

        resetWorkingDir(gitConfig, gitRequest.getGitConnectorId());

        return GitFetchFilesResult.builder()
            .files(gitFilesFromDiff)
            .gitCommitResult(GitCommitResult.builder().commitId(gitRequest.getNewCommitId()).build())
            .build();

      } catch (WingsException e) {
        tryResetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception: ", e);
        throw new YamlException(new StringBuilder()
                                    .append("Failed while fetching files between commits ")
                                    .append("Account: ")
                                    .append(gitConfig.getAccountId())
                                    .append(", newCommitId: ")
                                    .append(gitRequest.getNewCommitId())
                                    .append(", oldCommitId: ")
                                    .append(gitRequest.getOldCommitId())
                                    .append(", gitConnectorId: ")
                                    .append(gitRequest.getGitConnectorId())
                                    .toString(),
            ADMIN_SRE);
      }
    }
  }

  private void logPossibleFileLockRelatedExceptions(Exception e) {
    if (e instanceof ClosedChannelException || e instanceof AsynchronousCloseException
        || e instanceof FileLockInterruptionException || e instanceof OverlappingFileLockException
        || e instanceof NonWritableChannelException) {
      log.error("Exception occurred while creating file lock", e);
    }
  }

  private List<DiffEntry> getDiffEntries(Repository repository, Git git, ObjectId newCommitHead, ObjectId oldCommitHead)
      throws IOException, GitAPIException {
    try (ObjectReader reader = repository.newObjectReader()) {
      CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
      oldTreeIter.reset(reader, oldCommitHead);
      CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
      newTreeIter.reset(reader, newCommitHead);

      return git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
    }
  }

  private void validateRequiredArgsForFilesBetweenCommit(String oldCommitId, String newCommitId) {
    if (isEmpty(oldCommitId)) {
      throw new YamlException("Old commit id can not be empty", USER_ADMIN);
    }

    if (isEmpty(newCommitId)) {
      throw new YamlException("New commit id can not be empty", USER_ADMIN);
    }
  }

  // use this method wrapped in inter process file lock to handle multiple delegate version
  private String checkoutFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha) {
    return checkoutFiles(gitConfig, gitRequest, shouldExportCommitSha, null);
  }

  private String checkoutFiles(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha, LogCallback logCallback) {
    synchronized (gitClientHelper.getLockObject(gitRequest.getGitConnectorId())) {
      defaultRepoTypeToYaml(gitConfig);

      log.info(new StringBuilder(128)
                   .append(" Processing Git command: FETCH_FILES ")
                   .append("Account: ")
                   .append(gitConfig.getAccountId())
                   .append(", repo: ")
                   .append(gitConfig.getRepoUrl())
                   .append(gitRequest.isUseBranch() ? ", Branch: " : ", CommitId: ")
                   .append(gitRequest.isUseBranch() ? gitRequest.getBranch() : gitRequest.getCommitId())
                   .append(", filePaths: ")
                   .append(gitRequest.getFilePaths())

                   .toString());

      String gitConnectorId = gitRequest.getGitConnectorId();
      // create repository/gitFilesDownload/<AccId>/<GitConnectorId> path
      gitClientHelper.createDirStructureForFileDownload(gitConfig, gitConnectorId);

      // clone repo locally without checkout
      String branch = gitRequest.isUseBranch() ? gitRequest.getBranch() : StringUtils.EMPTY;
      cloneRepoForFilePathCheckout(gitConfig, branch, gitConnectorId, logCallback);
      // if useBranch is set, use it to checkout latest, else checkout given commitId
      String latestCommitSha;
      if (gitRequest.isUseBranch()) {
        latestCommitSha = checkoutBranchForPath(
            gitRequest.getBranch(), gitRequest.getFilePaths(), gitConfig, gitConnectorId, shouldExportCommitSha);
      } else {
        checkoutGivenCommitForPath(gitRequest.getCommitId(), gitRequest.getFilePaths(), gitConfig, gitConnectorId);
        latestCommitSha = gitRequest.getCommitId();
      }
      return latestCommitSha;
    }
  }

  @Override
  public String downloadFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, String destinationDirectory,
      boolean shouldExportCommitSha) {
    return downloadFiles(gitConfig, gitRequest, destinationDirectory, shouldExportCommitSha, null);
  }

  @Override
  public String downloadFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, String destinationDirectory,
      boolean shouldExportCommitSha, LogCallback logCallback) {
    validateRequiredArgs(gitRequest, gitConfig);
    String gitConnectorId = gitRequest.getGitConnectorId();
    saveInfoExecutionLogs(logCallback, "Trying to start synchronized download files operation from git");
    File lockFile = gitClientHelper.getLockObject(gitConnectorId);
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        saveInfoExecutionLogs(logCallback, "Started synchronized download files operation from git");
        String latestCommitSha = checkoutFiles(gitConfig, gitRequest, shouldExportCommitSha, logCallback);
        String repoPath = gitClientHelper.getRepoPathForFileDownload(gitConfig, gitRequest.getGitConnectorId());

        FileIo.createDirectoryIfDoesNotExist(destinationDirectory);
        FileIo.waitForDirectoryToBeAccessibleOutOfProcess(destinationDirectory, 10);

        File destinationDir = new File(destinationDirectory);

        for (String filePath : gitRequest.getFilePaths()) {
          File sourceDir = new File(Paths.get(repoPath + "/" + filePath).toString());
          if (sourceDir.isFile()) {
            FileUtils.copyFile(sourceDir, Paths.get(destinationDirectory, filePath).toFile());
          } else {
            FileUtils.copyDirectory(sourceDir, destinationDir);
            // if source directory is repo root we don't want to have .git copied to destination directory
            File gitFile = new File(Paths.get(destinationDirectory, ".git").toString());
            if (gitFile.exists()) {
              FileUtils.deleteQuietly(gitFile);
            }
          }
        }

        resetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        return latestCommitSha;
      } catch (WingsException e) {
        tryResetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        throw new YamlException(
            new StringBuilder()
                .append("Failed while fetching files ")
                .append(gitRequest.isUseBranch() ? "for Branch: " : "for CommitId: ")
                .append(gitRequest.isUseBranch() ? gitRequest.getBranch() : gitRequest.getCommitId())
                .append(", FilePaths: ")
                .append(gitRequest.getFilePaths())
                .append(". Reason: ")
                .append(e.getMessage())
                .append(", ")
                .append(e.getCause() != null ? e.getCause().getMessage() : "")
                .toString(),
            e, USER);
      }
    }
  }

  private void saveInfoExecutionLogs(LogCallback logCallback, String message) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(message, INFO, RUNNING);
    }
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha) {
    return fetchFilesByPath(gitConfig, gitRequest, shouldExportCommitSha, null);
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha, LogCallback logCallback) {
    validateRequiredArgs(gitRequest, gitConfig);

    String gitConnectorId = gitRequest.getGitConnectorId();
    /*
     * ConnectorId is per gitConfig and will result in diff local path for repo
     * */
    saveInfoExecutionLogs(logCallback, "Trying to start synchronized fetch files operation from git");
    File lockFile = gitClientHelper.getLockObject(gitConnectorId);
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        saveInfoExecutionLogs(logCallback, "Started synchronized fetch files operation from git");
        String latestCommitSHA = checkoutFiles(gitConfig, gitRequest, shouldExportCommitSha, logCallback);
        GitFetchMetadataLocalThread.putCommitId(gitRequest.getIdentifier(), latestCommitSHA);

        String repoPath = gitClientHelper.getRepoPathForFileDownload(gitConfig, gitRequest.getGitConnectorId());
        List<GitFile> gitFiles = getFilteredGitFiles(gitConfig, gitRequest, repoPath);

        resetWorkingDir(gitConfig, gitRequest.getGitConnectorId());

        if (isNotEmpty(gitFiles)) {
          gitFiles.forEach(gitFile -> log.info("File fetched : " + gitFile.getFilePath()));
        }
        return GitFetchFilesResult.builder()
            .files(gitFiles)
            .gitCommitResult(GitCommitResult.builder()
                                 .commitId(gitRequest.isUseBranch() ? "latest" : gitRequest.getCommitId())
                                 .build())
            .latestCommitSHA(latestCommitSHA)
            .build();

      } catch (WingsException e) {
        tryResetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(gitConfig, gitRequest.getGitConnectorId());
        log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Exception: ", e);
        throw new YamlException(
            new StringBuilder()
                .append("Failed while fetching files ")
                .append(gitRequest.isUseBranch() ? "for Branch: " : "for CommitId: ")
                .append(gitRequest.isUseBranch() ? gitRequest.getBranch() : gitRequest.getCommitId())
                .append(", FilePaths: ")
                .append(gitRequest.getFilePaths())
                .append(". Reason: ")
                .append(e.getMessage())
                .append(", ")
                .append(e.getCause() != null ? e.getCause().getMessage() : "")
                .toString(),
            USER);
      }
    }
  }

  @VisibleForTesting
  public List<GitFile> getFilteredGitFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, String repoPath) {
    List<GitFile> gitFiles = new ArrayList<>();
    Set<String> uniqueFilePaths = new HashSet<>();
    gitRequest.getFilePaths().forEach(filePath -> {
      try {
        Path repoFilePath = Paths.get(repoPath + "/" + filePath);
        Stream<Path> paths = gitRequest.isRecursive() ? Files.walk(repoFilePath) : Files.walk(repoFilePath, 1);
        paths.filter(Files::isRegularFile)
            .filter(path -> !path.toString().contains(".git"))
            .filter(matchingFilesExtensions(gitRequest))
            .forEach(path -> gitClientHelper.addFiles(gitFiles, uniqueFilePaths, path, repoPath));
      } catch (Exception e) {
        resetWorkingDir(gitConfig, gitRequest.getGitConnectorId());

        // GitFetchFilesTask relies on the exception cause whether to fail the deployment or not.
        // If the exception is being changed, make sure that the throwable cause is added to the new exception
        // Unit test testGetFilteredGitFilesNoFileFoundException makes sure that the original exception is not swallowed
        throw new GitClientException(
            new StringBuilder("Unable to checkout files for filePath [")
                .append(filePath)
                .append("]")
                .append(gitRequest.isUseBranch() ? " for Branch: " : " for CommitId: ")
                .append(gitRequest.isUseBranch() ? gitRequest.getBranch() : gitRequest.getCommitId())
                .toString(),
            USER, e);
      }
    });

    return gitFiles;
  }

  private Predicate<Path> matchingFilesExtensions(GitFetchFilesRequest gitRequest) {
    return path -> {
      if (isEmpty(gitRequest.getFileExtensions())) {
        return true;
      } else {
        for (String fileExtension : gitRequest.getFileExtensions()) {
          if (path.toString().endsWith(fileExtension)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  private void validateRequiredArgs(GitFetchFilesRequest gitRequest, GitConfig gitConfig) {
    // FilePath cant empty as well as (Branch and commitId both cant be empty)
    if (isEmpty(gitRequest.getFilePaths())) {
      throw new InvalidRequestException("FilePaths  can not be empty", USER);
    }

    if (gitRequest.isUseBranch() && isEmpty(gitRequest.getBranch())) {
      throw new InvalidRequestException("useBranch was set but branch is not provided", USER);
    }

    if (!gitRequest.isUseBranch() && isEmpty(gitRequest.getCommitId())) {
      throw new InvalidRequestException("useBranch was false but CommitId was not provided", USER);
    }

    if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType() && isBlank(gitConfig.getRepoName())) {
      throw new InvalidRequestException("Repository name not provided for Account level git connector.", USER);
    }
  }

  private void checkoutGivenCommitForAllPaths(String commitId, GitConfig gitConfig, String gitConnectorId) {
    try (Git git = openGit(new File(gitClientHelper.getFileDownloadRepoDirectory(gitConfig, gitConnectorId)),
             gitConfig.getDisableUserGitConfig())) {
      log.info("Checking out commitId: " + commitId);
      CheckoutCommand checkoutCommand = git.checkout().setStartPoint(commitId).setCreateBranch(false).setAllPaths(true);

      checkoutCommand.call();
      log.info("Successfully Checked out commitId: " + commitId);
    } catch (Exception ex) {
      log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType())
          + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in checking out commit id " + commitId, USER);
    }
  }

  private void checkoutGivenCommitForPath(
      String commitId, List<String> filePaths, GitConfig gitConfig, String gitConnectorId) {
    try (Git git = openGit(new File(gitClientHelper.getFileDownloadRepoDirectory(gitConfig, gitConnectorId)),
             gitConfig.getDisableUserGitConfig())) {
      log.info("Checking out commitId: " + commitId);
      CheckoutCommand checkoutCommand = git.checkout().setStartPoint(commitId).setCreateBranch(false);

      setPathsForCheckout(filePaths, checkoutCommand);
      checkoutCommand.call();
      log.info("Successfully Checked out commitId: " + commitId);
    } catch (Exception ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in checking out commit id " + commitId, USER);
    }
  }

  private void setPathsForCheckout(List<String> filePaths, CheckoutCommand checkoutCommand) {
    if (filePaths.size() == 1 && filePaths.get(0).equals("")) {
      checkoutCommand.setAllPaths(true);
    } else {
      filePaths.forEach(checkoutCommand::addPath);
    }
  }

  private String checkoutBranchForPath(String branch, List<String> filePaths, GitConfig gitConfig,
      String gitConnectorId, boolean shouldExportCommitSha) {
    String latestCommitSha = null;
    try (Git git = openGit(new File(gitClientHelper.getFileDownloadRepoDirectory(gitConfig, gitConnectorId)),
             gitConfig.getDisableUserGitConfig())) {
      log.info("Checking out Branch: " + branch);
      CheckoutCommand checkoutCommand = git.checkout()
                                            .setCreateBranch(true)
                                            .setStartPoint("origin/" + branch)
                                            .setForce(true)
                                            .setUpstreamMode(SetupUpstreamMode.TRACK)
                                            .setName(branch);
      setPathsForCheckout(filePaths, checkoutCommand);
      checkoutCommand.call();

      if (isNotEmpty(branch) && shouldExportCommitSha) {
        ObjectId branchObjectId = git.getRepository().resolve("origin/" + branch).toObjectId();
        Iterator<RevCommit> revCommitIterator = git.log().add(branchObjectId).setMaxCount(1).call().iterator();
        RevCommit revCommit = revCommitIterator.next();
        if (revCommit != null) {
          latestCommitSha = revCommit.toString().split(" ")[1];
        }
      }

      log.info("Successfully Checked out Branch: " + branch);
      return latestCommitSha;

    } catch (Exception ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in checking out Branch " + branch, USER);
    }
  }

  private void tryResetWorkingDir(GitConfig gitConfig, String gitConnectorId) {
    try {
      resetWorkingDir(gitConfig, gitConnectorId);
    } catch (Exception ex) {
      log.info("Not able to reset repository", ex);
    }
  }

  private void resetWorkingDir(GitConfig gitConfig, String gitConnectorId) {
    try (Git git = openGit(new File(gitClientHelper.getFileDownloadRepoDirectory(gitConfig, gitConnectorId)),
             gitConfig.getDisableUserGitConfig())) {
      log.info("Resetting repo");
      ResetCommand resetCommand = new ResetCommand(git.getRepository()).setMode(ResetType.HARD);
      resetCommand.call();
      log.info("Resetting repo completed successfully");
    } catch (Exception ex) {
      log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType())
          + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in resetting repo", USER);
    }
  }

  @Override
  public String validate(GitConfig gitConfig) {
    String repoUrl = gitConfig.getRepoUrl();
    try {
      // Init Git repo
      LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository();
      lsRemoteCommand = (LsRemoteCommand) getAuthConfiguredCommand(lsRemoteCommand, gitConfig);
      Collection<Ref> refs = lsRemoteCommand.setRemote(repoUrl).setHeads(true).setTags(true).call();
      log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Remote branches found, validation success.");
    } catch (Exception e) {
      log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Git validation failed [{}]", e.getMessage());

      if (e instanceof InvalidRemoteException || e.getCause() instanceof NoRemoteRepositoryException) {
        return "Invalid git repo " + repoUrl;
      }

      if (e instanceof org.eclipse.jgit.api.errors.TransportException) {
        org.eclipse.jgit.api.errors.TransportException te = (org.eclipse.jgit.api.errors.TransportException) e;
        Throwable cause = te.getCause();
        if (cause instanceof TransportException) {
          TransportException tee = (TransportException) cause;
          if (tee.getCause() instanceof UnknownHostException) {
            return UNREACHABLE_HOST.getDescription() + repoUrl;
          }
        }
      }
      // Any generic error
      return getMessage(e);
    }
    return null; // no error
  }

  /**
   * Ensure repo locally cloned. This is called before performing any git operation with remote
   *
   * @param gitConfig the git config
   */
  private synchronized void cloneRepoForFilePathCheckout(GitConfig gitConfig, String branch, String connectorId) {
    cloneRepoForFilePathCheckout(gitConfig, branch, connectorId, null);
  }

  /**
   * Ensure repo locally cloned. This is called before performing any git operation with remote
   *
   * @param gitConfig the git config
   */
  private synchronized void cloneRepoForFilePathCheckout(
      GitConfig gitConfig, String branch, String connectorId, LogCallback logCallback) {
    log.info(new StringBuilder(64)
                 .append(getGitLogMessagePrefix(gitConfig.getGitRepoType()))
                 .append("Cloning repo without checkout for file fetch op, for GitConfig: ")
                 .append(gitConfig.toString())
                 .toString());

    boolean exceptionOccured = false;
    File repoDir = new File(gitClientHelper.getFileDownloadRepoDirectory(gitConfig, connectorId));
    // If repo already exists, update references
    if (repoDir.exists()) {
      // Check URL change (ssh, https) and update in .git/config
      updateRemoteOriginInConfig(gitConfig, repoDir);

      try (Git git = openGit(repoDir, gitConfig.getDisableUserGitConfig())) {
        // update ref with latest commits on remote
        FetchResult fetchResult = performGitFetchOperation(gitConfig, logCallback, git); // fetch all remote references

        log.info(new StringBuilder()
                     .append(getGitLogMessagePrefix(gitConfig.getGitRepoType()))
                     .append("result fetched: ")
                     .append(fetchResult.toString())
                     .toString());

        return;
      } catch (Exception ex) {
        exceptionOccured = true;
        if (ex instanceof IOException) {
          log.warn(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Repo doesn't exist locally [repo: {}], {} ",
              gitConfig.getRepoUrl(), ex);
          log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Do a fresh clone");
        } else {
          log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Hard reset failed for branch [{}]",
              gitConfig.getBranch());
          log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType())
              + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
          gitClientHelper.checkIfGitConnectivityIssue(ex);
        }
      } finally {
        if (exceptionOccured) {
          gitClientHelper.releaseLock(gitConfig, gitClientHelper.getRepoPathForFileDownload(gitConfig, connectorId));
        }
      }
    }

    clone(gitConfig, gitClientHelper.getFileDownloadRepoDirectory(gitConfig, connectorId), branch, true);
  }

  private FetchResult performGitFetchOperation(GitConfig gitConfig, LogCallback logCallback, Git git)
      throws GitAPIException {
    FetchResult fetchResult;

    saveInfoExecutionLogs(logCallback, "Waiting on git fetch operation to be completed.");
    fetchResult = ((FetchCommand) (getAuthConfiguredCommand(git.fetch(), gitConfig)))
                      .setRemoveDeletedRefs(true)
                      .setTagOpt(TagOpt.FETCH_TAGS)
                      .call();
    saveInfoExecutionLogs(logCallback, "Git fetch operation has been completed.");
    return fetchResult;
  }

  public void cloneRepoAndCopyToDestDir(
      GitOperationContext gitOperationContext, String destinationDir, LogCallback logCallback) {
    String gitConnectorId = gitOperationContext.getGitConnectorId();
    saveInfoExecutionLogs(logCallback, "Trying to start synchronized git clone and copy to destination directory");
    File lockFile = gitClientHelper.getLockObject(gitConnectorId);
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        saveInfoExecutionLogs(
            logCallback, "Started synchronized operation: Cloning repo from git and copying to destination directory");
        ensureRepoLocallyClonedAndUpdated(gitOperationContext);
        File dest = new File(destinationDir);
        File src = new File(gitClientHelper.getRepoDirectory(gitOperationContext));
        deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
        FileUtils.copyDirectory(src, dest);
        FileIo.waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
      } catch (WingsException e) {
        tryResetWorkingDir(gitOperationContext.getGitConfig(), gitOperationContext.getGitConnectorId());
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(gitOperationContext.getGitConfig(), gitOperationContext.getGitConnectorId());
        throw new YamlException("Error in cloning and copying files to provisioner specific directory", e, USER);
      }
    }
  }

  @Override
  public synchronized void ensureRepoLocallyClonedAndUpdated(GitOperationContext gitOperationContext) {
    GitConfig gitConfig = gitOperationContext.getGitConfig();

    File repoDir = new File(gitClientHelper.getRepoDirectory(gitOperationContext));
    boolean executionFailed = false;
    if (repoDir.exists()) {
      // Check URL change (ssh, https) and update in .git/config
      updateRemoteOriginInConfig(gitConfig, repoDir);

      try (Git git = openGit(repoDir, gitConfig.getDisableUserGitConfig())) {
        log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Repo exist. do hard sync with remote branch");

        FetchResult fetchResult = ((FetchCommand) (getAuthConfiguredCommand(git.fetch(), gitConfig)))
                                      .setTagOpt(TagOpt.FETCH_TAGS)
                                      .call(); // fetch all remote references
        checkout(gitOperationContext);

        // Do not sync to the HEAD of the branch if a specific commit SHA is provided
        if (StringUtils.isEmpty(gitConfig.getReference())) {
          Ref ref = git.reset().setMode(ResetType.HARD).setRef("refs/remotes/origin/" + gitConfig.getBranch()).call();
        }
        log.info(
            getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Hard reset done for branch " + gitConfig.getBranch());
        // TODO:: log failed commits queued and being ignored.
        return;
      } catch (Exception ex) {
        executionFailed = true;
        if (ex instanceof IOException) {
          log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Repo doesn't exist locally [repo: {}], {} ",
              gitConfig.getRepoUrl(), ex);
        } else {
          if (ex instanceof GitAPIException) {
            log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Hard reset failed for branch [{}]",
                gitConfig.getBranch());
            log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType())
                + "Exception: " + ExceptionSanitizer.sanitizeForLogging(ex));
            gitClientHelper.checkIfGitConnectivityIssue(ex);
          }
        }
      } finally {
        if (executionFailed) {
          // ensureRepoLocallyClonedAndUpdated is called before any git op (commitAndPush, diff)
          // This is synchronized on this singleton class object. So if we are inside in this method, there is
          // no other method inside this one at the same time. Also all callers are synchronized as well.
          // Means if we fail due to existing index.lock it has to be orphan lock file
          // and needs to be deleted.
          gitClientHelper.releaseLock(gitConfig, gitClientHelper.getRepoDirectory(gitOperationContext));
        }
      }
    }

    // We are here, so either repo doesnt exist or we encounter some error while
    // opening/updating repo
    log.info(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + "Do a fresh clone");
    clone(gitConfig, gitClientHelper.getRepoDirectory(gitOperationContext), gitConfig.getBranch(), false);
    try {
      checkout(gitOperationContext);
    } catch (IOException | GitAPIException ex) {
      log.error(getGitLogMessagePrefix(gitConfig.getGitRepoType()) + EXCEPTION_STRING, ex);
      throw new YamlException(format("Unable to checkout given reference: %s",
                                  isEmpty(gitOperationContext.getGitConfig().getReference())
                                      ? gitOperationContext.getGitConfig().getBranch()
                                      : gitOperationContext.getGitConfig().getReference()),
          ex, USER);
    }
  }

  protected String getGitLogMessagePrefix(GitRepositoryType repositoryType) {
    if (repositoryType == null) {
      return GIT_DEFAULT_LOG_PREFIX;
    }

    switch (repositoryType) {
      case TERRAFORM:
        return GIT_TERRAFORM_LOG_PREFIX;

      case YAML:
        return GIT_YAML_LOG_PREFIX;

      case TRIGGER:
        return GIT_TRIGGER_LOG_PREFIX;

      case HELM:
        return GIT_HELM_LOG_PREFIX;

      case TERRAGRUNT:
        return GIT_TERRAGRUNT_LOG_PREFIX;
      default:
        unhandled(repositoryType);
        return GIT_DEFAULT_LOG_PREFIX;
    }
  }

  private TransportCommand getAuthConfiguredCommand(TransportCommand gitCommand, GitConfig gitConfig) {
    if (gitConfig.getRepoUrl().toLowerCase().startsWith("http")) {
      configureHttpCredentialProvider(gitCommand, gitConfig);
      gitCommand.setTransportConfigCallback(transport -> {
        if (transport instanceof TransportHttp) {
          TransportHttp http = (TransportHttp) transport;
          // Without proper timeout socket can get hang (ref: java.net.SocketInputStream.socketRead0) indefinitely
          // during packet loss. In some scenarios even if connection is established back this may still remain stuck.
          // Since socketRead0 ignores the thread interruptions, the original task thread will remain in running state
          // forever. As all of our operations are synchronized stuck thread will block other git tasks to execute
          // This timeout is used for setting connection and read timeout based on current implementation. A better
          // option for further improvements is to have a custom connection factory where will use a more granular
          // configuration of these timeouts parameters
          http.setTimeout(SOCKET_CONNECTION_READ_TIMEOUT_SECONDS);
          http.setHttpConnectionFactory(connectionFactory);
        }
      });
    } else {
      gitCommand.setTransportConfigCallback(transport -> {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(getSshSessionFactory(gitConfig.getSshSettingAttribute()));
      });
    }
    return gitCommand;
  }

  private void configureHttpCredentialProvider(TransportCommand gitCommand, GitConfig gitConfig) {
    String username = gitConfig.getUsername();
    char[] password = gitConfig.getPassword();
    if (KERBEROS == gitConfig.getAuthenticationScheme()) {
      addApacheConnectionFactoryAndGenerateTGT(gitConfig);
      username = ((HostConnectionAttributes) gitConfig.getSshSettingAttribute().getValue())
                     .getKerberosConfig()
                     .getPrincipal(); // set principal as username
    }
    if (HTTP_PASSWORD == gitConfig.getAuthenticationScheme()) {
      if (EmptyPredicate.isEmpty(username)) {
        log.info(String.format("The user name is null in the git config. Account id: %s", gitConfig.getAccountId()));
      }
      if (EmptyPredicate.isEmpty(password)) {
        log.info(String.format("The password is null in the git config. Account id: %s", gitConfig.getAccountId()));
      }
    }
    gitCommand.setCredentialsProvider(new UsernamePasswordCredentialsProviderWithSkipSslVerify(username, password));
  }

  private void addApacheConnectionFactoryAndGenerateTGT(GitConfig gitConfig) {
    try {
      HttpTransport.setConnectionFactory(new ApacheHttpConnectionFactory());
      URL url = new URL(gitConfig.getRepoUrl());
      SshSessionConfig sshSessionConfig = createSshSessionConfig(gitConfig.getSshSettingAttribute(), url.getHost());
      generateTGTUsingSshConfig(sshSessionConfig, new NoopExecutionCallback());
    } catch (Exception e) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception while setting kerberos auth for repo: [{}] with ex: [{}]",
          gitConfig.getRepoUrl(), getMessage(e));
      throw new InvalidRequestException("Failed to do Kerberos authentication");
    }
  }

  private SshSessionFactory getSshSessionFactory(SettingAttribute settingAttribute) {
    return new JschConfigSessionFactory() {
      @Override
      protected Session createSession(Host hc, String user, String host, int port, FS fs) throws JSchException {
        SshSessionConfig sshSessionConfig = createSshSessionConfig(settingAttribute, host);
        sshSessionConfig.setPort(port); // use port from repo URL
        if (sshSessionConfig.isUseSshClient() || sshSessionConfig.isVaultSSH()) {
          return ((JschConnection) SshFactory.getSshClient(sshSessionConfig).getConnection()).getSession();
        } else {
          return getSSHSession(sshSessionConfig);
        }
      }

      @Override
      protected void configure(Host hc, Session session) {}
    };
  }

  private void defaultRepoTypeToYaml(GitConfig gitConfig) {
    if (gitConfig.getGitRepoType() == null) {
      gitConfig.setGitRepoType(GitRepositoryType.YAML);
    }
  }

  private Git openGit(File repoDir, Boolean disableUserConfig) throws IOException {
    if (disableUserConfig != null && disableUserConfig) {
      SystemReader.setInstance(new CustomUserGitConfigSystemReader(null));
    } else {
      SystemReader.setInstance(null);
    }
    return Git.open(repoDir);
  }

  public static class ApacheHttpConnectionFactory implements HttpConnectionFactory {
    @Override
    public HttpConnection create(URL url) throws IOException {
      return create(url, null);
    }

    @Override
    public HttpConnection create(URL url, Proxy proxy) throws IOException {
      HttpConnection connection = new HttpClientConnectionFactory().create(url, proxy);
      HttpSupport.disableSslVerify(connection);
      return connection;
    }
  }
}
