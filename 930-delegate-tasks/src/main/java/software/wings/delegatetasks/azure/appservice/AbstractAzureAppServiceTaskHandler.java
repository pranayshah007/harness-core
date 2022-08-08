/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_STATUS;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;

import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.artifact.ArtifactStreamAttributes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAzureAppServiceTaskHandler {
  public AzureTaskExecutionResponse executeTask(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient,
      ArtifactStreamAttributes artifactStreamAttributes) {
    try {
      AzureAppServiceTaskResponse azureAppServiceTaskResponse =
          (AzureAppServiceTaskParameters.AzureAppServiceTaskType.SLOT_SETUP
                  == azureAppServiceTaskParameters.getCommandType()
              || AzureAppServiceTaskParameters.AzureAppServiceTaskType.SLOT_ROLLBACK
                  == azureAppServiceTaskParameters.getCommandType())
          ? executeTaskInternal(
              azureAppServiceTaskParameters, azureConfig, logStreamingTaskClient, artifactStreamAttributes)
          : executeTaskInternal(azureAppServiceTaskParameters, azureConfig, logStreamingTaskClient);
      return handleAppServiceTaskResponse(azureAppServiceTaskResponse);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String message = AzureResourceUtility.getAzureCloudExceptionMessage(sanitizedException);
      if (azureAppServiceTaskParameters.isSyncTask()) {
        throw new InvalidRequestException(message, sanitizedException);
      }
      logErrorMsg(azureAppServiceTaskParameters, logStreamingTaskClient, sanitizedException, message);
      return failureAppServiceTaskResponse(message);
    }
  }

  private AzureTaskExecutionResponse handleAppServiceTaskResponse(
      AzureAppServiceTaskResponse azureAppServiceTaskResponse) {
    if (azureAppServiceTaskResponse instanceof AzureWebAppSlotSetupResponse) {
      AzureWebAppSlotSetupResponse azureWebAppSlotSetupResponse =
          (AzureWebAppSlotSetupResponse) azureAppServiceTaskResponse;
      String errorMsg = azureWebAppSlotSetupResponse.getErrorMsg();
      return errorMsg != null ? failureAppServiceTaskResponse(azureAppServiceTaskResponse, errorMsg)
                              : successAppServiceTaskResponse(azureAppServiceTaskResponse);
    }
    return successAppServiceTaskResponse(azureAppServiceTaskResponse);
  }

  private AzureTaskExecutionResponse successAppServiceTaskResponse(
      AzureAppServiceTaskResponse azureAppServiceTaskResponse) {
    return AzureTaskExecutionResponse.builder()
        .azureTaskResponse(azureAppServiceTaskResponse)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  private AzureTaskExecutionResponse failureAppServiceTaskResponse(
      AzureAppServiceTaskResponse azureAppServiceTaskResponse, String message) {
    return AzureTaskExecutionResponse.builder()
        .errorMessage(message)
        .azureTaskResponse(azureAppServiceTaskResponse)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .build();
  }

  private AzureTaskExecutionResponse failureAppServiceTaskResponse(String message) {
    return AzureTaskExecutionResponse.builder()
        .errorMessage(message)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .build();
  }

  protected void logErrorMsg(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      ILogStreamingTaskClient logStreamingTaskClient, Exception ex, String message) {
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(DEPLOYMENT_STATUS);
    logCallback.saveExecutionLog(message, ERROR, FAILURE);
    log.error(format("Exception: [%s] while processing azure app service task: [%s].", message,
                  azureAppServiceTaskParameters.getCommandType().name()),
        ex);
  }

  protected void markDeploymentStatusAsSuccess(
      AzureAppServiceTaskParameters azureAppServiceTaskParameters, ILogStreamingTaskClient logStreamingTaskClient) {
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(AzureConstants.DEPLOYMENT_STATUS);
    logCallback.saveExecutionLog(String.format("The following task - [%s] completed successfully",
                                     azureAppServiceTaskParameters.getCommandType().name()),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  protected abstract AzureAppServiceTaskResponse executeTaskInternal(
      AzureAppServiceTaskParameters azureAppServiceTaskParameters, AzureConfig azureConfig,
      ILogStreamingTaskClient logStreamingTaskClient);

  protected abstract AzureAppServiceTaskResponse executeTaskInternal(
      AzureAppServiceTaskParameters azureAppServiceTaskParameters, AzureConfig azureConfig,
      ILogStreamingTaskClient logStreamingTaskClient, ArtifactStreamAttributes artifactStreamAttributes);
}
