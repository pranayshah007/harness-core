/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;
import static io.harness.git.model.GitRepositoryType.YAML;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.GitClientV2;
import io.harness.git.model.FetchFilesByPathRequest;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class GitFetchTaskHelper {
  @Inject private GitClientV2 gitClientV2;
  @Inject private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;
  @Inject private SecretDecryptionService secretDecryptionService;

  public static String getCompleteFilePath(String folderPath, String fileKey) {
    if (isBlank(folderPath)) {
      return fileKey;
    }
    return folderPath + fileKey;
  }

  public void printFileNames(LogCallback executionLogCallback, List<String> filePaths, boolean closeLogStream) {
    executionLogCallback.saveExecutionLog("\nFetching following Files :");
    gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(filePaths, executionLogCallback, closeLogStream);
  }

  public FetchFilesResult fetchFileFromRepo(GitStoreDelegateConfig gitStoreDelegateConfig, List<String> filePaths,
      String accountId, GitConfigDTO gitConfigDTO) throws IOException {
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      return scmFetchFilesHelper.fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePaths);
    }
    SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
        gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
    FetchFilesByPathRequest fetchFilesByPathRequest =
        FetchFilesByPathRequest.builder()
            .authRequest(ngGitService.getAuthRequest(gitConfigDTO, sshSessionConfig))
            .filePaths(filePaths)
            .recursive(true)
            .accountId(accountId)
            .branch(gitStoreDelegateConfig.getBranch())
            .commitId(gitStoreDelegateConfig.getCommitId())
            .connectorId(gitStoreDelegateConfig.getConnectorName())
            .repoType(YAML)
            .repoUrl(gitConfigDTO.getUrl())
            .build();
    return gitClientV2.fetchFilesByPath(fetchFilesByPathRequest);
  }

  public void decryptGitStoreConfig(GitStoreDelegateConfig gitStoreDelegateConfig) {
    secretDecryptionService.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
        gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
        gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
  }

  public void decryptGitConfig(GitStoreDelegateConfig gitStoreDelegateConfig) {
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      decryptGitStoreConfig(gitStoreDelegateConfig);
    } else {
      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    }
  }
}
