/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.sam;

import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.sam.command.AwsSamCommandUnitConstants;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
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
import io.harness.delegate.task.aws.sam.AwsSamGitFetchFileConfig;
import io.harness.delegate.task.aws.sam.AwsSamGitFetchFilesResult;
import io.harness.delegate.task.aws.sam.request.AwsSamGitFetchRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamGitFetchResponse;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.secret.SecretSanitizerThreadLocal;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamGitFetchTask extends AbstractDelegateRunnableTask {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;
  public AwsSamGitFetchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }
  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    AwsSamGitFetchRequest awsSamGitFetchRequest = (AwsSamGitFetchRequest) parameters;
    LogCallback executionLogCallback =
        new NGDelegateLogCallback(getLogStreamingTaskClient(), AwsSamCommandUnitConstants.fetchFiles.toString(),
            awsSamGitFetchRequest.isShouldOpenLogStream(), commandUnitsProgress);
    try {
      log.info("Running AWS SAM GitFetchFilesTask for activityId {}", awsSamGitFetchRequest.getActivityId());

      AwsSamGitFetchFileConfig awsSamGitFetchFileConfig = awsSamGitFetchRequest.getAwsSamGitFetchFileConfig();
      executionLogCallback.saveExecutionLog(
          color(format("Fetching %s AWS SAM Manifest Files with identifier: %s",
                    awsSamGitFetchFileConfig.getManifestType(), awsSamGitFetchFileConfig.getIdentifier()),
              White, Bold));
      AwsSamGitFetchFilesResult awsSamGitFetchFilesResult =
          fetchManifestFile(awsSamGitFetchFileConfig, executionLogCallback, awsSamGitFetchRequest.getAccountId());
      executionLogCallback.saveExecutionLog(
          color(format("%nFetch AWS SAM Manifest Files completed successfully..%n"), LogColor.White, LogWeight.Bold),
          INFO);
      executionLogCallback.saveExecutionLog("Done..\n", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return AwsSamGitFetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .awsSamGitFetchFilesResult(awsSamGitFetchFilesResult)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in Git Fetch Files Task", sanitizedException);
      executionLogCallback.saveExecutionLog(
          color(format("%n File fetch failed with error: %s", ExceptionUtils.getMessage(sanitizedException)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private AwsSamGitFetchFilesResult fetchManifestFile(AwsSamGitFetchFileConfig awsSamGitFetchFileConfig,
      LogCallback executionLogCallback, String accountId) throws Exception {
    GitStoreDelegateConfig gitStoreDelegateConfig = awsSamGitFetchFileConfig.getGitStoreDelegateConfig();
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
    FetchFilesResult templateFileResult = null;
    Optional<FetchFilesResult> optionalConfigFileResult = null;
    try {
      if (EmptyPredicate.isNotEmpty(gitStoreDelegateConfig.getPaths())) {
        String folderPath = awsSamGitFetchFileConfig.getGitStoreDelegateConfig().getPaths().get(0);
        if (EmptyPredicate.isNotEmpty(awsSamGitFetchFileConfig.getTemplateFilePath())) {
          templateFileResult = fetchManifestFileFromRepo(gitStoreDelegateConfig, folderPath,
              awsSamGitFetchFileConfig.getTemplateFilePath(), accountId, gitConfigDTO, executionLogCallback);
        } else {
          templateFileResult = fetchTemplateFileInPriorityOrder(
              gitStoreDelegateConfig, folderPath, accountId, gitConfigDTO, executionLogCallback);
        }
        String configFilePath = EmptyPredicate.isNotEmpty(awsSamGitFetchFileConfig.getConfigFilePath())
            ? awsSamGitFetchFileConfig.getConfigFilePath()
            : "samconfig.toml";
        optionalConfigFileResult = fetchAwsSamManifestFileFromRepo(
            gitStoreDelegateConfig, folderPath, configFilePath, accountId, gitConfigDTO, executionLogCallback);
      }

    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String msg = "Exception in processing GitFetchFilesTask. " + sanitizedException.getMessage();
      if (sanitizedException.getCause() instanceof NoSuchFileException) {
        log.error(msg, sanitizedException);
        executionLogCallback.saveExecutionLog(
            color(format("No manifest file found with identifier: %s.", awsSamGitFetchFileConfig.getIdentifier()), Red),
            ERROR);
      }
      throw sanitizedException;
    }
    return AwsSamGitFetchFilesResult.builder()
        .templateFileResult(templateFileResult)
        .configFileResult(optionalConfigFileResult.get())
        .build();
  }

  private FetchFilesResult fetchTemplateFileInPriorityOrder(GitStoreDelegateConfig gitStoreDelegateConfig,
      String folderPath, String accountId, GitConfigDTO gitConfigDTO, LogCallback executionLogCallback) {
    Optional<FetchFilesResult> awsSamManifestFileResult;
    awsSamManifestFileResult = fetchAwsSamManifestFileFromRepo(
        gitStoreDelegateConfig, folderPath, "template1.yaml", accountId, gitConfigDTO, executionLogCallback);
    if (awsSamManifestFileResult.isPresent()) {
      return awsSamManifestFileResult.get();
    }

    awsSamManifestFileResult = fetchAwsSamManifestFileFromRepo(
        gitStoreDelegateConfig, folderPath, "template.yml", accountId, gitConfigDTO, executionLogCallback);
    if (awsSamManifestFileResult.isPresent()) {
      return awsSamManifestFileResult.get();
    }

    executionLogCallback.saveExecutionLog(
        color(format("No manifest file found with identifier: %s.", gitStoreDelegateConfig.getManifestId()), Red),
        ERROR);
    throw NestedExceptionUtils.hintWithExplanationException(
        format("please add a AWS SAM template file inside provided path: %s", folderPath),
        format("not able to find a AWS SAM template file "
                + "(template.yaml/template.yml) inside provided path: %s",
            folderPath),
        new InvalidRequestException("Not able to fetch AWS SAM template file"));
  }

  private Optional<FetchFilesResult> fetchAwsSamManifestFileFromRepo(GitStoreDelegateConfig gitStoreDelegateConfig,
      String folderPath, String filePath, String accountId, GitConfigDTO gitConfigDTO,
      LogCallback executionLogCallback) {
    try {
      return Optional.of(fetchManifestFileFromRepo(
          gitStoreDelegateConfig, folderPath, filePath, accountId, gitConfigDTO, executionLogCallback));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private FetchFilesResult fetchManifestFileFromRepo(GitStoreDelegateConfig gitStoreDelegateConfig, String folderPath,
      String filePath, String accountId, GitConfigDTO gitConfigDTO, LogCallback executionLogCallback)
      throws IOException {
    filePath = GitFetchTaskHelper.getCompleteFilePath(folderPath, filePath);
    List<String> filePaths = Collections.singletonList(filePath);
    gitFetchTaskHelper.printFileNames(executionLogCallback, filePaths, false);
    return gitFetchTaskHelper.fetchFileFromRepo(gitStoreDelegateConfig, filePaths, accountId, gitConfigDTO);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
