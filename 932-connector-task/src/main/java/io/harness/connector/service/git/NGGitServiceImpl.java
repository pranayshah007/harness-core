/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.service.git;

import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;
import static io.harness.git.Constants.DEFAULT_FETCH_IDENTIFIER;
import static io.harness.git.model.GitRepositoryType.YAML;
import static io.harness.shell.SshSessionFactory.getSSHSession;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientV2;
import io.harness.git.UsernamePasswordAuthRequest;
import io.harness.git.model.AuthRequest;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.FetchFilesByPathRequest;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitRepositoryType;
import io.harness.git.model.JgitSshAuthRequest;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.SshFactory;
import io.harness.shell.ssh.client.jsch.JschConnection;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class NGGitServiceImpl implements NGGitService {
  @Inject private GitClientV2 gitClientV2;

  @Override
  public void validate(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig) {
    final GitBaseRequest gitBaseRequest = GitBaseRequest.builder().build();
    setGitBaseRequest(gitConfig, accountId, gitBaseRequest, YAML, sshSessionConfig, true);
    gitClientV2.validate(gitBaseRequest);
  }
  @Override
  public void validateOrThrow(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig) {
    final GitBaseRequest gitBaseRequest = GitBaseRequest.builder().build();
    setGitBaseRequest(gitConfig, accountId, gitBaseRequest, YAML, sshSessionConfig, true);
    gitClientV2.validateOrThrow(gitBaseRequest);
  }

  @VisibleForTesting
  void setGitBaseRequest(GitConfigDTO gitConfig, String accountId, GitBaseRequest gitBaseRequest,
      GitRepositoryType repositoryType, SshSessionConfig sshSessionConfig, boolean overrideFromGitConfig) {
    gitBaseRequest.setAuthRequest(getAuthRequest(gitConfig, sshSessionConfig));
    gitBaseRequest.setRepoType(repositoryType);
    gitBaseRequest.setAccountId(accountId);

    if (overrideFromGitConfig) {
      gitBaseRequest.setBranch(gitConfig.getBranchName());
      gitBaseRequest.setRepoUrl(gitConfig.getUrl());
    }
  }

  public AuthRequest getAuthRequest(GitConfigDTO gitConfig, SshSessionConfig sshSessionConfig) {
    switch (gitConfig.getGitAuthType()) {
      case SSH:
        return JgitSshAuthRequest.builder().factory(getSshSessionFactory(sshSessionConfig)).build();
      case HTTP:
        GitHTTPAuthenticationDTO httpAuthenticationDTO = (GitHTTPAuthenticationDTO) gitConfig.getGitAuth();
        String userName = getSecretAsStringFromPlainTextOrSecretRef(
            httpAuthenticationDTO.getUsername(), httpAuthenticationDTO.getUsernameRef());
        return UsernamePasswordAuthRequest.builder()
            .username(userName)
            .password(httpAuthenticationDTO.getPasswordRef().getDecryptedValue())
            .build();
      default:
        throw new InvalidRequestException("Unknown auth type.");
    }
  }

  private SshSessionFactory getSshSessionFactory(SshSessionConfig sshSessionConfig) {
    return new JschConfigSessionFactory() {
      @Override
      protected Session createSession(OpenSshConfig.Host hc, String user, String host, int port, FS fs)
          throws JSchException {
        sshSessionConfig.setPort(port); // use port from repo URL
        sshSessionConfig.setHost(host);
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

  @Override
  public CommitAndPushResult commitAndPush(GitConfigDTO gitConfig, CommitAndPushRequest commitAndPushRequest,
      String accountId, SshSessionConfig sshSessionConfig, boolean overrideFromGitConfig) {
    setGitBaseRequest(gitConfig, accountId, commitAndPushRequest, YAML, sshSessionConfig, overrideFromGitConfig);
    return gitClientV2.commitAndPush(commitAndPushRequest);
  }

  @Override
  public FetchFilesResult fetchFilesByPath(GitStoreDelegateConfig gitStoreDelegateConfig, String accountId,
      SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException {
    return fetchFilesByPath(
        DEFAULT_FETCH_IDENTIFIER, gitStoreDelegateConfig, accountId, sshSessionConfig, gitConfigDTO);
  }

  @Override
  public FetchFilesResult fetchFilesByPath(String identifier, GitStoreDelegateConfig gitStoreDelegateConfig,
      String accountId, SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException {
    FetchFilesByPathRequest fetchFilesByPathRequest =
        FetchFilesByPathRequest.builder()
            .authRequest(getAuthRequest(gitConfigDTO, sshSessionConfig))
            .filePaths(gitStoreDelegateConfig.getPaths())
            .recursive(true)
            .accountId(accountId)
            .branch(gitStoreDelegateConfig.getBranch())
            .commitId(gitStoreDelegateConfig.getCommitId())
            .connectorId(gitStoreDelegateConfig.getConnectorId() == null ? gitStoreDelegateConfig.getConnectorName()
                                                                         : gitStoreDelegateConfig.getConnectorId())
            .repoType(YAML)
            .repoUrl(gitConfigDTO.getUrl())
            .build();
    return gitClientV2.fetchFilesByPath(identifier, fetchFilesByPathRequest);
  }

  @Override
  public void downloadFiles(GitStoreDelegateConfig gitStoreDelegateConfig, String destinationDirectory,
      String accountId, SshSessionConfig sshSessionConfig, GitConfigDTO gitConfigDTO) throws IOException {
    DownloadFilesRequest downloadFilesRequest =
        DownloadFilesRequest.builder()
            .authRequest(getAuthRequest(gitConfigDTO, sshSessionConfig))
            .filePaths(gitStoreDelegateConfig.getPaths())
            .recursive(true)
            .accountId(accountId)
            .branch(gitStoreDelegateConfig.getBranch())
            .commitId(gitStoreDelegateConfig.getCommitId())
            .connectorId(gitStoreDelegateConfig.getConnectorId() == null ? gitStoreDelegateConfig.getConnectorName()
                                                                         : gitStoreDelegateConfig.getConnectorId())
            .repoType(YAML)
            .repoUrl(gitConfigDTO.getUrl())
            .destinationDirectory(destinationDirectory)
            .build();
    gitClientV2.downloadFiles(downloadFilesRequest);
  }
}
