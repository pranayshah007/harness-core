/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.SshSessionFactory.generateTGTUsingSshConfig;
import static io.harness.shell.SshSessionFactory.getSSHSession;

import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;
import static software.wings.utils.SshDelegateHelperUtils.createSshSessionConfig;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientV2;
import io.harness.git.UsernamePasswordAuthRequest;
import io.harness.git.model.AuthRequest;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushRequest.CommitAndPushRequestBuilder;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.CommitResult;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitBaseRequest.GitBaseRequestBuilder;
import io.harness.git.model.GitFileChange;
import io.harness.git.model.JgitSshAuthRequest;
import io.harness.git.model.PushResultGit;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.SshFactory;
import io.harness.shell.ssh.client.jsch.JschConnection;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitCommitResult;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.beans.yaml.GitFilesBetweenCommitsRequest;
import software.wings.beans.yaml.GitPushResult;
import software.wings.beans.yaml.GitPushResult.RefUpdate;
import software.wings.service.impl.yaml.GitClientImpl;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.yaml.GitClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(DX)
public class GitServiceImpl implements GitService {
  @Inject private GitClient gitClient;
  @Inject private GitClientV2 gitClientV2;
  private static final String DEFAULT_FETCH_IDENTIFIER = "--default";

  @Override
  public String validate(GitConfig gitConfig) {
    AuthRequest authRequest = getAuthRequest(gitConfig);
    return gitClientV2.validate(GitBaseRequest.builder()
                                    .branch(gitConfig.getBranch())
                                    .repoUrl(gitConfig.getRepoUrl())
                                    .repoType(gitConfig.getGitRepoType())
                                    .authRequest(authRequest)
                                    .build());
  }

  @Override
  public void ensureRepoLocallyClonedAndUpdated(GitOperationContext gitOperationContext) {
    GitConfig gitConfig = gitOperationContext.getGitConfig();
    AuthRequest authRequest = getAuthRequest(gitConfig);
    gitClientV2.ensureRepoLocallyClonedAndUpdated(GitBaseRequest.builder()
                                                      .branch(gitConfig.getBranch())
                                                      .commitId(gitConfig.getReference())
                                                      .repoUrl(gitConfig.getRepoUrl())
                                                      .repoType(gitConfig.getGitRepoType())
                                                      .authRequest(authRequest)
                                                      .accountId(gitConfig.getAccountId())
                                                      .connectorId(gitOperationContext.getGitConnectorId())
                                                      .disableUserGitConfig(gitConfig.getDisableUserGitConfig())
                                                      .build());
  }

  private AuthRequest getAuthRequest(GitConfig gitConfig) {
    AuthRequest authRequest;
    if (gitConfig.getRepoUrl().toLowerCase().startsWith("http")) {
      String username = gitConfig.getUsername();
      char[] password = gitConfig.getPassword();
      if (KERBEROS == gitConfig.getAuthenticationScheme()) {
        addApacheConnectionFactoryAndGenerateTGT(gitConfig);
        username = ((HostConnectionAttributes) gitConfig.getSshSettingAttribute().getValue())
                       .getKerberosConfig()
                       .getPrincipal(); // set principal as username
      }
      authRequest = UsernamePasswordAuthRequest.builder().username(username).password(password).build();
    } else {
      authRequest =
          JgitSshAuthRequest.builder().factory(getSshSessionFactory(gitConfig.getSshSettingAttribute())).build();
    }
    return authRequest;
  }

  private SshSessionFactory getSshSessionFactory(SettingAttribute settingAttribute) {
    return new JschConfigSessionFactory() {
      @Override
      protected Session createSession(OpenSshConfig.Host hc, String user, String host, int port, FS fs)
          throws JSchException {
        SshSessionConfig sshSessionConfig = createSshSessionConfig(settingAttribute, host);
        sshSessionConfig.setPort(port); // use port from repo URL
        if (sshSessionConfig.isUseSshClient()) {
          return ((JschConnection) SshFactory.getSshClient(sshSessionConfig).getConnection()).getSession();
        } else {
          return getSSHSession(sshSessionConfig);
        }
      }

      @Override
      protected void configure(OpenSshConfig.Host hc, Session session) {}
    };
  }

