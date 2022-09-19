/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.delegate.task.azure.arm.AzureARMDeploymentService;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData.AzureARMPreDeploymentDataBuilder;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGResponse;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGResponse;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
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
public class AzureCreateArmResourceTaskHandler extends AzureResourceCreationAbstractTaskHandler {
  @Inject protected AzureConnectorMapper azureConnectorMapper;
  @Inject private AzureARMDeploymentService azureARMDeploymentService;

  @Override
  public AzureResourceCreationTaskNGResponse executeTaskInternal(AzureResourceCreationTaskNGParameters taskNGParameters,
      String delegateId, String taskId, AzureLogCallbackProvider logCallback)
      throws IOException, TimeoutException, InterruptedException {
    AzureARMTaskNGParameters azureARMTaskNGParameters = (AzureARMTaskNGParameters) taskNGParameters;
    ARMScopeType deploymentScope = azureARMTaskNGParameters.getDeploymentScope();
    AzureConfig azureConfig = azureConnectorMapper.toAzureConfig(azureARMTaskNGParameters.getAzureConnectorDTO());
    switch (deploymentScope) {
      case RESOURCE_GROUP:
        return deployAtResourceGroupScope(azureConfig, logCallback, azureARMTaskNGParameters);
      case SUBSCRIPTION:
        return deployAtSubscriptionScope(azureConfig, logCallback, azureARMTaskNGParameters);
      case MANAGEMENT_GROUP:
        return deployAtManagementGroupScope(azureConfig, logCallback, azureARMTaskNGParameters);
      case TENANT:
        return deployAtTenantScope(azureConfig, logCallback, azureARMTaskNGParameters);
      default:
        throw new IllegalArgumentException(format("Invalid Azure ARM deployment scope: [%s]", deploymentScope));
    }
  }

  private AzureResourceCreationTaskNGResponse deployAtResourceGroupScope(AzureConfig azureConfig,
      AzureLogCallbackProvider logCallback, AzureARMTaskNGParameters azureARMTaskNGParameters) {
    AzureARMPreDeploymentDataBuilder preDeploymentData =
        AzureARMPreDeploymentData.builder()
            .resourceGroup(azureARMTaskNGParameters.getResourceGroupName())
            .subscriptionId(azureARMTaskNGParameters.getSubscriptionId());

    DeploymentResourceGroupContext context =
        azureARMBaseHelper.toDeploymentResourceGroupContext(azureARMTaskNGParameters, azureConfig, logCallback);
    try {
      if (!azureARMTaskNGParameters.isRollback()) {
        azureARMDeploymentService.validateTemplate(context);
        String existingResourceGroupTemplate = azureARMDeploymentService.exportExistingResourceGroupTemplate(context);
        preDeploymentData.resourceGroupTemplateJson(existingResourceGroupTemplate);
      }
      String outPuts = azureARMDeploymentService.deployAtResourceGroupScope(context);
      return AzureARMTaskNGResponse.builder()
          .outputs(outPuts)
          .azureARMPreDeploymentData(preDeploymentData.build())
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception ex) {
      printDefaultFailureMsgForARMDeploymentUnits(
          ex, context.getLogStreamingTaskClient(), context.getRunningCommandUnit());
      throw ex;
    }
  }

  private AzureResourceCreationTaskNGResponse deployAtSubscriptionScope(AzureConfig azureConfig,
      AzureLogCallbackProvider logCallback, AzureARMTaskNGParameters azureARMTaskNGParameters) {
    DeploymentSubscriptionContext context =
        azureARMBaseHelper.toDeploymentSubscriptionContext(azureARMTaskNGParameters, azureConfig, logCallback);
    try {
      String outputs = azureARMDeploymentService.deployAtSubscriptionScope(context);
      return azureARMBaseHelper.populateDeploymentResponse(outputs);
    } catch (Exception ex) {
      printDefaultFailureMsgForARMDeploymentUnits(
          ex, context.getLogStreamingTaskClient(), context.getRunningCommandUnit());
      throw ex;
    }
  }

  private AzureResourceCreationTaskNGResponse deployAtManagementGroupScope(AzureConfig azureConfig,
      AzureLogCallbackProvider logCallback, AzureARMTaskNGParameters azureARMTaskNGParameters) {
    DeploymentManagementGroupContext context =
        azureARMBaseHelper.toDeploymentManagementGroupContext(azureARMTaskNGParameters, azureConfig, logCallback);
    try {
      String outputs = azureARMDeploymentService.deployAtManagementGroupScope(context);
      return azureARMBaseHelper.populateDeploymentResponse(outputs);
    } catch (Exception ex) {
      printDefaultFailureMsgForARMDeploymentUnits(
          ex, context.getLogStreamingTaskClient(), context.getRunningCommandUnit());
      throw ex;
    }
  }

  private AzureResourceCreationTaskNGResponse deployAtTenantScope(AzureConfig azureConfig,
      AzureLogCallbackProvider logCallback, AzureARMTaskNGParameters azureARMTaskNGParameters) {
    DeploymentTenantContext context =
        azureARMBaseHelper.toDeploymentTenantContext(azureARMTaskNGParameters, azureConfig, logCallback);
    try {
      String outputs = azureARMDeploymentService.deployAtTenantScope(context);
      return azureARMBaseHelper.populateDeploymentResponse(outputs);
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
      AzureLogCallbackProvider logStreamingTaskClient, final String runningCommandUnit, final String errorMsg) {
    if (isBlank(runningCommandUnit)) {
      return;
    }
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(runningCommandUnit);
    logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
  }
}
