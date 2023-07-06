/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.UNREACHABLE_HOST;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.ADMIN;
import static io.harness.exception.WingsException.ADMIN_SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.git.Constants.COMMIT_MESSAGE;
import static io.harness.git.Constants.DEFAULT_FETCH_IDENTIFIER;
import static io.harness.git.Constants.EXCEPTION_STRING;
import static io.harness.git.Constants.GIT_YAML_LOG_PREFIX;
import static io.harness.git.Constants.HARNESS_IO_KEY_;
import static io.harness.git.Constants.HARNESS_SUPPORT_EMAIL_KEY;
import static io.harness.git.Constants.PATH_DELIMITER;
import static io.harness.git.model.PushResultGit.pushResultBuilder;
import static io.harness.govern.Switch.unhandled;
import static io.harness.validation.Validator.notEmptyCheck;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GeneralException;
import io.harness.exception.GitClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.exception.runtime.JGitRuntimeException;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.git.model.AuthInfo;
import io.harness.git.model.ChangeType;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.CommitResult;
import io.harness.git.model.DiffRequest;
import io.harness.git.model.DiffResult;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.FetchFilesBwCommitsRequest;
import io.harness.git.model.FetchFilesByPathRequest;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitFile;
import io.harness.git.model.GitFileChange;
import io.harness.git.model.GitRepositoryType;
import io.harness.git.model.JgitSshAuthRequest;
import io.harness.git.model.ListRemoteRequest;
import io.harness.git.model.ListRemoteResult;
import io.harness.git.model.PushRequest;
import io.harness.git.model.PushResultGit;
import io.harness.git.model.RevertAndPushRequest;
import io.harness.git.model.RevertAndPushResult;
import io.harness.git.model.RevertRequest;

