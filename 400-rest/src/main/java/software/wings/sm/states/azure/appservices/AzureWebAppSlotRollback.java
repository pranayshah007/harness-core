/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservices;

import static io.harness.beans.OrchestrationWorkflowType.CANARY;

import static software.wings.sm.StateType.AZURE_WEBAPP_SLOT_ROLLBACK;

import io.harness.azure.model.AzureConstants;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppRollbackParameters;

import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.AzureWebAppCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class AzureWebAppSlotRollback extends AzureWebAppSlotSetup {
  public AzureWebAppSlotRollback(String name) {
    super(name, AZURE_WEBAPP_SLOT_ROLLBACK);
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    return getTimeout(context);
  }

  @Override
  protected boolean supportRemoteManifest() {
    return false;
  }

  @Override
  protected AzureTaskExecutionRequest buildTaskExecutionRequest(
      ExecutionContext context, AzureAppServiceStateData azureAppServiceStateData, String activityId) {
    AzureWebAppRollbackParameters rollbackParameters =
        buildAzureWebAppSlotRollbackParameters(context, azureAppServiceStateData, activityId);
    ArtifactConnectorMapper artifactConnectorMapper =
        azureVMSSStateHelper.getConnectorMapper(context, azureAppServiceStateData.getArtifact());
    return AzureTaskExecutionRequest.builder()
        .azureConfigDTO(azureVMSSStateHelper.createAzureConfigDTO(azureAppServiceStateData.getAzureConfig()))
        .azureConfigEncryptionDetails(azureAppServiceStateData.getAzureEncryptedDataDetails())
        .azureTaskParameters(rollbackParameters)
        .artifactStreamAttributes(getArtifactStreamAttributes(context, artifactConnectorMapper))
        .build();
  }

  private AzureWebAppRollbackParameters buildAzureWebAppSlotRollbackParameters(
      ExecutionContext context, AzureAppServiceStateData azureAppServiceStateData, String activityId) {
    AzureAppServiceSlotSetupContextElement contextElement = readContextElement(context);
    return AzureWebAppRollbackParameters.builder()
        .accountId(azureAppServiceStateData.getApplication().getAccountId())
        .appId(azureAppServiceStateData.getApplication().getAppId())
        .commandName(APP_SERVICE_SLOT_SETUP)
        .activityId(activityId)
        .appName(contextElement.getWebApp())
        .subscriptionId(azureAppServiceStateData.getSubscriptionId())
        .resourceGroupName(azureAppServiceStateData.getResourceGroup())
        .preDeploymentData(contextElement.getPreDeploymentData())
        .timeoutIntervalInMin(contextElement.getAppServiceSlotSetupTimeOut())
        .blueGreen(azureVMSSStateHelper.isBlueGreenWorkflow(context))
        .isBasicDeployment(isBasicWorkflowOrchestrationType(context))
        .build();
  }

  @Override
  @SchemaIgnore
  public String getTargetSlot() {
    return super.getTargetSlot();
  }

  @Override
  @SchemaIgnore
  public String getSlotSteadyStateTimeout() {
    return super.getSlotSteadyStateTimeout();
  }

  @Override
  protected boolean shouldExecute(ExecutionContext context) {
    if (!verifyIfContextElementExist(context)) {
      return false;
    }

    if (!isPreDeploymentDataPresent(context)) {
      setStepSkipMsg("No Azure App service setup context element found. Skipping rollback");
      return false;
    }

    if (azureVMSSStateHelper.isWebAppDockerDeployment(context)) {
      return true;
    }

    if (!azureVMSSStateHelper.getWebAppPackageArtifactForRollback(context).isPresent()) {
      setStepSkipMsg("Not found artifact for rollback. Skipping rollback");
      return false;
    }

    return true;
  }

  private boolean isPreDeploymentDataPresent(ExecutionContext context) {
    AzureAppServiceSlotSetupContextElement contextElement = readContextElement(context);
    AzureAppServicePreDeploymentData preDeploymentData = contextElement.getPreDeploymentData();
    return preDeploymentData != null;
  }

  @Override
  protected Artifact getWebAppPackageArtifact(ExecutionContext context) {
    if (azureVMSSStateHelper.isWebAppDockerDeployment(context)) {
      return null;
    }

    return azureVMSSStateHelper.getWebAppPackageArtifactForRollbackExceptionally(context);
  }

  @Override
  @SchemaIgnore
  public boolean isRollback() {
    return true;
  }

  @Override
  @SchemaIgnore
  public String getAppService() {
    return super.getAppService();
  }

  @Override
  @SchemaIgnore
  public String getDeploymentSlot() {
    return super.getDeploymentSlot();
  }

  @Override
  public String skipMessage() {
    return stepSkipMsg;
  }

  @Override
  public Map<String, String> validateFields() {
    return Collections.emptyMap();
  }

  @Override
  protected ExecutionResponse processDelegateResponse(
      AzureTaskExecutionResponse executionResponse, ExecutionContext context, ExecutionStatus executionStatus) {
    return prepareExecutionResponse(executionResponse, context, executionStatus);
  }

  @Override
  protected List<CommandUnit> commandUnits(OrchestrationWorkflowType workflowType, boolean isGitFetch) {
    List<CommandUnit> commandUnits = new ArrayList<>();
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS));
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.DEPLOY_TO_SLOT));
    if (CANARY == workflowType) {
      commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.SLOT_TRAFFIC_PERCENTAGE));
    }
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.DEPLOYMENT_STATUS));
    return commandUnits;
  }
}