  private void addApacheConnectionFactoryAndGenerateTGT(GitConfig gitConfig) {
    try {
      HttpTransport.setConnectionFactory(new GitClientImpl.ApacheHttpConnectionFactory());
      URL url = new URL(gitConfig.getRepoUrl());
      SshSessionConfig sshSessionConfig = createSshSessionConfig(gitConfig.getSshSettingAttribute(), url.getHost());
      generateTGTUsingSshConfig(sshSessionConfig, new NoopExecutionCallback());
    } catch (Exception e) {
      log.error(GIT_YAML_LOG_PREFIX + "Exception while setting kerberos auth for repo: [{}] with ex: [{}]",
          gitConfig.getRepoUrl(), getMessage(e));
      throw new InvalidRequestException("Failed to do Kerberos authentication");
    }
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, boolean shouldExportCommitSha) {
    return fetchFilesByPath(
        gitConfig, connectorId, commitId, branch, filePaths, useBranch, shouldExportCommitSha, null);
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, boolean shouldExportCommitSha, LogCallback logCallback) {
    return fetchFilesByPath(DEFAULT_FETCH_IDENTIFIER, gitConfig, connectorId, commitId, branch, filePaths, useBranch,
        shouldExportCommitSha, null);
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(String identifier, GitConfig gitConfig, String connectorId,
      String commitId, String branch, List<String> filePaths, boolean useBranch, boolean shouldExportCommitSha,
      LogCallback logCallback) {
    return gitClient.fetchFilesByPath(gitConfig,
        GitFetchFilesRequest.builder()
            .commitId(commitId)
            .identifier(identifier)
            .branch(branch)
            .filePaths(filePaths)
            .gitConnectorId(connectorId)
            .useBranch(useBranch)
            .recursive(true)
            .build(),
        shouldExportCommitSha, logCallback);
  }

  @Override
  public String downloadFiles(
      GitConfig gitConfig, GitFileConfig gitFileConfig, String destinationDirectory, boolean shouldExportCommitSha) {
    return downloadFiles(gitConfig, gitFileConfig, destinationDirectory, shouldExportCommitSha, null);
  }

  @Override
  public String downloadFiles(GitConfig gitConfig, GitFileConfig gitFileConfig, String destinationDirectory,
      boolean shouldExportCommitSha, LogCallback logCallback) {
    return gitClient.downloadFiles(gitConfig,
        GitFetchFilesRequest.builder()
            .commitId(gitFileConfig.getCommitId())
            .branch(gitFileConfig.getBranch())
            .filePaths(Collections.singletonList(gitFileConfig.getFilePath()))
            .gitConnectorId(gitFileConfig.getConnectorId())
            .useBranch(gitFileConfig.isUseBranch())
            .recursive(true)
            .build(),
        destinationDirectory, shouldExportCommitSha, logCallback);
  }

  @Override
  public GitFetchFilesResult fetchFilesBetweenCommits(
      GitConfig gitConfig, String newCommitId, String oldCommitId, String connectorId) {
    return gitClient.fetchFilesBetweenCommits(gitConfig,
        GitFilesBetweenCommitsRequest.builder()
            .newCommitId(newCommitId)
            .oldCommitId(oldCommitId)
            .gitConnectorId(connectorId)
            .build());
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, List<String> fileExtensions, boolean isRecursive) {
    return gitClient.fetchFilesByPath(gitConfig,
        GitFetchFilesRequest.builder()
            .commitId(commitId)
            .branch(branch)
            .filePaths(filePaths)
            .gitConnectorId(connectorId)
            .useBranch(useBranch)
            .fileExtensions(fileExtensions)
            .recursive(isRecursive)
            .build(),
        false);
  }

  @Override
  public GitCommitAndPushResult commitAndPush(GitOperationContext gitOperationContext) {
    final CommitAndPushRequest commitAndPushRequest = getCommitAndPushRequestFromOperationContext(gitOperationContext);
    CommitAndPushResult commitAndPushResult = gitClientV2.commitAndPush(commitAndPushRequest);
    return buildCommitAndPushResult(gitOperationContext, commitAndPushResult);
  }

  private GitCommitAndPushResult buildCommitAndPushResult(
      GitOperationContext gitOperationContext, CommitAndPushResult commitAndPushResult) {
    return GitCommitAndPushResult.builder()
        .gitCommitResult(castToCommitResult(commitAndPushResult.getGitCommitResult()))
        .filesCommitedToGit(toManagerGitFileChange(commitAndPushResult.getFilesCommittedToGit()))
        .gitPushResult(toGitPushResult(commitAndPushResult.getGitPushResult()))
        .yamlGitConfig(gitOperationContext.getGitCommitRequest().getYamlGitConfig())
        .build();
  }

  private GitPushResult toGitPushResult(PushResultGit pushResultGit) {
    if (pushResultGit == null) {
      return null;
    }
    PushResultGit.RefUpdate refUpdate = pushResultGit.getRefUpdate();
    RefUpdate ref = RefUpdate.builder()
                        .expectedOldObjectId(refUpdate.getExpectedOldObjectId())
                        .forceUpdate(refUpdate.isForceUpdate())
                        .message(refUpdate.getMessage())
                        .newObjectId(refUpdate.getNewObjectId())
                        .status(refUpdate.getStatus())
                        .build();
    return GitPushResult.builder().refUpdate(ref).build();
  }

  private GitCommitResult castToCommitResult(CommitResult commitResult) {
    if (commitResult == null) {
      return null;
    }
    return GitCommitResult.builder()
        .commitId(commitResult.getCommitId())
        .commitMessage(commitResult.getCommitMessage())
        .commitTime(commitResult.getCommitTime())
        .build();
  }

  private List<software.wings.beans.yaml.GitFileChange> toManagerGitFileChange(List<GitFileChange> gitFileChanges) {
    if (gitFileChanges == null) {
      return Collections.emptyList();
    }
    return gitFileChanges.stream().map(this::toManagerGitFileChange).collect(Collectors.toList());
  }

  private software.wings.beans.yaml.GitFileChange toManagerGitFileChange(GitFileChange gitFileChange) {
    if (gitFileChange == null) {
      return null;
    }
    return software.wings.beans.yaml.GitFileChange.Builder.aGitFileChange()
        .withFilePath(gitFileChange.getFilePath())
        .withAccountId(gitFileChange.getAccountId())
        .withFileContent(gitFileChange.getFileContent())
        .withChangeType(gitFileChange.getChangeType())
        .withCommitId(gitFileChange.getCommitId())
        .withChangeFromAnotherCommit(gitFileChange.isChangeFromAnotherCommit())
        .withSyncFromGit(gitFileChange.isSyncFromGit())
        .withCommitMessage(gitFileChange.getCommitMessage())
        .withCommitTimeMs(gitFileChange.getCommitTimeMs())
        .withProcessingCommitId(gitFileChange.getProcessingCommitId())
        .withOldFilePath(gitFileChange.getOldFilePath())
        .withProcessingCommitMessage(gitFileChange.getProcessingCommitMessage())
        .withObjectId(gitFileChange.getObjectId())
        .withProcessingCommitTimeMs(gitFileChange.getProcessingCommitTimeMs())
        .build();
  }

  private CommitAndPushRequest getCommitAndPushRequestFromOperationContext(GitOperationContext gitOperationContext) {
    final GitConfig gitConfig = gitOperationContext.getGitConfig();
    final GitCommitRequest gitCommitRequest = gitOperationContext.getGitCommitRequest();
    final String gitConnectorId = gitOperationContext.getGitConnectorId();
    CommitAndPushRequestBuilder<?, ?> builder = CommitAndPushRequest.builder();
    setGitBaseRequest(gitConfig, gitConnectorId, null, builder);
    return builder.commitMessage(gitConfig.getCommitMessage())
        .authorEmail(gitConfig.getAuthorEmailId())
        .authorName(gitConfig.getAuthorName())
        .forcePush(gitCommitRequest.isForcePush())
        .lastProcessedGitCommit(gitCommitRequest.getLastProcessedGitCommit())
        .pushOnlyIfHeadSeen(gitCommitRequest.isPushOnlyIfHeadSeen())
        .gitFileChanges(toApiServiceGitFileChange(gitCommitRequest.getGitFileChanges()))
        .build();
  }

  private void setGitBaseRequest(
      GitConfig gitConfig, String connectorId, String commitId, GitBaseRequestBuilder<?, ?> gitBaseRequestBuilder) {
    gitBaseRequestBuilder.repoUrl(gitConfig.getRepoUrl())
        .accountId(gitConfig.getAccountId())
        .authRequest(getAuthRequest(gitConfig))
        .repoType(gitConfig.getGitRepoType())
        .branch(gitConfig.getBranch())
        .disableUserGitConfig(gitConfig.getDisableUserGitConfig())
        .connectorId(connectorId)
        .commitId(commitId);
  }

  private List<GitFileChange> toApiServiceGitFileChange(List<software.wings.beans.yaml.GitFileChange> gitFileChanges) {
    return gitFileChanges.stream().map(this::toApiServiceGitFileChange).collect(Collectors.toList());
  }

  private GitFileChange toApiServiceGitFileChange(software.wings.beans.yaml.GitFileChange gitFileChange) {
    return GitFileChange.builder()
        .syncFromGit(gitFileChange.isSyncFromGit())
        .oldFilePath(gitFileChange.getOldFilePath())
        .filePath(gitFileChange.getFilePath())
        .fileContent(gitFileChange.getFileContent())
        .commitTimeMs(gitFileChange.getCommitTimeMs())
        .accountId(gitFileChange.getAccountId())
        .commitId(gitFileChange.getCommitId())
        .changeFromAnotherCommit(gitFileChange.isChangeFromAnotherCommit())
        .objectId(gitFileChange.getObjectId())
        .commitMessage(gitFileChange.getCommitMessage())
        .processingCommitId(gitFileChange.getProcessingCommitId())
        .processingCommitMessage(gitFileChange.getProcessingCommitMessage())
        .processingCommitTimeMs(gitFileChange.getProcessingCommitTimeMs())
        .changeType(gitFileChange.getChangeType())
        .build();
  }
}