import software.wings.misc.CustomUserGitConfigSystemReader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
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
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.SystemReader;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class GitClientV2Impl implements GitClientV2 {
  private static final int GIT_COMMAND_RETRY = 3;
  private static final String UPLOAD_PACK_ERROR = "git-upload-pack";
  private static final String INVALID_ADVERTISEMENT_ERROR = "invalid advertisement of";
  private static final String REDIRECTION_BLOCKED_ERROR = "Redirection blocked";
  private static final String TIMEOUT_ERROR = "Connection time out";
  private static final int SOCKET_CONNECTION_READ_TIMEOUT_SECONDS = 60;
  private static final String REFS_HEADS = "refs/heads/";
  private static final String ORIGIN = "origin/";

  @Inject private GitClientHelper gitClientHelper;
  /**
   * factory for creating HTTP connections. By default, JGit uses JDKHttpConnectionFactory which doesn't work well with
   * proxy. See:
   * https://stackoverflow.com/questions/67492788/eclipse-egit-tfs-git-connection-authentication-not-supported
   */
  public static final HttpConnectionFactory connectionFactory = new HttpClientConnectionFactory();

  private void cleanup(GitBaseRequest request) {
    if (request.getRepoType() == null) {
      log.error("gitRepoType can not be null. defaulting it to YAML");
      request.setRepoType(GitRepositoryType.YAML);
    }
  }

  /**
   * Note: Checkout is added after clone as clone doesn't support throwing errors in case branch is not available
   *
   * @param request GitBaseRequest
   */
  @Override
  public synchronized void ensureRepoLocallyClonedAndUpdated(GitBaseRequest request) {
    notNullCheck("Repo update request cannot be null", request);
    cleanup(request);
    File repoDir = new File(gitClientHelper.getRepoDirectory(request));
    boolean executionFailed = false;
    if (repoDir.exists()) {
      // Check URL change (ssh, https) and update in .git/config
      updateRemoteOriginInConfig(request.getRepoUrl(), repoDir, request.getDisableUserGitConfig());
      try (Git git = openGit(repoDir, request.getDisableUserGitConfig())) {
        log.info(gitClientHelper.getGitLogMessagePrefix(request.getRepoType())
            + "Repo exist. do hard sync with remote branch");
        printCommitId(request, git);

        ((FetchCommand) (getAuthConfiguredCommand(git.fetch(), request))).setTagOpt(TagOpt.FETCH_TAGS).call();
        checkout(request);

        // Do not sync to the HEAD of the branch if a specific commit SHA is provided
        if (StringUtils.isEmpty(request.getCommitId())) {
          git.reset().setMode(ResetCommand.ResetType.HARD).setRef("refs/remotes/origin/" + request.getBranch()).call();
        }
        log.info(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Hard reset done for branch "
            + request.getBranch());
        printCommitId(request, git);
        // TODO:: log failed commits queued and being ignored.
        return;
      } catch (Exception ex) {
        executionFailed = true;
        if (ex instanceof IOException) {
          log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType())
                  + "Repo doesn't exist locally [repo: {}], {} ",
              request.getRepoUrl(), ex);
        } else {
          if (ex instanceof GitAPIException) {
            log.info(
                gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Hard reset failed for branch [{}]",
                request.getBranch());
            log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING
                + ExceptionSanitizer.sanitizeForLogging(ex));
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
          gitClientHelper.releaseLock(request, gitClientHelper.getRepoDirectory(request));
        }
      }
    }

    // We are here, so either repo doesn't exist or we encounter some error while
    // opening/updating repo
    log.info(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Do a fresh clone");
    clone(request, gitClientHelper.getRepoDirectory(request), false);
    try {
      checkout(request);
    } catch (IOException | GitAPIException ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING, ex);
      throw new YamlException(format("Unable to checkout given reference: %s",
                                  isEmpty(request.getCommitId()) ? request.getBranch() : request.getCommitId()),
          ex, USER);
    }
  }

  private void printCommitId(GitBaseRequest request, Git git) {
    try {
      log.info("{}Commit id: {}", gitClientHelper.getGitLogMessagePrefix(request.getRepoType()), getHeadCommit(git));
    } catch (Exception e) {
      log.warn("{}Failed to get last commit: {}", gitClientHelper.getGitLogMessagePrefix(request.getRepoType()),
          e.getMessage());
    }
  }

  @VisibleForTesting
  synchronized void clone(GitBaseRequest request, String gitRepoDirectory, boolean noCheckout) {
    try {
      if (new File(gitRepoDirectory).exists()) {
        deleteDirectoryAndItsContentIfExists(gitRepoDirectory);
      }
    } catch (IOException ioex) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception while deleting repo: ", getMessage(ioex));
    }
    log.info(GIT_YAML_LOG_PREFIX + "cloning repo, Git repo directory :{}", gitRepoDirectory);

    CloneCommand cloneCommand = (CloneCommand) getAuthConfiguredCommand(Git.cloneRepository(), request);
    cloneCommand.setURI(request.getRepoUrl()).setDirectory(new File(gitRepoDirectory));
    if (!request.isUnsureOrNonExistentBranch()) {
      cloneCommand
          .setBranch(isEmpty(request.getBranch()) ? null : request.getBranch())
          // if set to <code>true</code> no branch will be checked out, after the clone.
          // This enhances performance of the clone command when there is no need for a checked out branch.
          .setNoCheckout(noCheckout);
    } else {
      ListRemoteRequest listRemoteRequest = buildListRemoteRequestFromGitBaseRequest(request);
      ListRemoteResult listRemoteResult = listRemote(listRemoteRequest);
      Map<String, String> refs = listRemoteResult.getRemoteList();
      if (refs.containsKey(REFS_HEADS + request.getBranch())) {
        cloneCommand.setBranch(isEmpty(request.getBranch()) ? null : request.getBranch());
        cloneCommand.setNoCheckout(noCheckout);
      } else {
        String branchToClone = refs.get("HEAD");
        cloneCommand.setBranch(branchToClone);
        cloneCommand.setBranchesToClone(Collections.singleton(branchToClone));
        cloneCommand.setNoCheckout(true);
      }
    }
    try (Git git = cloneCommand.call()) {
    } catch (GitAPIException ex) {
      log.error(GIT_YAML_LOG_PREFIX + "Error in cloning repo: " + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in cloning repo", USER);
    }
  }

  private synchronized void checkout(GitBaseRequest request) throws IOException, GitAPIException {
    Git git = openGit(new File(gitClientHelper.getRepoDirectory(request)), request.getDisableUserGitConfig());
    try {
      if (isNotEmpty(request.getBranch())) {
        CheckoutCommand checkoutCommand = git.checkout();
        checkoutCommand.setCreateBranch(true).setName(request.getBranch());
        if (!request.isUnsureOrNonExistentBranch()) {
          checkoutCommand.setUpstreamMode(SetupUpstreamMode.TRACK).setStartPoint(ORIGIN + request.getBranch());
        } else {
          ListRemoteRequest listRemoteRequest = buildListRemoteRequestFromGitBaseRequest(request);
          ListRemoteResult listRemoteResult = listRemote(listRemoteRequest);
          Map<String, String> refs = listRemoteResult.getRemoteList();
          if (refs.containsKey(REFS_HEADS + request.getBranch())) {
            checkoutCommand.setUpstreamMode(SetupUpstreamMode.TRACK).setStartPoint(ORIGIN + request.getBranch());
          } else {
            String branchToClone = refs.get("HEAD");
            checkoutCommand.setUpstreamMode(SetupUpstreamMode.TRACK)
                .setStartPoint(ORIGIN + branchToClone.replace(REFS_HEADS, ""));
          }
        }
        checkoutCommand.call();
      }

    } catch (RefAlreadyExistsException refExIgnored) {
      log.info(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Reference already exist do nothing.");
      // TODO:: check gracefully instead of relying on Exception
    }

    String gitRef = request.getCommitId() != null ? request.getCommitId() : request.getBranch();
    if (StringUtils.isNotEmpty(gitRef)) {
      git.checkout().setName(gitRef).call();
    }
  }

  @VisibleForTesting
  void updateRemoteOriginInConfig(String repoUrl, File gitRepoDirectory, Boolean clearUserGitConfig) {
    try (Git git = openGit(gitRepoDirectory, clearUserGitConfig)) {
      StoredConfig config = git.getRepository().getConfig();
      // Update local remote url if its changed
      if (!repoUrl.equals(config.getString("remote", "origin", "url"))) {
        config.setString("remote", "origin", "url", repoUrl);
        config.save();
        log.info(GIT_YAML_LOG_PREFIX + "Local repo remote origin is updated to : {}", repoUrl);
      }
    } catch (IOException ioex) {
      log.error(GIT_YAML_LOG_PREFIX + "Failed to update repo url in git config", ioex);
    }
  }

  @Override
  public String validate(GitBaseRequest request) {
    notNullCheck("Validate request cannot be null", request);
    cleanup(request);
    notEmptyCheck("url cannot be empty", request.getRepoUrl());
    String repoUrl = request.getRepoUrl();
    try {
      // Init Git repo
      LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository();
      lsRemoteCommand = (LsRemoteCommand) getAuthConfiguredCommand(lsRemoteCommand, request);
      Collection<Ref> refs = lsRemoteCommand.setRemote(repoUrl).setHeads(true).setTags(true).call();
      log.info(
          gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Remote branches found, validation success.");
    } catch (Exception e) {
      log.info(
          gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Git validation failed [{}]", e.getMessage());

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

  @Override
  public void validateOrThrow(GitBaseRequest request) {
    notNullCheck("Validate request cannot be null", request);
    cleanup(request);
    notEmptyCheck("url cannot be empty", request.getRepoUrl());
    String repoUrl = request.getRepoUrl();

    try {
      // Init Git repo
      LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository();
      lsRemoteCommand = (LsRemoteCommand) getAuthConfiguredCommand(lsRemoteCommand, request);
      RetryPolicy<Object> retryPolicy = getRetryPolicyForCommand(format("[Retrying failed git validation, attempt: {}"),
          format("Git validation failed after retrying {} times"));
      lsRemoteCommand.setRemote(repoUrl).setHeads(true).setTags(true);
      final LsRemoteCommand finalLsRemoteCommand = lsRemoteCommand;
      Failsafe.with(retryPolicy).get(() -> finalLsRemoteCommand.call());
      log.info(
          gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Remote branches found, validation success.");
    } catch (Exception e) {
      log.error(
          gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Git validation failed [{}]", e.getMessage());
      if (e instanceof GitAPIException) {
        throw new JGitRuntimeException(e.getMessage(), e);
      } else if (e instanceof FailsafeException) {
        String message = e.getMessage();
        if (message.contains("not authorized")) {
          throw SCMRuntimeException.builder()
              .message("Please check your credentials (potential token expiration issue)")
              .errorCode(ErrorCode.SCM_UNAUTHORIZED)
              .build();
        } else if (containsUrlError(message)) {
          throw SCMRuntimeException.builder()
              .message("Couldn't connect to given repo")
              .errorCode(ErrorCode.GIT_CONNECTION_ERROR)
              .build();
        } else if (message.contains(TIMEOUT_ERROR)) {
          throw SCMRuntimeException.builder()
              .message("Git connection timed out")
              .errorCode(ErrorCode.CONNECTION_TIMEOUT)
              .build();
        }
      }
      throw new GeneralException(e.getMessage(), e);
    }
  }

  private boolean containsUrlError(String message) {
    if (message.contains(UPLOAD_PACK_ERROR) || message.contains(INVALID_ADVERTISEMENT_ERROR)
        || message.contains(REDIRECTION_BLOCKED_ERROR)) {
      return true;
    }
    return false;
  }

  private RetryPolicy<Object> getRetryPolicyForCommand(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withMaxAttempts(GIT_COMMAND_RETRY)
        .withBackoff(5, 10, ChronoUnit.SECONDS)
        .onFailedAttempt(event -> {
          if (event.getLastFailure() instanceof TransportException) {
            log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure().getMessage());
          } else {
            log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure());
          }
        })
        .onFailure(event -> {
          if (event.getFailure() instanceof TransportException) {
            log.info(failureMessage, event.getAttemptCount(), event.getFailure().getMessage());
          } else {
            log.info(failureMessage, event.getAttemptCount(), event.getFailure());
          }
        });
  }
  @Override
  public DiffResult diff(DiffRequest request) {
    String startCommitIdStr = request.getLastProcessedCommitId();
    final String endCommitIdStr = StringUtils.defaultIfEmpty(request.getEndCommitId(), "HEAD");

    ensureRepoLocallyClonedAndUpdated(request);

    DiffResult diffResult = DiffResult.builder()
                                .branch(request.getBranch())
                                .repoName(request.getRepoUrl())
                                .accountId(request.getAccountId())
                                .build();
    try (Git git = openGit(new File(gitClientHelper.getRepoDirectory(request)), request.getDisableUserGitConfig())) {
      git.checkout().setName(request.getBranch()).call();
      performGitPull(request, git);
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
      final boolean commitsInOrder = ensureCommitOrdering(startCommitIdStr, endCommitIdStr, repository);
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
        addToGitDiffResult(diffs, diffResult, endCommitId, request.getAccountId(), repository,
            request.isExcludeFilesOutsideSetupFolder(), diffResult.getCommitTimeMs(),
            getTruncatedCommitMessage(diffResult.getCommitMessage()));
      }

    } catch (IOException | GitAPIException ex) {
      log.error(GIT_YAML_LOG_PREFIX + EXCEPTION_STRING + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in getting commit diff", ADMIN_SRE);
    }
    return diffResult;
  }

  @VisibleForTesting
  void performGitPull(DiffRequest request, Git git) throws GitAPIException {
    ((PullCommand) (getAuthConfiguredCommand(git.pull(), request))).call();
  }

  private String getTruncatedCommitMessage(String commitMessage) {
    if (isBlank(commitMessage)) {
      return commitMessage;
    }
    return commitMessage.substring(0, Math.min(commitMessage.length(), 500));
  }

  @VisibleForTesting
  void addToGitDiffResult(List<DiffEntry> diffs, DiffResult diffResult, ObjectId headCommitId, String accountId,
      Repository repository, boolean excludeFilesOutsideSetupFolder, Long commitTimeMs, String commitMessage)
      throws IOException {
    log.info(GIT_YAML_LOG_PREFIX + "Diff Entries: {}", diffs);
    ArrayList<GitFileChange> gitFileChanges = new ArrayList<>();
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

      if (excludeFilesOutsideSetupFolder && filePath != null && !filePath.startsWith("SETUP_FOLDER")) {
        log.info("Excluding file [{}] ", filePath);
        continue;
      }

      ObjectLoader loader = repository.open(objectId);
      content = new String(loader.getBytes(), UTF_8);
      GitFileChange gitFileChange = GitFileChange.builder()
                                        .commitId(headCommitId.getName())
                                        .changeType(gitClientHelper.getChangeType(entry.getChangeType()))
                                        .filePath(filePath)
                                        .fileContent(content)
                                        .objectId(objectId.name())
                                        .accountId(accountId)
                                        .commitTimeMs(commitTimeMs)
                                        .commitMessage(commitMessage)
                                        .build();

      gitFileChanges.add(gitFileChange);
    }
    diffResult.setGitFileChanges(gitFileChanges);
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

  @VisibleForTesting
  boolean ensureCommitOrdering(String startCommitIdStr, String endCommitIdStr, Repository repository)
      throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      final RevCommit startCommit = revWalk.parseCommit(repository.resolve(startCommitIdStr));
      final RevCommit endCommit = revWalk.parseCommit(repository.resolve(endCommitIdStr));
      return endCommit.getCommitTime() >= startCommit.getCommitTime();
    }
  }

  @Override
  public synchronized CommitAndPushResult commitAndPush(CommitAndPushRequest commitAndPushRequest) {
    CommitResult commitResult = commit(commitAndPushRequest);
    CommitAndPushResult gitCommitAndPushResult = CommitAndPushResult.builder().gitCommitResult(commitResult).build();
    if (isNotBlank(commitResult.getCommitId())) {
      gitCommitAndPushResult.setGitPushResult(push(commitAndPushRequest));
      List<GitFileChange> gitFileChanges = getFilesCommited(commitResult.getCommitId(), commitAndPushRequest);
      gitCommitAndPushResult.setFilesCommittedToGit(gitFileChanges);
    } else {
      log.warn(gitClientHelper.getGitLogMessagePrefix(commitAndPushRequest.getRepoType())
              + "Null commitId. Nothing to push for request [{}]",
          commitAndPushRequest);
    }
    return gitCommitAndPushResult;
  }

  /**
   * Used to revert a commit and push to a target branch
   *
   * @param request RevertAndPushRequest
   * @return result RevertAndPushResult
   */
  @Override
  public RevertAndPushResult revertAndPush(RevertAndPushRequest request) {
    RevertRequest revertRequest = RevertRequest.mapFromRevertAndPushRequest(request);
    CommitResult commitResult = revert(revertRequest);

    PushRequest pushRequest = PushRequest.mapFromRevertAndPushRequest(request);
    RevertAndPushResult revertAndPushResult =
        RevertAndPushResult.builder().gitCommitResult(commitResult).gitPushResult(push(pushRequest)).build();
    return revertAndPushResult;
  }

  protected CommitResult revert(RevertRequest request) {
    ensureRepoLocallyClonedAndUpdated(request);

    try (Git git = openGit(new File(gitClientHelper.getRepoDirectory(request)), request.getDisableUserGitConfig())) {
      ObjectId commitId = git.getRepository().resolve(request.getCommitId());
      if (commitId == null) {
        throw new YamlException("Commit not found with id: " + request.getCommitId(), ADMIN_SRE);
      }
      RevCommit commitToRevert = git.getRepository().parseCommit(commitId);
      RevCommit revertCommit = git.revert().include(commitToRevert).call();

      return CommitResult.builder()
          .commitId(revertCommit.getName())
          .commitTime(revertCommit.getCommitTime())
          .commitMessage("Harness revert of commit: " + commitToRevert.getId())
          .build();
    } catch (IOException | GitAPIException ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING, ex);
      throw new YamlException("Error in writing commit", ex, ADMIN_SRE);
    }
  }

  @VisibleForTesting
  synchronized CommitResult commit(CommitAndPushRequest commitRequest) {
    boolean pushOnlyIfHeadSeen = commitRequest.isPushOnlyIfHeadSeen();
    String lastProcessedCommit = commitRequest.getLastProcessedGitCommit();

    List<String> filesToAdd = new ArrayList<>();

    ensureRepoLocallyClonedAndUpdated(commitRequest);

    try (Git git = openGit(
             new File(gitClientHelper.getRepoDirectory(commitRequest)), commitRequest.getDisableUserGitConfig())) {
      applyChangeSetOnFileSystem(gitClientHelper.getRepoDirectory(commitRequest), commitRequest, filesToAdd, git);

      // Removal of files should happen before addition of files.
      applyGitAddCommand(commitRequest, filesToAdd, git);

      Status status = git.status().call();

      if (status.isClean()) {
        log.warn(gitClientHelper.getGitLogMessagePrefix(commitRequest.getRepoType())
                + "No git change to commit. GitCommitRequest: [{}]",
            commitRequest);
        return CommitResult.builder().build(); // do nothing
      }

      ensureLastProcessedCommitIsHead(pushOnlyIfHeadSeen, lastProcessedCommit, git);

      StringBuilder commitMessage = prepareCommitMessage(commitRequest, status);
      String authorName = isNotBlank(commitRequest.getAuthorName()) ? commitRequest.getAuthorName() : HARNESS_IO_KEY_;
      String authorEmailId =
          isNotBlank(commitRequest.getAuthorEmail()) ? commitRequest.getAuthorEmail() : HARNESS_SUPPORT_EMAIL_KEY;

      RevCommit revCommit = git.commit()
                                .setCommitter(authorName, authorEmailId)
                                .setAuthor(authorName, authorEmailId)
                                .setAll(true)
                                .setMessage(commitMessage.toString())
                                .call();

      return CommitResult.builder()
          .commitId(revCommit.getName())
          .commitTime(revCommit.getCommitTime())
          .commitMessage(getTruncatedCommitMessage(commitMessage.toString()))
          .build();

    } catch (IOException | GitAPIException ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(commitRequest.getRepoType()) + EXCEPTION_STRING, ex);
      throw new YamlException("Error in writing commit", ex, ADMIN_SRE);
    }
  }

  private StringBuilder prepareCommitMessage(CommitAndPushRequest commitAndPushRequest, Status status) {
    StringBuilder commitMessage = new StringBuilder(48);
    if (isNotBlank(commitAndPushRequest.getCommitMessage())) {
      commitMessage.append(commitAndPushRequest.getCommitMessage());
    } else {
      commitMessage.append(COMMIT_MESSAGE);
      status.getAdded().forEach(
          filePath -> commitMessage.append(format("%s: %s\n", DiffEntry.ChangeType.ADD, filePath)));
      status.getChanged().forEach(
          filePath -> commitMessage.append(format("%s: %s\n", DiffEntry.ChangeType.MODIFY, filePath)));
      status.getRemoved().forEach(
          filePath -> commitMessage.append(format("%s: %s\n", DiffEntry.ChangeType.DELETE, filePath)));
    }
    log.info(format("Commit message for git sync for accountId: [%s]  repoUrl: [%s] %n "
            + "Additions: [%d] Modifications:[%d] Deletions:[%d]"
            + "%n Message: [%s]",
        commitAndPushRequest.getAccountId(), commitAndPushRequest.getRepoUrl(), status.getAdded().size(),
        status.getChanged().size(), status.getRemoved().size(), commitMessage));
    return commitMessage;
  }

  private List<GitFileChange> getFilesCommited(String gitCommitId, CommitAndPushRequest commitAndPushRequest) {
    try (Git git = openGit(new File(gitClientHelper.getRepoDirectory(commitAndPushRequest)),
             commitAndPushRequest.getDisableUserGitConfig())) {
      ObjectId commitId = ObjectId.fromString(gitCommitId);
      RevCommit currentCommitObject = null;
      try (RevWalk revWalk = new RevWalk(git.getRepository())) {
        currentCommitObject = revWalk.parseCommit(commitId);
      }

      if (currentCommitObject == null) {
        String repoURL = StringUtils.defaultIfBlank(commitAndPushRequest.getRepoUrl(), "");
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
      return getGitFileChangesFromDiff(diffs, git.getRepository(), commitAndPushRequest.getAccountId());
    } catch (Exception ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(commitAndPushRequest.getRepoType()) + EXCEPTION_STRING, ex);
      throw new YamlException("Error in getting the files commited to the git", ex, USER_ADMIN);
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
      String content = new String(loader.getBytes(), UTF_8);
      gitFileChanges.add(GitFileChange.builder()
                             .accountId(accountId)
                             .filePath(filePath)
                             .fileContent(content)
                             .changeType(gitClientHelper.getChangeType(entry.getChangeType()))
                             .syncFromGit(false)
                             .changeFromAnotherCommit(false)
                             .build());
    }
    return gitFileChanges;
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

  @VisibleForTesting
  String getHeadCommit(Git git) {
    try {
      ObjectId id = git.getRepository().resolve(org.eclipse.jgit.lib.Constants.HEAD);
      return id.getName();
    } catch (Exception ex) {
      throw new YamlException("Error in getting the head commit to the git", ex, USER_ADMIN);
    }
  }

  @VisibleForTesting
  void ensureLastProcessedCommitIsHead(boolean pushOnlyIfHeadSeen, String lastProcessedCommit, Git git) {
    if (!pushOnlyIfHeadSeen || isEmpty(lastProcessedCommit)) {
      return;
    }
    String headCommit = getHeadCommit(git);
    if (!lastProcessedCommit.equals(headCommit)) {
      throw new YamlException(String.format("Git commit failed. Encountered unseen commit [%s] expected [%s]",
                                  headCommit, lastProcessedCommit),
          ErrorCode.GIT_UNSEEN_REMOTE_HEAD_COMMIT, ADMIN_SRE);
    }
  }

  @VisibleForTesting
  void applyGitAddCommand(GitBaseRequest request, List<String> filesToAdd, Git git) {
    /*
    We do not need to specifically git add every added/modified file. git add . will take care
    of this
     */
    if (isNotEmpty(filesToAdd)) {
      try {
        git.add().addFilepattern(".").call();
      } catch (GitAPIException ex) {
        log.error(
            format("Error in add/modify git operation connectorId:[%s]", request.getConnectorId()) + " Exception: ",
            ex);
        throw new YamlException(
            format("Error in add/modify git operation connectorId:[%s]", request.getConnectorId()), ex, ADMIN_SRE);
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
      String repoDirectory, CommitAndPushRequest gitCommitRequest, List<String> filesToAdd, Git git) {
    gitCommitRequest.getGitFileChanges().forEach(gitFileChange -> {
      String filePath = repoDirectory + PATH_DELIMITER + gitFileChange.getFilePath();
      File file = new File(filePath);
      final ChangeType changeType = gitFileChange.getChangeType();
      switch (changeType) {
        case ADD:
        case MODIFY:
          try {
            log.info(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType()) + "Adding git file "
                + gitFileChange.toString());
            FileUtils.forceMkdir(file.getParentFile());
            FileUtils.writeStringToFile(file, gitFileChange.getFileContent(), UTF_8);
            filesToAdd.add(gitFileChange.getFilePath());
          } catch (IOException ex) {
            log.error(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType())
                + "Exception in adding/modifying file to git " + ex);
            throw new YamlException("IOException in ADD/MODIFY git operation", ADMIN);
          }
          break;
        case RENAME:
          log.info(
              gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType()) + "Old path:[{}], new path: [{}]",
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
              log.error(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType())
                  + "Exception in renaming file " + e);
              throw new YamlException(
                  format("Exception in renaming file [%s]->[%s]", oldFile.toPath(), newFile.toPath()), ADMIN_SRE);
            }
          } else {
            log.warn(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType())
                    + "File doesn't exist. path: [{}]",
                gitFileChange.getOldFilePath());
          }
          break;
        case DELETE:
          File fileToBeDeleted = new File(repoDirectory + PATH_DELIMITER + gitFileChange.getFilePath());
          if (fileToBeDeleted.exists()) {
            try {
              git.rm().addFilepattern(gitFileChange.getFilePath()).call();
            } catch (GitAPIException e) {
              log.error(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType())
                  + "Exception in deleting file " + e);
              throw new YamlException(
                  format("Exception in deleting file [%s]", gitFileChange.getFilePath()), ADMIN_SRE);
            }
            log.info(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType()) + "Deleting git file "
                + gitFileChange.toString());
          } else {
            log.warn(gitClientHelper.getGitLogMessagePrefix(gitCommitRequest.getRepoType())
                    + "File already deleted. path: [{}]",
                gitFileChange.getFilePath());
          }
          break;
        default:
          unhandled(changeType);
      }
    });
  }

  @VisibleForTesting
  protected PushResultGit push(PushRequest pushRequest) {
    boolean forcePush = pushRequest.isForcePush();

    log.info(gitClientHelper.getGitLogMessagePrefix(pushRequest.getRepoType())
        + "Performing git PUSH, forcePush is: " + forcePush);

    try (Git git =
             openGit(new File(gitClientHelper.getRepoDirectory(pushRequest)), pushRequest.getDisableUserGitConfig())) {
      Iterable<PushResult> pushResults = ((PushCommand) (getAuthConfiguredCommand(git.push(), pushRequest)))
                                             .setRemote("origin")
                                             .setForce(forcePush)
                                             .setRefSpecs(new RefSpec(pushRequest.getBranch()))
                                             .call();

      RemoteRefUpdate remoteRefUpdate = pushResults.iterator().next().getRemoteUpdates().iterator().next();
      PushResultGit.RefUpdate refUpdate =
          PushResultGit.RefUpdate.builder()
              .status(remoteRefUpdate.getStatus().name())
              .expectedOldObjectId(remoteRefUpdate.getExpectedOldObjectId() != null
                      ? remoteRefUpdate.getExpectedOldObjectId().name()
                      : null)
              .newObjectId(remoteRefUpdate.getNewObjectId() != null ? remoteRefUpdate.getNewObjectId().name() : null)
              .forceUpdate(remoteRefUpdate.isForceUpdate())
              .message(remoteRefUpdate.getMessage())
              .build();
      if (remoteRefUpdate.getStatus() == OK || remoteRefUpdate.getStatus() == UP_TO_DATE) {
        return pushResultBuilder().refUpdate(refUpdate).build();
      } else {
        String errorMsg = format("Unable to push changes to git repository [%s] and branch [%s]. "
                + "Status reported by Remote is: %s and message is: %s. \n \n",
            pushRequest.getRepoUrl(), pushRequest.getBranch(), remoteRefUpdate.getStatus(),
            remoteRefUpdate.getMessage());
        log.error(gitClientHelper.getGitLogMessagePrefix(pushRequest.getRepoType()) + errorMsg);
        throw new YamlException(errorMsg, ADMIN_SRE);
      }
    } catch (IOException | GitAPIException ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(pushRequest.getRepoType()) + EXCEPTION_STRING
          + ExceptionSanitizer.sanitizeForLogging(ex));
      String errorMsg = getMessage(ex);
      if (ex instanceof InvalidRemoteException || ex.getCause() instanceof NoRemoteRepositoryException) {
        errorMsg = "Invalid git repo or user doesn't have write access to repository. repo:" + pushRequest.getRepoUrl();
      }

      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException(errorMsg, ex, USER);
    }
  }

  @VisibleForTesting
  synchronized PushResultGit push(CommitAndPushRequest commitAndPushRequest) {
    boolean forcePush = commitAndPushRequest.isForcePush();

    log.info(gitClientHelper.getGitLogMessagePrefix(commitAndPushRequest.getRepoType())
        + "Performing git PUSH, forcePush is: " + forcePush);

    try (Git git = openGit(new File(gitClientHelper.getRepoDirectory(commitAndPushRequest)),
             commitAndPushRequest.getDisableUserGitConfig())) {
      Iterable<PushResult> pushResults = ((PushCommand) (getAuthConfiguredCommand(git.push(), commitAndPushRequest)))
                                             .setRemote("origin")
                                             .setForce(forcePush)
                                             .setRefSpecs(new RefSpec(commitAndPushRequest.getBranch()))
                                             .call();

      RemoteRefUpdate remoteRefUpdate = pushResults.iterator().next().getRemoteUpdates().iterator().next();
      PushResultGit.RefUpdate refUpdate =
          PushResultGit.RefUpdate.builder()
              .status(remoteRefUpdate.getStatus().name())
              .expectedOldObjectId(remoteRefUpdate.getExpectedOldObjectId() != null
                      ? remoteRefUpdate.getExpectedOldObjectId().name()
                      : null)
              .newObjectId(remoteRefUpdate.getNewObjectId() != null ? remoteRefUpdate.getNewObjectId().name() : null)
              .forceUpdate(remoteRefUpdate.isForceUpdate())
              .message(remoteRefUpdate.getMessage())
              .build();
      if (remoteRefUpdate.getStatus() == OK || remoteRefUpdate.getStatus() == UP_TO_DATE) {
        return pushResultBuilder().refUpdate(refUpdate).build();
      } else {
        String errorMsg = format("Unable to push changes to git repository [%s] and branch [%s]. "
                + "Status reported by Remote is: %s and message is: %s. \n \n"
                + "Files which were staged: [%s]",
            commitAndPushRequest.getRepoUrl(), commitAndPushRequest.getBranch(), remoteRefUpdate.getStatus(),
            remoteRefUpdate.getMessage(),
            emptyIfNull(commitAndPushRequest.getGitFileChanges())
                .stream()
                .map(GitFileChange::getFilePath)
                .collect(Collectors.toList()));
        log.error(gitClientHelper.getGitLogMessagePrefix(commitAndPushRequest.getRepoType()) + errorMsg);
        throw new YamlException(errorMsg, ADMIN_SRE);
      }
    } catch (IOException | GitAPIException ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(commitAndPushRequest.getRepoType()) + EXCEPTION_STRING
          + ExceptionSanitizer.sanitizeForLogging(ex));
      String errorMsg = getMessage(ex);
      if (ex instanceof InvalidRemoteException || ex.getCause() instanceof NoRemoteRepositoryException) {
        errorMsg = "Invalid git repo or user doesn't have write access to repository. repo:"
            + commitAndPushRequest.getRepoUrl();
      }

      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException(errorMsg, ex, USER);
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

  private void checkoutGivenCommitForAllPaths(FetchFilesBwCommitsRequest request) {
    try (Git git = openGit(
             new File(gitClientHelper.getFileDownloadRepoDirectory(request)), request.getDisableUserGitConfig())) {
      log.info("Checking out commitId: " + request.getNewCommitId());
      CheckoutCommand checkoutCommand =
          git.checkout().setStartPoint(request.getNewCommitId()).setCreateBranch(false).setAllPaths(true);

      checkoutCommand.call();
      log.info("Successfully Checked out commitId: " + request.getNewCommitId());
    } catch (Exception ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING
          + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfMissingCommitIdIssue(ex, request.getNewCommitId());
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in checking out commit id " + request.getNewCommitId(), USER);
    }
  }

  private List<GitFile> getGitFilesFromDiff(
      List<DiffEntry> diffs, Repository repository, GitRepositoryType gitRepositoryType) throws IOException {
    log.info(gitClientHelper.getGitLogMessagePrefix(gitRepositoryType)
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
      String content = new String(loader.getBytes(), UTF_8);
      gitFiles.add(GitFile.builder().filePath(filePath).fileContent(content).build());
    }

    return gitFiles;
  }

  @Override
  public FetchFilesResult fetchFilesBetweenCommits(FetchFilesBwCommitsRequest request) {
    String gitConnectorId = request.getConnectorId();
    validateRequiredArgsForFilesBetweenCommit(request.getOldCommitId(), request.getNewCommitId());
    if (!isEmpty(request.getBranch())) {
      request.setBranch(StringUtils.EMPTY);
    }

    final File lockFile = gitClientHelper.getLockObject(gitConnectorId);
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        log.info(new StringBuilder(128)
                     .append(" Processing Git command: FILES_BETWEEN_COMMITS ")
                     .append("Account: ")
                     .append(request.getAccountId())
                     .append(", repo: ")
                     .append(request.getRepoUrl())
                     .append(", newCommitId: ")
                     .append(request.getNewCommitId())
                     .append(", oldCommitId: ")
                     .append(request.getOldCommitId())
                     .append(", gitConnectorId: ")
                     .append(request.getConnectorId())
                     .toString());

        gitClientHelper.createDirStructureForFileDownload(request);
        cloneRepoForFilePathCheckout(request);
        checkoutGivenCommitForAllPaths(request);
        List<GitFile> gitFilesFromDiff;

        try (Git git = openGit(
                 new File(gitClientHelper.getFileDownloadRepoDirectory(request)), request.getDisableUserGitConfig())) {
          Repository repository = git.getRepository();

          ObjectId newCommitHead = repository.resolve(request.getNewCommitId() + "^{tree}");
          ObjectId oldCommitHead = repository.resolve(request.getOldCommitId() + "^{tree}");

          List<DiffEntry> diffs = getDiffEntries(repository, git, newCommitHead, oldCommitHead);
          gitFilesFromDiff = getGitFilesFromDiff(diffs, repository, request.getRepoType());

        } catch (IOException | GitAPIException ex) {
          log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING, ex);
          throw new YamlException("Error in getting commit diff", USER_ADMIN);
        }

        resetWorkingDir(request);

        return FetchFilesResult.builder()
            .files(gitFilesFromDiff)
            .commitResult(CommitResult.builder().commitId(request.getNewCommitId()).build())
            .build();

      } catch (WingsException e) {
        tryResetWorkingDir(request);
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(request);
        log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING, e);
        throw new YamlException(new StringBuilder()
                                    .append("Failed while fetching files between commits ")
                                    .append("Account: ")
                                    .append(request.getAccountId())
                                    .append(", newCommitId: ")
                                    .append(request.getNewCommitId())
                                    .append(", oldCommitId: ")
                                    .append(request.getOldCommitId())
                                    .append(", gitConnectorId: ")
                                    .append(request.getConnectorId())
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

  @Override
  public FetchFilesResult fetchFilesByPath(FetchFilesByPathRequest request) throws IOException {
    return fetchFilesByPath(DEFAULT_FETCH_IDENTIFIER, request);
  }

  @Override
  public FetchFilesResult fetchFilesByPath(String identifier, FetchFilesByPathRequest request) throws IOException {
    cleanup(request);
    validateRequiredArgs(request);
    File lockFile = gitClientHelper.getLockObject(request.getConnectorId());
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        String latestCommitSHA = checkoutFiles(request);
        GitFetchMetadataLocalThread.putCommitId(identifier, latestCommitSHA);
        List<GitFile> gitFiles = getFilteredGitFiles(request);
        resetWorkingDir(request);

        if (isNotEmpty(gitFiles)) {
          gitFiles.forEach(gitFile -> log.info("File fetched : " + gitFile.getFilePath()));
        }

        return FetchFilesResult.builder()
            .files(gitFiles)
            .commitResult(
                CommitResult.builder().commitId(request.useBranch() ? "latest" : request.getCommitId()).build())
            .build();

      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(request);
        log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING, e);
        throw e;
      }
    }
  }

  @VisibleForTesting
  List<GitFile> getFilteredGitFiles(FetchFilesByPathRequest request) {
    List<GitFile> gitFiles = new ArrayList<>();

    String repoPath = gitClientHelper.getFileDownloadRepoDirectory(request);
    request.getFilePaths().forEach(filePath -> {
      try {
        Path repoFilePath = Paths.get(repoPath + "/" + filePath);
        Stream<Path> paths = request.isRecursive() ? Files.walk(repoFilePath) : Files.walk(repoFilePath, 1);
        paths.filter(Files::isRegularFile)
            .filter(path -> !path.toString().contains(".git"))
            .filter(matchingFilesExtensions(request.getFileExtensions()))
            .forEach(path -> gitClientHelper.addFiles(gitFiles, path, repoPath));
      } catch (Exception e) {
        resetWorkingDir(request);

        // GitFetchFilesTask relies on the exception cause whether to fail the deployment or not.
        // If the exception is being changed, make sure that the throwable cause is added to the new exception
        // Unit test testGetFilteredGitFilesNoFileFoundException makes sure that the original exception is not swallowed
        throw JGitRuntimeException.builder()
            .message("Unable to checkout file: " + filePath)
            .cause(e)
            .branch(request.getBranch())
            .commitId(request.getCommitId())
            .build();
      }
    });

    return gitFiles;
  }

  @VisibleForTesting
  Predicate<Path> matchingFilesExtensions(List<String> fileExtensions) {
    return path -> {
      if (isEmpty(fileExtensions)) {
        return true;
      } else {
        for (String fileExtension : fileExtensions) {
          if (path.toString().endsWith(fileExtension)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  @Override
  public String downloadFiles(DownloadFilesRequest request) throws IOException {
    cleanup(request);
    validateRequiredArgs(request);

    final File lockFile = gitClientHelper.getLockObject(request.getConnectorId());
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock lock = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        String commitReference = checkoutFiles(request);
        String repoPath = gitClientHelper.getFileDownloadRepoDirectory(request);

        FileIo.createDirectoryIfDoesNotExist(request.getDestinationDirectory());
        FileIo.waitForDirectoryToBeAccessibleOutOfProcess(request.getDestinationDirectory(), 10);

        File destinationDir = new File(request.getDestinationDirectory());

        for (String filePath : request.getFilePaths()) {
          try {
            File sourceDir = new File(Paths.get(repoPath + "/" + filePath).toString());
            if (sourceDir.isFile()) {
              FileUtils.copyFile(sourceDir, Paths.get(request.getDestinationDirectory(), filePath).toFile());
            } else {
              FileUtils.copyDirectory(sourceDir, destinationDir);
              // if source directory is repo root we don't want to have .git copied to destination directory
              File gitFile = new File(Paths.get(request.getDestinationDirectory(), ".git").toString());
              if (gitFile.exists()) {
                FileUtils.deleteQuietly(gitFile);
              }
            }
          } catch (FileNotFoundException e) {
            throw JGitRuntimeException.builder()
                .message(String.format("File '%s' not found", filePath))
                .cause(e)
                .commitId(request.getCommitId())
                .branch(request.getBranch())
                .build();
          }
        }

        resetWorkingDir(request);
        return commitReference;
      } catch (WingsException e) {
        tryResetWorkingDir(request);
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(request);
        throw new YamlException(new StringBuilder()
                                    .append("Failed while fetching files ")
                                    .append(request.useBranch() ? "for Branch: " : "for CommitId: ")
                                    .append(request.useBranch() ? request.getBranch() : request.getCommitId())
                                    .append(", FilePaths: ")
                                    .append(request.getFilePaths())
                                    .append(". Reason: ")
                                    .append(e.getMessage())
                                    .append(", ")
                                    .append(e.getCause() != null ? e.getCause().getMessage() : "")
                                    .toString(),
            e, USER);
      }
    }
  }

  @Override
  @Nullable
  public String cloneRepoAndCopyToDestDir(DownloadFilesRequest request) {
    final File lockFile = gitClientHelper.getLockObject(request.getConnectorId());
    synchronized (lockFile) {
      log.info("Trying to acquire lock on {}", lockFile);
      try (FileOutputStream fileOutputStream = new FileOutputStream(lockFile);
           FileLock ignored = fileOutputStream.getChannel().lock()) {
        log.info("Successfully acquired lock on {}", lockFile);
        ensureRepoLocallyClonedAndUpdated(request);
        String repoPath = gitClientHelper.getRepoDirectory(request);
        File src = new File(repoPath);
        File dest = new File(request.getDestinationDirectory());
        deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
        FileUtils.copyDirectory(src, dest);
        FileIo.waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);

        return getLatestCommitReference(repoPath);
      } catch (WingsException e) {
        tryResetWorkingDir(request);
        throw e;
      } catch (Exception e) {
        logPossibleFileLockRelatedExceptions(e);
        tryResetWorkingDir(request);
        throw new YamlException(new StringBuilder()
                                    .append("Failed while fetching files ")
                                    .append(request.useBranch() ? "for Branch: " : "for CommitId: ")
                                    .append(request.useBranch() ? request.getBranch() : request.getCommitId())
                                    .append(", FilePaths: ")
                                    .append(request.getFilePaths())
                                    .append(". Reason: ")
                                    .append(e.getMessage())
                                    .append(", ")
                                    .append(e.getCause() != null ? e.getCause().getMessage() : "")
                                    .toString(),
            e, USER);
      }
    }
  }

  @Override
  public ListRemoteResult listRemote(ListRemoteRequest request) {
    try {
      LsRemoteCommand lsRemoteCommand = (LsRemoteCommand) getAuthConfiguredCommand(Git.lsRemoteRepository(), request);
      Map<String, Ref> lsRemoteCommandResult = lsRemoteCommand.setRemote(request.getRepoUrl()).callAsMap();
      Map<String, String> remoteList = new HashMap<>();
      lsRemoteCommandResult.forEach((k, v) -> remoteList.put(k, v.getTarget().getName()));
      return ListRemoteResult.builder().remoteList(remoteList).build();
    } catch (GitAPIException e) {
      log.error(GIT_YAML_LOG_PREFIX + "Error in listing remote: " + ExceptionSanitizer.sanitizeForLogging(e));
      throw new YamlException("Error in listing remote", USER);
    }
  }

  private void tryResetWorkingDir(GitBaseRequest request) {
    try {
      resetWorkingDir(request);
    } catch (Exception ex) {
      log.info("Not able to reset repository", ex);
    }
  }

  private void resetWorkingDir(GitBaseRequest request) {
    try (Git git = openGit(
             new File(gitClientHelper.getFileDownloadRepoDirectory(request)), request.getDisableUserGitConfig())) {
      log.info("Resetting repo");
      ResetCommand resetCommand = new ResetCommand(git.getRepository()).setMode(ResetCommand.ResetType.HARD);
      resetCommand.call();
      log.info("Resetting repo completed successfully");
    } catch (Exception ex) {
      log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING
          + ExceptionSanitizer.sanitizeForLogging(ex));
      gitClientHelper.checkIfGitConnectivityIssue(ex);
      throw new YamlException("Error in resetting repo", USER);
    }
  }

  // use this method wrapped in inter process file lock to handle multiple delegate version
  private String checkoutFiles(FetchFilesByPathRequest request) {
    synchronized (gitClientHelper.getLockObject(request.getConnectorId())) {
      log.info(new StringBuilder(128)
                   .append(" Processing Git command: FETCH_FILES ")
                   .append("Account: ")
                   .append(request.getAccountId())
                   .append(", repo: ")
                   .append(request.getRepoUrl())
                   .append(request.useBranch() ? ", Branch: " : ", CommitId: ")
                   .append(request.useBranch() ? request.getBranch() : request.getCommitId())
                   .append(", filePaths: ")
                   .append(request.getFilePaths())
                   .toString());

      gitClientHelper.createDirStructureForFileDownload(request);

      // clone repo locally without checkout
      cloneRepoForFilePathCheckout(request);

      // if useBranch is set, use it to checkout latest, else checkout given commitId
      String commitId = request.getCommitId();
      if (request.useBranch()) {
        commitId = checkoutBranchForPath(request);
      } else {
        checkoutGivenCommitForPath(request);
      }
      return commitId;
    }
  }

  private void checkoutGivenCommitForPath(FetchFilesByPathRequest request) {
    try (Git git = openGit(
             new File(gitClientHelper.getFileDownloadRepoDirectory(request)), request.getDisableUserGitConfig())) {
      log.info("Checking out commitId: " + request.getCommitId());
      CheckoutCommand checkoutCommand = git.checkout().setStartPoint(request.getCommitId()).setCreateBranch(false);

      setPathsForCheckout(request.getFilePaths(), checkoutCommand);
      checkoutCommand.call();
      log.info("Successfully Checked out commitId: " + request.getCommitId());
    } catch (Exception ex) {
      log.error(GIT_YAML_LOG_PREFIX + EXCEPTION_STRING, ex);
      throw JGitRuntimeException.builder()
          .message("Error in checking out commit id " + request.getCommitId())
          .cause(ex)
          .commitId(request.getCommitId())
          .build();
    }
  }

  private String checkoutBranchForPath(FetchFilesByPathRequest request) {
    try (Git git = openGit(
             new File(gitClientHelper.getFileDownloadRepoDirectory(request)), request.getDisableUserGitConfig())) {
      log.info("Checking out Branch: " + request.getBranch());
      CheckoutCommand checkoutCommand = git.checkout()
                                            .setCreateBranch(true)
                                            .setStartPoint(ORIGIN + request.getBranch())
                                            .setForce(true)
                                            .setUpstreamMode(SetupUpstreamMode.TRACK)
                                            .setName(request.getBranch());

      setPathsForCheckout(request.getFilePaths(), checkoutCommand);
      checkoutCommand.call();
      log.info("Successfully Checked out Branch: " + request.getBranch());
      return getCommitId(git, request.getBranch());
    } catch (Exception ex) {
      log.error(GIT_YAML_LOG_PREFIX + EXCEPTION_STRING, ex);
      throw JGitRuntimeException.builder()
          .message("Error in checking out Branch " + request.getBranch())
          .cause(ex)
          .branch(request.getBranch())
          .build();
    }
  }

  private String getCommitId(Git git, String branch) {
    String commitId = null;
    try {
      Ref ref = git.getRepository().getAllRefs().get("refs/remotes/origin/" + branch);
      if (ref != null && ref.getObjectId() != null) {
        commitId = ref.getObjectId().name();
      }
    } catch (Exception e) {
      log.error("Failed to get commit id: {}", e.getMessage());
    }
    return commitId;
  }

  private void setPathsForCheckout(List<String> filePaths, CheckoutCommand checkoutCommand) {
    if (filePaths.size() == 1 && (filePaths.get(0).equals("") || filePaths.get(0).equals("/"))) {
      checkoutCommand.setAllPaths(true);
    } else {
      filePaths.stream().map(this::getNormalRelativePath).forEach(checkoutCommand::addPath);
    }
  }

  private String getNormalRelativePath(String path) {
    if (path.charAt(0) == '/') {
      return path.substring(1);
    } else {
      return path;
    }
  }

  /**
   * Ensure repo locally cloned. This is called before performing any git operation with remote
   *
   * @param request
   */
  private synchronized void cloneRepoForFilePathCheckout(GitBaseRequest request) {
    log.info(new StringBuilder(64)
                 .append(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()))
                 .append("Cloning repo without checkout for file fetch op, for GitConfig: ")
                 .append(request.toString())
                 .toString());

    boolean exceptionOccured = false;
    File repoDir = new File(gitClientHelper.getFileDownloadRepoDirectory(request));
    // If repo already exists, update references
    if (repoDir.exists()) {
      // Check URL change (ssh, https) and update in .git/config
      updateRemoteOriginInConfig(request.getRepoUrl(), repoDir, request.getDisableUserGitConfig());

      try (Git git = openGit(repoDir, request.getDisableUserGitConfig())) {
        // update ref with latest commits on remote
        FetchResult fetchResult = ((FetchCommand) (getAuthConfiguredCommand(git.fetch(), request)))
                                      .setRemoveDeletedRefs(true)
                                      .setTagOpt(TagOpt.FETCH_TAGS)
                                      .call(); // fetch all remote references

        log.info(new StringBuilder()
                     .append(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()))
                     .append("result fetched: ")
                     .append(fetchResult.toString())
                     .toString());

        return;
      } catch (Exception ex) {
        exceptionOccured = true;
        if (ex instanceof IOException) {
          log.warn(gitClientHelper.getGitLogMessagePrefix(request.getRepoType())
                  + "Repo doesn't exist locally [repo: {}], {} ",
              request.getRepoUrl(), ex);
          log.info(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Do a fresh clone");
        } else {
          log.info(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + "Hard reset failed for branch [{}]",
              request.getBranch());
          log.error(gitClientHelper.getGitLogMessagePrefix(request.getRepoType()) + EXCEPTION_STRING
              + ExceptionSanitizer.sanitizeForLogging(ex));
          gitClientHelper.checkIfGitConnectivityIssue(ex);
        }
      } finally {
        if (exceptionOccured) {
          gitClientHelper.releaseLock(request, gitClientHelper.getFileDownloadRepoDirectory(request));
        }
      }
    }

    clone(request, gitClientHelper.getFileDownloadRepoDirectory(request), true);
  }

  /**
   * FilePath cant empty as well as (Branch and commitId both cant be empty)
   *
   * @param request Download request
   * @throws InvalidRequestException for required args with message
   */
  private void validateRequiredArgs(FetchFilesByPathRequest request) {
    if (isEmpty(request.getFilePaths())) {
      throw new InvalidRequestException("FilePaths can not be empty", USER);
    }

    if (isEmpty(request.getBranch()) && isEmpty(request.getCommitId())) {
      throw new InvalidRequestException("No refs provided to checkout", USER);
    }
  }

  @VisibleForTesting
  TransportCommand getAuthConfiguredCommand(TransportCommand gitCommand, GitBaseRequest gitBaseRequest) {
    if (gitBaseRequest.getAuthRequest().getAuthType() == AuthInfo.AuthType.HTTP_PASSWORD) {
      UsernamePasswordAuthRequest authRequest = (UsernamePasswordAuthRequest) gitBaseRequest.getAuthRequest();
      Preconditions.checkState(EmptyPredicate.isNotEmpty(authRequest.getUsername()),
          "The user is null in git config for the git connector " + gitBaseRequest.getConnectorId());
      Preconditions.checkState(EmptyPredicate.isNotEmpty(authRequest.getPassword()),
          "The password is null in git config for the git connector " + gitBaseRequest.getConnectorId());
      gitCommand.setCredentialsProvider(new UsernamePasswordCredentialsProviderWithSkipSslVerify(
          authRequest.getUsername(), authRequest.getPassword()));
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
    } else if (gitBaseRequest.getAuthRequest().getAuthType() == AuthInfo.AuthType.SSH_KEY) {
      JgitSshAuthRequest authRequest = (JgitSshAuthRequest) gitBaseRequest.getAuthRequest();
      gitCommand.setTransportConfigCallback(transport -> {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(authRequest.getFactory());
      });
    }
    return gitCommand;
  }

  private Git openGit(File repoDir, Boolean disableUserConfig) throws IOException {
    if (disableUserConfig != null && disableUserConfig) {
      SystemReader.setInstance(new CustomUserGitConfigSystemReader(null));
    } else {
      SystemReader.setInstance(null);
    }
    return Git.open(repoDir);
  }

  private String getLatestCommitReference(String repoDir) {
    try (Git git = Git.open(new File(repoDir))) {
      Iterator<RevCommit> commits = git.log().call().iterator();
      if (commits.hasNext()) {
        RevCommit firstCommit = commits.next();

        return firstCommit.toString().split(" ")[1];
      }
    } catch (IOException | GitAPIException e) {
      log.error("Failed to extract the commit id from the cloned repo.", e);
    }

    return null;
  }

  private ListRemoteRequest buildListRemoteRequestFromGitBaseRequest(GitBaseRequest request) {
    return ListRemoteRequest.builder()
        .repoUrl(request.getRepoUrl())
        .branch(request.getBranch())
        .commitId(request.getCommitId())
        .authRequest(request.getAuthRequest())
        .connectorId(request.getConnectorId())
        .accountId(request.getAccountId())
        .repoType(request.getRepoType())
        .disableUserGitConfig(request.getDisableUserGitConfig())
        .unsureOrNonExistentBranch(request.isUnsureOrNonExistentBranch())
        .build();
  }
}
