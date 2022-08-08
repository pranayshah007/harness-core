/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMDeploymentResponse;
import io.harness.exception.AzureClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public abstract class AbstractAzureARMTaskHandler {
  AzureTaskExecutionResponse executeTask(AzureARMTaskParameters azureARMTaskParameters, AzureConfig azureConfig,
      ILogStreamingTaskClient logStreamingTaskClient) {
    try {
      AzureARMTaskResponse azureARMTaskResponse =
          executeTaskInternal(azureARMTaskParameters, azureConfig, logStreamingTaskClient);

      return handleARMTaskResponse(azureARMTaskResponse);
    } catch (AzureClientException ex) {
      throw(AzureClientException) ExceptionMessageSanitizer.sanitizeException(ex);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String message = AzureResourceUtility.getAzureCloudExceptionMessage(sanitizedException);
      if (azureARMTaskParameters.isSyncTask()) {
        throw new InvalidRequestException(message, sanitizedException);
      }

      logErrorMsg(azureARMTaskParameters, sanitizedException, message);
      return handleFailureARMTaskResponse(message);
    }
  }

  private AzureTaskExecutionResponse handleARMTaskResponse(AzureARMTaskResponse azureARMTaskResponse) {
    return azureARMTaskResponse.getErrorMsg() == null
        ? successARMTaskResponse(azureARMTaskResponse)
        : failureARMTaskResponse(azureARMTaskResponse, azureARMTaskResponse.getErrorMsg());
  }

  private AzureTaskExecutionResponse successARMTaskResponse(AzureARMTaskResponse azureARMTaskResponse) {
    return AzureTaskExecutionResponse.builder()
        .azureTaskResponse(azureARMTaskResponse)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  private AzureTaskExecutionResponse failureARMTaskResponse(AzureARMTaskResponse azureARMTaskResponse, String message) {
    return AzureTaskExecutionResponse.builder()
        .errorMessage(message)
        .azureTaskResponse(azureARMTaskResponse)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .build();
  }

  private AzureTaskExecutionResponse handleFailureARMTaskResponse(String message) {
    return AzureTaskExecutionResponse.builder()
        .azureTaskResponse(AzureARMDeploymentResponse.builder().errorMsg(message).build())
        .errorMessage(message)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .build();
  }

  protected void logErrorMsg(AzureARMTaskParameters azureARMTaskParameters, Exception ex, String message) {
    log.error(format("Exception: [%s] while processing Azure ARM task: [%s].", message,
                  azureARMTaskParameters.getCommandType().name()),
        ex);
  }

  protected void printDefaultFailureMsgForARMDeploymentUnits(
      Exception ex, ILogStreamingTaskClient logStreamingTaskClient, final String runningCommandUnit) {
    if ((ex instanceof InvalidRequestException) || isBlank(runningCommandUnit)) {
      return;
    }

    if (AzureConstants.EXECUTE_ARM_DEPLOYMENT.equals(runningCommandUnit)) {
      printErrorMsg(logStreamingTaskClient, runningCommandUnit, format("%nError while executing ARM deployment"));
    }

    if (AzureConstants.ARM_DEPLOYMENT_STEADY_STATE.equals(runningCommandUnit)) {
      printErrorMsg(logStreamingTaskClient, runningCommandUnit, format("%nError during ARM deployment steady check"));
    }

    if (AzureConstants.ARM_DEPLOYMENT_OUTPUTS.equals(runningCommandUnit)) {
      printErrorMsg(logStreamingTaskClient, runningCommandUnit, format("%nError while getting ARM deployment outputs"));
    }
  }

  protected void printErrorMsg(
      ILogStreamingTaskClient logStreamingTaskClient, final String runningCommandUnit, final String errorMsg) {
    if (isBlank(runningCommandUnit)) {
      return;
    }
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(runningCommandUnit);
    logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
  }

  protected abstract AzureARMTaskResponse executeTaskInternal(AzureARMTaskParameters azureARMTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient);
}
