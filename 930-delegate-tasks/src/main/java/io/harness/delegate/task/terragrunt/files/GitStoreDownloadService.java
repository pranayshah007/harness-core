/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientV2;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.GitRepositoryType;
import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@OwnedBy(CDP)
public class GitStoreDownloadService implements FileStoreDownloadService {
  @Inject private GitClientV2 gitClient;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;
  @Inject private NGGitService ngGitService;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;

  @Override
  public void download(
      StoreDelegateConfig storeConfig, String accountId, String outputDirectory, LogCallback logCallback) {
    validateGitStore(storeConfig);
    GitStoreDelegateConfig gitStoreConfig = (GitStoreDelegateConfig) storeConfig;
    if (isEmpty(gitStoreConfig.getPaths())) {
      return;
    }

    GitConfigDTO gitConfigDTO = (GitConfigDTO) gitStoreConfig.getGitConfigDTO();
    SshSessionConfig sshSessionConfig = null;
    if (gitConfigDTO.getGitAuthType() == SSH) {
      sshSessionConfig = getSshSessionConfig(gitStoreConfig);
    }

    gitClient.cloneRepoAndCopyToDestDir(DownloadFilesRequest.builder()
                                            .branch(gitStoreConfig.getBranch())
                                            .commitId(gitStoreConfig.getCommitId())
                                            .filePaths(gitStoreConfig.getPaths())
                                            .connectorId(gitStoreConfig.getConnectorName())
                                            .repoUrl(gitConfigDTO.getUrl())
                                            .accountId(accountId)
                                            .recursive(true)
                                            .authRequest(ngGitService.getAuthRequest(gitConfigDTO, sshSessionConfig))
                                            .repoType(GitRepositoryType.TERRAGRUNT)
                                            .destinationDirectory(outputDirectory)
                                            .build());
  }

  @Override
  public List<String> fetchFiles(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException {
    validateGitStore(storeConfig);
    GitStoreDelegateConfig gitStoreConfig = (GitStoreDelegateConfig) storeConfig;
    if (isEmpty(gitStoreConfig.getPaths())) {
      return emptyList();
    }

    if (gitStoreConfig.isOptimizedFilesFetch()) {
      return fetchFilesUsingScm(storeConfig, outputDirectory, logCallback);
    } else {
      return fetchFilesUsingGitClient(storeConfig, accountId, outputDirectory);
    }
  }

  private List<String> fetchFilesUsingGitClient(
      StoreDelegateConfig storeConfig, String accountId, String outputDirectory) throws IOException {
    GitStoreDelegateConfig gitStoreConfig = (GitStoreDelegateConfig) storeConfig;
    GitConfigDTO gitConfigDTO = (GitConfigDTO) gitStoreConfig.getGitConfigDTO();
    SshSessionConfig sshSessionConfig = null;
    if (gitConfigDTO.getGitAuthType() == SSH) {
      sshSessionConfig = getSshSessionConfig(gitStoreConfig);
    }

    gitClient.downloadFiles(DownloadFilesRequest.builder()
                                .branch(gitStoreConfig.getBranch())
                                .commitId(gitStoreConfig.getCommitId())
                                .filePaths(gitStoreConfig.getPaths())
                                .connectorId(gitStoreConfig.getConnectorName())
                                .repoUrl(gitConfigDTO.getUrl())
                                .accountId(accountId)
                                .recursive(true)
                                .authRequest(ngGitService.getAuthRequest(gitConfigDTO, sshSessionConfig))
                                .repoType(GitRepositoryType.TERRAFORM)
                                .destinationDirectory(outputDirectory)
                                .build());

    return createFilesPaths(gitStoreConfig, outputDirectory);
  }

  private List<String> fetchFilesUsingScm(
      StoreDelegateConfig storeConfig, String outputDirectory, LogCallback logCallback) {
    GitStoreDelegateConfig gitStoreConfig = (GitStoreDelegateConfig) storeConfig;
    scmFetchFilesHelper.downloadFilesUsingScm(outputDirectory, gitStoreConfig, logCallback);
    return createFilesPaths(gitStoreConfig, outputDirectory);
  }

  private void validateGitStore(StoreDelegateConfig storeConfig) {
    if (GIT != storeConfig.getType()) {
      throw new InvalidArgumentsException(
          Pair.of("storeConfig", format("Invalid store config '%s', expected '%s'", storeConfig.getType(), GIT)));
    }
  }

  private SshSessionConfig getSshSessionConfig(GitStoreDelegateConfig gitStoreDelegateConfig) {
    if (gitStoreDelegateConfig.getSshKeySpecDTO() == null) {
      throw new InvalidRequestException(
          format("SSHKeySpecDTO is null for connector %s", gitStoreDelegateConfig.getConnectorName()));
    }
    return sshSessionConfigMapper.getSSHSessionConfig(
        gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
  }

  private List<String> createFilesPaths(GitStoreDelegateConfig storeConfig, String baseDirectory) {
    List<String> filePaths = new ArrayList<>();
    String absolutePath = Paths.get(baseDirectory).toAbsolutePath().toString();
    for (String paths : storeConfig.getPaths()) {
      filePaths.add(absolutePath + "/" + paths);
    }

    return filePaths;
  }
}
