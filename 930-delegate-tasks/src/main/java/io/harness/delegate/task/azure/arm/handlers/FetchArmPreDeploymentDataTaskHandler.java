/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.FETCH_RESOURCE_GROUP_TEMPLATE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.delegate.task.azure.arm.AzureARMDeploymentService;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskParameters;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskResponse;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGResponse;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class FetchArmPreDeploymentDataTaskHandler extends AzureResourceCreationAbstractTaskHandler {
  @Inject protected AzureConnectorMapper azureConnectorMapper;
  @Inject private AzureARMDeploymentService azureARMDeploymentService;

  @Override
  public AzureResourceCreationTaskNGResponse executeTaskInternal(AzureResourceCreationTaskNGParameters taskNGParameters,
      String delegateId, String taskId, AzureLogCallbackProvider logCallback)
      throws IOException, TimeoutException, InterruptedException {
    AzureFetchArmPreDeploymentDataTaskParameters azureARMTaskNGParameters =
        (AzureFetchArmPreDeploymentDataTaskParameters) taskNGParameters;
    AzureConfig azureConfig = azureConnectorMapper.toAzureConfig(azureARMTaskNGParameters.getAzureConnectorDTO());
    return fetchPreDeploymentData(azureConfig, logCallback, azureARMTaskNGParameters);
  }

  private AzureFetchArmPreDeploymentDataTaskResponse fetchPreDeploymentData(AzureConfig azureConfig,
      AzureLogCallbackProvider logCallbackProvider,
      AzureFetchArmPreDeploymentDataTaskParameters azureARMTaskNGParameters) {
    AzureClientContext azureClientContext = new AzureClientContext(azureConfig,
        azureARMTaskNGParameters.getSubscriptionId(), azureARMTaskNGParameters.getResourceGroupName(), false);
    DeploymentResourceGroupContext context = DeploymentResourceGroupContext.builder()
                                                 .azureClientContext(azureClientContext)
                                                 .logStreamingTaskClient(logCallbackProvider)
                                                 .build();
    try {
      context.setRunningCommandUnit(AzureConstants.FETCH_RESOURCE_GROUP_TEMPLATE);
      LogCallback logCallback = logCallbackProvider.obtainLogCallback(FETCH_RESOURCE_GROUP_TEMPLATE);
      String existingResourceGroupTemplate =
          azureARMDeploymentService.exportExistingResourceGroupTemplate(context, logCallback);
      AzureARMPreDeploymentData preDeploymentData = AzureARMPreDeploymentData.builder()
                                                        .resourceGroup(azureARMTaskNGParameters.getResourceGroupName())
                                                        .subscriptionId(azureARMTaskNGParameters.getSubscriptionId())
                                                        .resourceGroupTemplateJson(existingResourceGroupTemplate)
                                                        .build();
      logCallback.saveExecutionLog(
          "Successfully fetched resource group template", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return AzureFetchArmPreDeploymentDataTaskResponse.builder()
          .azureARMPreDeploymentData(preDeploymentData)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception ex) {
      printDefaultFailureMsgForARMDeploymentUnits(
          ex, context.getLogStreamingTaskClient(), context.getRunningCommandUnit());
      throw ex;
    }
  }

  protected void printDefaultFailureMsgForARMDeploymentUnits(
      Exception ex, AzureLogCallbackProvider logStreamingTaskClient, final String runningCommandUnit) {
    if ((ex instanceof InvalidRequestException) || isBlank(runningCommandUnit)) {
      return;
    }

    if (AzureConstants.FETCH_RESOURCE_GROUP_TEMPLATE.equals(runningCommandUnit)) {
      printErrorMsg(logStreamingTaskClient, runningCommandUnit, format("%nError while fetch resource group template"));
    }
  }

  protected void printErrorMsg(
      AzureLogCallbackProvider logStreamingTaskClient, final String runningCommandUnit, final String errorMsg) {
    if (isBlank(runningCommandUnit)) {
      return;
    }
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(runningCommandUnit);
    logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
  }
}
