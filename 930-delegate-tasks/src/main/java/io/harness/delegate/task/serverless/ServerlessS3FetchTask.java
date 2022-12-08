/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.NO_SERVERLESS_MANIFEST_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.NO_SERVERLESS_MANIFEST_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.NO_SERVERLESS_MANIFEST_HINT;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.serverless.ServerlessS3FetchFileResult;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.request.ServerlessS3FetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessS3FetchResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.serverless.ServerlessCommandUnitConstants;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ServerlessS3FetchTask extends AbstractDelegateRunnableTask {
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;

  private static final String WORKING_DIR_BASE = "./repository/serverless/";

  public ServerlessS3FetchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
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
    ServerlessS3FetchRequest serverlessS3FetchRequest = (ServerlessS3FetchRequest) parameters;
    log.info("Running Serverless S3FetchFilesTask for activityId {}", serverlessS3FetchRequest.getActivityId());
    String workingDirectory = Paths.get(WORKING_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
                                  .normalize()
                                  .toAbsolutePath()
                                  .toString();

    return getS3FetchResponse(serverlessS3FetchRequest, workingDirectory);
  }

  public ServerlessS3FetchResponse getS3FetchResponse(
      ServerlessS3FetchRequest serverlessS3FetchRequest, String workingDirectory) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    LogCallback executionLogCallback =
        new NGDelegateLogCallback(getLogStreamingTaskClient(), ServerlessCommandUnitConstants.fetchFiles.toString(),
            serverlessS3FetchRequest.isShouldOpenLogStream(), commandUnitsProgress);
    try {
      createDirectoryIfDoesNotExist(workingDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

      ServerlessS3FetchFileConfig serverlessS3FetchFileConfig =
          serverlessS3FetchRequest.getServerlessS3FetchFileConfig();
      executionLogCallback.saveExecutionLog(
          color(format("Fetching %s config file with identifier: %s", serverlessS3FetchFileConfig.getManifestType(),
                    serverlessS3FetchFileConfig.getIdentifier()),
              White, Bold));

      S3StoreDelegateConfig s3StoreDelegateConfig = serverlessS3FetchFileConfig.getS3StoreDelegateConfig();

      serverlessTaskHelperBase.downloadFilesFromS3(s3StoreDelegateConfig, executionLogCallback, workingDirectory);

      ServerlessS3FetchFileResult serverlessS3FetchFileResult =
          fetchS3ManifestContentFromDirectory(serverlessS3FetchFileConfig, workingDirectory, executionLogCallback);

      executionLogCallback.saveExecutionLog(
          color(format("%nFetch Config File completed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
      executionLogCallback.saveExecutionLog("Done..\n", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return ServerlessS3FetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .serverlessS3FetchFileResult(serverlessS3FetchFileResult)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in S3 Fetch Files Task", sanitizedException);
      executionLogCallback.saveExecutionLog(
          color(format("%n File fetch failed with error: %s", ExceptionUtils.getMessage(sanitizedException)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      if (e instanceof HintException) {
        throw new TaskNGDataException(
            UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
      } else {
        return ServerlessS3FetchResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      }
    } finally {
      serverlessTaskHelperBase.cleanup(workingDirectory);
    }
  }

  private ServerlessS3FetchFileResult fetchS3ManifestContentFromDirectory(
      ServerlessS3FetchFileConfig serverlessS3FetchFileConfig, String workingDirectory,
      LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(format("Fetching %s config file with identifier: %s",
        serverlessS3FetchFileConfig.getManifestType(), serverlessS3FetchFileConfig.getIdentifier(), White, Bold));

    ServerlessS3FetchFileResult serverlessS3FetchFileResult = null;
    try {
      if (EmptyPredicate.isNotEmpty(serverlessS3FetchFileConfig.getConfigOverridePath())) {
        serverlessS3FetchFileResult = fetchS3ManifestFileFromDirectory(
            workingDirectory, serverlessS3FetchFileConfig.getConfigOverridePath(), executionLogCallback);
      } else {
        serverlessS3FetchFileResult = fetchS3ManifestFileInPriorityOrder(workingDirectory, executionLogCallback);
      }
      return serverlessS3FetchFileResult;
    } catch (Exception ex) {
      throw new InvalidRequestException(format("Error while fetching file content from S3 zip file"), ex);
    }
  }

  ServerlessS3FetchFileResult fetchS3ManifestFileInPriorityOrder(
      String workingDirectory, LogCallback executionLogCallback) throws IOException {
    Optional<ServerlessS3FetchFileResult> serverlessS3FetchFileResult;
    serverlessS3FetchFileResult =
        fetchServerlessS3ManifestFileFromDirectory(workingDirectory, "serverless.yaml", executionLogCallback);
    if (serverlessS3FetchFileResult.isPresent()) {
      return serverlessS3FetchFileResult.get();
    }
    serverlessS3FetchFileResult =
        fetchServerlessS3ManifestFileFromDirectory(workingDirectory, "serverless.yml", executionLogCallback);
    if (serverlessS3FetchFileResult.isPresent()) {
      return serverlessS3FetchFileResult.get();
    }
    serverlessS3FetchFileResult =
        fetchServerlessS3ManifestFileFromDirectory(workingDirectory, "serverless.json", executionLogCallback);
    if (serverlessS3FetchFileResult.isPresent()) {
      return serverlessS3FetchFileResult.get();
    }
    executionLogCallback.saveExecutionLog(color(format("No Serverless manifest file in S3 zip"), Red), ERROR);
    throw NestedExceptionUtils.hintWithExplanationException(format(NO_SERVERLESS_MANIFEST_HINT, workingDirectory),
        format(NO_SERVERLESS_MANIFEST_EXPLANATION, workingDirectory),
        new ServerlessCommandExecutionException(NO_SERVERLESS_MANIFEST_FAILED));
  }

  private ServerlessS3FetchFileResult fetchS3ManifestFileFromDirectory(
      String workingDirectory, String fileName, LogCallback executionLogCallback) throws IOException {
    File file = new File(workingDirectory, fileName);
    executionLogCallback.saveExecutionLog(
        color(format("Fetching [%s] manifest file from S3 zip file", fileName), White));
    if (!file.exists()) {
      executionLogCallback.saveExecutionLog(color(format("No [%s] manifest file in S3 zip file ", fileName), White));
      throw new InvalidRequestException(format("file [%s] doest exists in S3 zip file", fileName));
    }
    executionLogCallback.saveExecutionLog(
        color(format("Successfully fetched [%s] manifest file in S3 zip file ", fileName), White, Bold));
    return ServerlessS3FetchFileResult.builder()
        .fileContent(FileUtils.readFileToString(file))
        .filePath(fileName)
        .build();
  }

  private Optional<ServerlessS3FetchFileResult> fetchServerlessS3ManifestFileFromDirectory(
      String workingDirectory, String fileName, LogCallback executionLogCallback) {
    try {
      return Optional.of(fetchS3ManifestFileFromDirectory(workingDirectory, fileName, executionLogCallback));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
