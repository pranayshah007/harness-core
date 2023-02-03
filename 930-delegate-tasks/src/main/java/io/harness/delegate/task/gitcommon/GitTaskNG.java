/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitcommon;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.secret.SecretSanitizerThreadLocal;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class GitTaskNG extends AbstractDelegateRunnableTask {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;

  public GitTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      GitTaskNGRequest gitTaskNGRequest = (GitTaskNGRequest) parameters;

      log.info("Running Git Fetch Task for activityId {}", gitTaskNGRequest.getActivityId());

      LogCallback executionLogCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(),
          gitTaskNGRequest.getCommandUnitName(), gitTaskNGRequest.isShouldOpenLogStream(), commandUnitsProgress);

      List<GitFetchFilesResult> gitFetchFilesResults = new ArrayList<>();
      if (CollectionUtils.isNotEmpty(gitTaskNGRequest.getGitRequestFileConfigs())) {
        for (GitRequestFileConfig gitRequestFileConfig : gitTaskNGRequest.getGitRequestFileConfigs()) {
          FetchFilesResult fetchFilesResult =
              fetchManifestFile(gitRequestFileConfig, executionLogCallback, gitTaskNGRequest.getAccountId());
          GitFetchFilesResult gitFetchFilesResult =
              GitFetchFilesResult.builder()
                  .files(fetchFilesResult != null ? fetchFilesResult.getFiles() : Lists.newArrayList())
                  .commitResult(fetchFilesResult != null ? fetchFilesResult.getCommitResult() : null)
                  .manifestType(gitRequestFileConfig.getManifestType())
                  .identifier(gitRequestFileConfig.getIdentifier())
                  .build();
          gitFetchFilesResults.add(gitFetchFilesResult);
        }
      }
      executionLogCallback.saveExecutionLog(
          color(format("%nFetched all manifests successfully..%n"), LogColor.White, LogWeight.Bold), INFO,
          CommandExecutionStatus.SUCCESS);

      return GitTaskNGResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .gitFetchFilesResults(gitFetchFilesResults)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in Git Fetch Files Task", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private FetchFilesResult fetchManifestFile(
      GitRequestFileConfig gitRequestFileConfig, LogCallback executionLogCallback, String accountId) throws Exception {
    executionLogCallback.saveExecutionLog(
        color(format("Fetching %s config file with identifier: %s", gitRequestFileConfig.getManifestType(),
                  gitRequestFileConfig.getIdentifier()),
            White, Bold));
    GitStoreDelegateConfig gitStoreDelegateConfig = gitRequestFileConfig.getGitStoreDelegateConfig();
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitStoreDelegateConfig.getGitConfigDTO().getUrl());
    String fetchTypeInfo;
    GitConfigDTO gitConfigDTO = null;
    if (gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH) {
      fetchTypeInfo = "Branch: " + gitStoreDelegateConfig.getBranch();
    } else {
      fetchTypeInfo = "Commit: " + gitStoreDelegateConfig.getCommitId();
    }
    executionLogCallback.saveExecutionLog(fetchTypeInfo);
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      executionLogCallback.saveExecutionLog("Using optimized file fetch ");
      gitFetchTaskHelper.decryptGitStoreConfig(gitStoreDelegateConfig);
    } else {
      gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    }
    FetchFilesResult filesResult = null;
    try {
      if (isNotEmpty(gitStoreDelegateConfig.getPaths())) {
        String filePath = gitRequestFileConfig.getGitStoreDelegateConfig().getPaths().get(0);

        List<String> filePaths = Collections.singletonList(filePath);
        gitFetchTaskHelper.printFileNames(executionLogCallback, filePaths, false);
        try {
          filesResult =
              gitFetchTaskHelper.fetchFileFromRepo(gitStoreDelegateConfig, filePaths, accountId, gitConfigDTO);
        } catch (Exception e) {
          throw NestedExceptionUtils.hintWithExplanationException(
              format(
                  "Please checks files %s configured Manifest section in Harness Service are correct. Check if git credentials are correct.",
                  filePaths),
              format("Error while fetching files %s from Git repo %s", filePaths,
                  gitRequestFileConfig.getGitStoreDelegateConfig().getGitConfigDTO().getUrl()),
              e);
        }
      }
      executionLogCallback.saveExecutionLog(
          color(format("%nFetch Config File completed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
      executionLogCallback.saveExecutionLog("Done..\n", LogLevel.INFO);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String msg = "Exception in processing GitFetchFilesTask. " + sanitizedException.getMessage();
      if (sanitizedException.getCause() instanceof NoSuchFileException) {
        log.error(msg, sanitizedException);
        executionLogCallback.saveExecutionLog(
            color(format("No manifest file found with identifier: %s.", gitRequestFileConfig.getIdentifier()), Red),
            ERROR);
      }
      executionLogCallback.saveExecutionLog(msg, ERROR, CommandExecutionStatus.FAILURE);
      throw sanitizedException;
    }
    checkIfFilesContentAreNotEmpty(
        filesResult, gitRequestFileConfig.getGitStoreDelegateConfig().getGitConfigDTO().getUrl());
    return filesResult;
  }

  public void checkIfFilesContentAreNotEmpty(FetchFilesResult filesResult, String gitUrl) {
    for (GitFile file : filesResult.getFiles()) {
      String fileContent = file.getFileContent();
      if (isEmpty(fileContent)) {
        Throwable e = new InvalidRequestException(format("EMPTY FILE CONTENT in %s", file.getFilePath()));
        throw NestedExceptionUtils.hintWithExplanationException(
            format("Please check the file content of the file %s", file.getFilePath()),
            format("The following file %s in Git Repo %s has empty content", file.getFilePath(), gitUrl), e);
      }
    }
  }
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
