/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.AUTHORIZATION_ERROR;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_VALIDATION_FAILED_MSG_PATTERN;
import static io.harness.azure.model.AzureConstants.ERROR_CODE_LOCATION_NOT_FOUND;
import static io.harness.azure.model.AzureConstants.ERROR_INVALID_MANAGEMENT_GROUP_ID;
import static io.harness.azure.model.AzureConstants.ERROR_INVALID_TENANT_CREDENTIALS;
import static io.harness.azure.model.AzureConstants.ERROR_LOCATION_NOT_FOUND;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureARMRGTemplateExportOptions;
import io.harness.azure.model.AzureARMTemplate;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.exception.runtime.azure.AzureARMManagementScopeException;
import io.harness.exception.runtime.azure.AzureARMSubscriptionScopeException;
import io.harness.exception.runtime.azure.AzureARMTenantScopeException;
import io.harness.exception.runtime.azure.AzureARMValidationException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.JsonUtils;

import software.wings.delegatetasks.azure.arm.deployment.context.DeploymentContext;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.polling.PollResult;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.resources.fluent.models.DeploymentExtendedInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentValidateResultInner;
import com.azure.resourcemanager.resources.models.Deployment;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class AzureARMDeploymentService {
  @Inject private AzureManagementClient azureManagementClient;
  @Inject private ARMDeploymentSteadyStateChecker deploymentSteadyStateChecker;

  public String deployAtResourceGroupScope(DeploymentResourceGroupContext context) {
    AzureClientContext azureClientContext = context.getAzureClientContext();
    AzureARMTemplate azureARMTemplate = AzureARMTemplate.builder()
                                            .deploymentMode(context.getMode())
                                            .deploymentName(context.getDeploymentName())
                                            .parametersJSON(context.getParametersJson())
                                            .templateJSON(context.getTemplateJson())
                                            .build();

    LogCallback logCallback = getARMDeploymentLogCallback(context);
    logCallback.saveExecutionLog(String.format(
        "Starting ARM %s at Resource Group scope ... %nResource Group - [%s]%nMode - [%s]%nDeployment Name - [%s]",
        context.isRollback() ? "Rollback" : "Deployment", azureClientContext.getResourceGroupName(),
        azureARMTemplate.getDeploymentMode().name(), azureARMTemplate.getDeploymentName()));
    SyncPoller<Void, Deployment> syncPoller =
        azureManagementClient.deployAtResourceGroupScope(azureClientContext, azureARMTemplate);
    logCallback.saveExecutionLog(
        String.format("ARM %s request send successfully", context.isRollback() ? "Rollback" : "Deployment"),
        LogLevel.INFO, SUCCESS);

    return performSteadyStateCheckResourceGroupScope(context, syncPoller);
  }

  public void validateTemplate(DeploymentResourceGroupContext context) {
    AzureClientContext azureClientContext = context.getAzureClientContext();
    AzureARMTemplate azureARMTemplate = AzureARMTemplate.builder()
                                            .deploymentMode(context.getMode())
                                            .deploymentName(context.getDeploymentName())
                                            .parametersJSON(context.getParametersJson())
                                            .templateJSON(context.getTemplateJson())
                                            .build();
    LogCallback logCallback = getARMDeploymentLogCallback(context);
    logCallback.saveExecutionLog("Starting template validation");
    DeploymentValidateResultInner deploymentValidateResultInner =
        azureManagementClient.validateDeploymentAtResourceGroupScope(azureClientContext, azureARMTemplate);
    ManagementError errorResponse = deploymentValidateResultInner.error();
    if (errorResponse != null) {
      logCallback.saveExecutionLog("Template validation failed", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new AzureARMValidationException(
          format("Unable to deploy at resource group scope, deployment validation failed: %s",
              getValidationErrorMsg(errorResponse)));
    }
  }

  public String exportExistingResourceGroupTemplate(DeploymentResourceGroupContext context, LogCallback logCallback) {
    AzureClientContext azureClientContext = context.getAzureClientContext();
    logCallback.saveExecutionLog(String.format(
        "Saving existing template for resource group - [%s] ", azureClientContext.getResourceGroupName()));
    return azureManagementClient.exportResourceGroupTemplateJSON(
        azureClientContext, AzureARMRGTemplateExportOptions.INCLUDE_PARAMETER_DEFAULT_VALUE);
  }

  private String performSteadyStateCheckResourceGroupScope(
      DeploymentResourceGroupContext context, SyncPoller<Void, Deployment> syncPoller) {
    ARMDeploymentSteadyStateContext steadyStateContext =
        ARMDeploymentSteadyStateContext.builder()
            .azureConfig(context.getAzureClientContext().getAzureConfig())
            .deploymentName(context.getDeploymentName())
            .resourceGroup(context.getAzureClientContext().getResourceGroupName())
            .subscriptionId(context.getAzureClientContext().getSubscriptionId())
            .scopeType(ARMScopeType.RESOURCE_GROUP)
            .statusCheckIntervalInSeconds(ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL)
            .steadyCheckTimeoutInMinutes(context.getSteadyStateTimeoutInMin())
            .build();

    deploymentSteadyStateChecker.waitUntilCompleteWithTimeout(
        steadyStateContext, azureManagementClient, getARMDeploymentSteadyStateLogCallback(context), syncPoller);

    return getARMDeploymentOutputs(context, steadyStateContext);
  }

  public String deployAtSubscriptionScope(DeploymentSubscriptionContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    String subscriptionId = context.getSubscriptionId();
    AzureARMTemplate azureARMTemplate = AzureARMTemplate.builder()
                                            .location(context.getDeploymentDataLocation())
                                            .deploymentMode(context.getMode())
                                            .deploymentName(context.getDeploymentName())
                                            .parametersJSON(context.getParametersJson())
                                            .templateJSON(context.getTemplateJson())
                                            .build();

    LogCallback logCallback = getARMDeploymentLogCallback(context);
    logCallback.saveExecutionLog("Starting template validation");
    DeploymentValidateResultInner deploymentValidateResultInner =
        azureManagementClient.validateDeploymentAtSubscriptionScope(azureConfig, subscriptionId, azureARMTemplate);
    ManagementError errorResponse = deploymentValidateResultInner.error();
    if (errorResponse != null) {
      logCallback.saveExecutionLog("Template validation failed", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      if (Objects.equals(errorResponse.getCode(), ERROR_CODE_LOCATION_NOT_FOUND)) {
        throw new AzureARMSubscriptionScopeException(
            format(ERROR_LOCATION_NOT_FOUND, context.getDeploymentDataLocation()));
      }
      throw new AzureARMSubscriptionScopeException(
          format("Unable to deploy at subscription scope, deployment validation failed: %s",
              getValidationErrorMsg(errorResponse)));
    }

    logCallback.saveExecutionLog(String.format(
        "Starting ARM Deployment at Subscription scope. Deployment Name - [%s]", azureARMTemplate.getDeploymentName()));
    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller =
        azureManagementClient.deployAtSubscriptionScope(azureConfig, subscriptionId, azureARMTemplate);
    logCallback.saveExecutionLog("ARM Deployment request send successfully", LogLevel.INFO, SUCCESS);
    return performSteadyStateCheckSubscriptionScope(context, syncPoller);
  }

  private String performSteadyStateCheckSubscriptionScope(DeploymentSubscriptionContext context,
      SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller) {
    ARMDeploymentSteadyStateContext steadyStateContext =
        ARMDeploymentSteadyStateContext.builder()
            .azureConfig(context.getAzureConfig())
            .deploymentName(context.getDeploymentName())
            .subscriptionId(context.getSubscriptionId())
            .scopeType(ARMScopeType.SUBSCRIPTION)
            .statusCheckIntervalInSeconds(ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL)
            .steadyCheckTimeoutInMinutes(context.getSteadyStateTimeoutInMin())
            .build();

    deploymentSteadyStateChecker.waitUntilCompleteWithTimeout(
        steadyStateContext, azureManagementClient, getARMDeploymentSteadyStateLogCallback(context), syncPoller);

    return getARMDeploymentOutputs(context, steadyStateContext);
  }

  public String deployAtManagementGroupScope(DeploymentManagementGroupContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    String managementGroupId = context.getManagementGroupId();
    AzureARMTemplate azureARMTemplate = AzureARMTemplate.builder()
                                            .location(context.getDeploymentDataLocation())
                                            .deploymentMode(context.getMode())
                                            .deploymentName(context.getDeploymentName())
                                            .parametersJSON(context.getParametersJson())
                                            .templateJSON(context.getTemplateJson())
                                            .build();

    LogCallback logCallback = getARMDeploymentLogCallback(context);
    logCallback.saveExecutionLog("Starting template validation");
    try {
      DeploymentValidateResultInner deploymentValidateResultInner =
          azureManagementClient.validateDeploymentAtManagementGroupScope(
              azureConfig, managementGroupId, azureARMTemplate);
      ManagementError errorResponse = deploymentValidateResultInner.error();
      if (errorResponse != null) {
        logCallback.saveExecutionLog("Template validation failed", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        if (Objects.equals(errorResponse.getCode(), ERROR_CODE_LOCATION_NOT_FOUND)) {
          throw new AzureARMManagementScopeException(
              format(ERROR_LOCATION_NOT_FOUND, context.getDeploymentDataLocation()));
        }
        throw new AzureARMManagementScopeException(
            format("Unable to deploy at management group scope, deployment validation failed: %s",
                getValidationErrorMsg(errorResponse)));
      }

      logCallback.saveExecutionLog(String.format(
          "Starting ARM Deployment at Management scope. Deployment Name - [%s]", azureARMTemplate.getDeploymentName()));
      SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller =
          azureManagementClient.deployAtManagementGroupScope(azureConfig, managementGroupId, azureARMTemplate);
      logCallback.saveExecutionLog("ARM Deployment request send successfully", LogLevel.INFO, SUCCESS);
      return performSteadyStateCheckManagementGroupScope(context, syncPoller);
    } catch (Exception ex) {
      if (ex.getMessage() != null && ex.getMessage().contains(AUTHORIZATION_ERROR)) {
        throw new AzureARMManagementScopeException(ERROR_INVALID_MANAGEMENT_GROUP_ID);
      }
      throw ex;
    }
  }

  private String performSteadyStateCheckManagementGroupScope(DeploymentManagementGroupContext context,
      SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller) {
    ARMDeploymentSteadyStateContext steadyStateContext =
        ARMDeploymentSteadyStateContext.builder()
            .azureConfig(context.getAzureConfig())
            .deploymentName(context.getDeploymentName())
            .managementGroupId(context.getManagementGroupId())
            .scopeType(ARMScopeType.MANAGEMENT_GROUP)
            .statusCheckIntervalInSeconds(ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL)
            .steadyCheckTimeoutInMinutes(context.getSteadyStateTimeoutInMin())
            .build();

    deploymentSteadyStateChecker.waitUntilCompleteWithTimeout(
        steadyStateContext, azureManagementClient, getARMDeploymentSteadyStateLogCallback(context), syncPoller);
    return getARMDeploymentOutputs(context, steadyStateContext);
  }

  public String deployAtTenantScope(DeploymentTenantContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    AzureARMTemplate azureARMTemplate = AzureARMTemplate.builder()
                                            .location(context.getDeploymentDataLocation())
                                            .deploymentMode(context.getMode())
                                            .deploymentName(context.getDeploymentName())
                                            .parametersJSON(context.getParametersJson())
                                            .templateJSON(context.getTemplateJson())
                                            .build();

    LogCallback logCallback = getARMDeploymentLogCallback(context);
    logCallback.saveExecutionLog("Starting template validation");
    try {
      DeploymentValidateResultInner deploymentValidateResultInner =
          azureManagementClient.validateDeploymentAtTenantScope(azureConfig, azureARMTemplate);
      ManagementError errorResponse = deploymentValidateResultInner.error();
      if (errorResponse != null) {
        logCallback.saveExecutionLog("Template validation failed", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        if (Objects.equals(errorResponse.getCode(), ERROR_CODE_LOCATION_NOT_FOUND)) {
          throw new AzureARMTenantScopeException(format(ERROR_LOCATION_NOT_FOUND, context.getDeploymentDataLocation()));
        }
        throw new AzureARMTenantScopeException(
            format("Unable to deploy at tenant scope, deployment validation failed: %s",
                getValidationErrorMsg(errorResponse)));
      }

      logCallback.saveExecutionLog(String.format(
          "Starting ARM Deployment at Tenant scope. Deployment Name - [%s]", azureARMTemplate.getDeploymentName()));
      SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller =
          azureManagementClient.deployAtTenantScope(azureConfig, azureARMTemplate);
      logCallback.saveExecutionLog("ARM Deployment request send successfully", LogLevel.INFO, SUCCESS);
      return performSteadyStateCheckTenantScope(context, syncPoller);
    } catch (Exception ex) {
      if (ex.getMessage() != null && ex.getMessage().contains(AUTHORIZATION_ERROR)) {
        throw new AzureARMTenantScopeException(ERROR_INVALID_TENANT_CREDENTIALS);
      }
      throw ex;
    }
  }

  private String performSteadyStateCheckTenantScope(DeploymentTenantContext context,
      SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller) {
    ARMDeploymentSteadyStateContext steadyStateContext =
        ARMDeploymentSteadyStateContext.builder()
            .azureConfig(context.getAzureConfig())
            .deploymentName(context.getDeploymentName())
            .tenantId(context.getAzureConfig().getTenantId())
            .scopeType(ARMScopeType.TENANT)
            .statusCheckIntervalInSeconds(ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL)
            .steadyCheckTimeoutInMinutes(context.getSteadyStateTimeoutInMin())
            .build();

    deploymentSteadyStateChecker.waitUntilCompleteWithTimeout(
        steadyStateContext, azureManagementClient, getARMDeploymentSteadyStateLogCallback(context), syncPoller);
    return getARMDeploymentOutputs(context, steadyStateContext);
  }

  private String getValidationErrorMsg(ManagementError errorResponse) {
    StringBuilder errorMessageBuilder = new StringBuilder("");
    buildErrorMessage(errorResponse, errorMessageBuilder);
    return errorMessageBuilder.toString();
  }

  private void buildErrorMessage(ManagementError errorResponse, StringBuilder parentErrorBuilder) {
    if (errorResponse == null) {
      return;
    }
    String errorMessage = format(DEPLOYMENT_VALIDATION_FAILED_MSG_PATTERN, errorResponse.getCode(),
        errorResponse.getMessage(), errorResponse.getTarget());
    parentErrorBuilder.append(errorMessage).append('\n');

    if (isNotEmpty(errorResponse.getDetails())) {
      for (ManagementError error : errorResponse.getDetails()) {
        buildErrorMessage(error, parentErrorBuilder);
      }
    }
  }

  private String getARMDeploymentOutputs(
      DeploymentContext context, ARMDeploymentSteadyStateContext steadyStateContext) {
    LogCallback outPutLogCallback = getARMDeploymentOutputsLogCallback(context);
    String armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(steadyStateContext);
    String prettifyJson = JsonUtils.prettifyJsonString(armDeploymentOutputs);
    outPutLogCallback.saveExecutionLog(
        prettifyJson.equalsIgnoreCase("null") ? "{}" : prettifyJson, LogLevel.INFO, SUCCESS);
    return armDeploymentOutputs;
  }

  private LogCallback getARMDeploymentOutputsLogCallback(DeploymentContext context) {
    context.setRunningCommandUnit(AzureConstants.ARM_DEPLOYMENT_OUTPUTS);
    return context.getLogStreamingTaskClient().obtainLogCallback(AzureConstants.ARM_DEPLOYMENT_OUTPUTS);
  }

  public LogCallback getARMDeploymentLogCallback(DeploymentContext context) {
    context.setRunningCommandUnit(AzureConstants.EXECUTE_ARM_DEPLOYMENT);
    return context.getLogStreamingTaskClient().obtainLogCallback(AzureConstants.EXECUTE_ARM_DEPLOYMENT);
  }

  private LogCallback getARMDeploymentSteadyStateLogCallback(DeploymentContext context) {
    context.setRunningCommandUnit(AzureConstants.ARM_DEPLOYMENT_STEADY_STATE);
    return context.getLogStreamingTaskClient().obtainLogCallback(AzureConstants.ARM_DEPLOYMENT_STEADY_STATE);
  }
}
