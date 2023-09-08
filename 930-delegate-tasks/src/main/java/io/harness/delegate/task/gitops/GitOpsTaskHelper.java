/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitops;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesTaskHelper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.LogCallback;
import static io.harness.logging.LogLevel.INFO;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;
import java.io.IOException;
import static java.lang.String.format;
import java.util.List;
import software.wings.beans.LogColor;
import static software.wings.beans.LogHelper.color;
import software.wings.beans.LogWeight;

@Singleton
public class GitOpsTaskHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;

  public FetchFilesResult getFetchFilesResult(GitFetchFilesConfig gitFetchFilesConfig, String accountId,
      LogCallback logCallback, boolean closeLogStream) throws IOException {
    logCallback.saveExecutionLog(color(format("%nStarting Git Fetch Files"), LogColor.White, LogWeight.Bold));

    FetchFilesResult fetchFilesResult = fetchFilesFromRepo(gitFetchFilesConfig, logCallback, accountId, closeLogStream);

    if (fetchFilesResult.getFiles().isEmpty()) {
      return fetchFilesResult;
    }

    logCallback.saveExecutionLog(
        color(format("%nGit Fetch Files completed successfully."), LogColor.White, LogWeight.Bold), INFO);
    return fetchFilesResult;
  }

  private FetchFilesResult fetchFilesFromRepo(GitFetchFilesConfig gitFetchFilesConfig, LogCallback executionLogCallback,
      String accountId, boolean closeLogStream) throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = gitFetchFilesConfig.getGitStoreDelegateConfig();
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitStoreDelegateConfig.getGitConfigDTO().getUrl());
    String fetchTypeInfo = gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH
        ? "Branch: " + gitStoreDelegateConfig.getBranch()
        : "CommitId: " + gitStoreDelegateConfig.getCommitId();

    executionLogCallback.saveExecutionLog(fetchTypeInfo);

    List<String> filePathsToFetch = null;
    if (EmptyPredicate.isNotEmpty(gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths())) {
      filePathsToFetch = gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths();
      executionLogCallback.saveExecutionLog("\nFetching following Files :");
      gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(filePathsToFetch, executionLogCallback, closeLogStream);
    }

    FetchFilesResult gitFetchFilesResult;
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      executionLogCallback.saveExecutionLog("Using optimized file fetch");
      //decryption happening
      //decryptable entity - APIAccessDecryptableEntity
      secretDecryptionService.decrypt(
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
      if (gitFetchFilesConfig.isSucceedIfFileNotFound()) {
        gitFetchFilesResult =
            scmFetchFilesHelper.fetchAnyFilesFromRepoWithScm(gitStoreDelegateConfig, filePathsToFetch);
      } else {
        gitFetchFilesResult = scmFetchFilesHelper.fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePathsToFetch);
      }
    } else {
      //decryptable entity - gitConfigDTO
      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
      ScmConnector sc = ScmConnectorMapper.toGitConfigDTO(gitConfigDTO);
      SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
          gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
      gitFetchFilesResult =
          ngGitService.fetchFilesByPath(gitStoreDelegateConfig, accountId, sshSessionConfig, gitConfigDTO);
    }

    gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(
        executionLogCallback, gitFetchFilesResult.getFiles(), closeLogStream);

    return gitFetchFilesResult;
  }
}
