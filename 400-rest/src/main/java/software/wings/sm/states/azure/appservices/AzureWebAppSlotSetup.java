/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservices;

import static io.harness.azure.model.AzureConstants.SECRET_REF_FIELS_NAME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.AZURE_APP_SERVICE_SLOT_SETUP;
import static software.wings.sm.StateType.AZURE_WEBAPP_SLOT_SETUP;
import static software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement.SWEEPING_OUTPUT_APP_SERVICE;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConfiguration;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters.AzureWebAppSlotSetupParametersBuilder;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.AzureWebAppCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateType;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class AzureWebAppSlotSetup extends AbstractAzureAppServiceState {
  @Getter @Setter private String appService;
  @Getter @Setter private String deploymentSlot;
  @Getter @Setter private String targetSlot;
  @Getter @Setter private String slotSteadyStateTimeout;
  public static final String APP_SERVICE_SLOT_SETUP = "App Service Slot Setup";

  public AzureWebAppSlotSetup(String name) {
    this(name, AZURE_WEBAPP_SLOT_SETUP);
  }

  public AzureWebAppSlotSetup(String name, StateType stateType) {
    super(name, stateType);
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    int timeOut = getUserDefinedTimeOut(context);
    return Ints.checkedCast(TimeUnit.MINUTES.toMillis(timeOut));
  }

  private int getUserDefinedTimeOut(ExecutionContext context) {
    return azureVMSSStateHelper.renderExpressionOrGetDefault(
        slotSteadyStateTimeout, context, AzureConstants.DEFAULT_AZURE_VMSS_TIMEOUT_MIN);
  }

  @Override
  protected boolean supportRemoteManifest() {
    return true;
  }

  @Override
  protected boolean shouldExecute(ExecutionContext context) {
    // setup is first step and hence should always be run
    return true;
  }

  @Override
  protected AzureTaskExecutionRequest buildTaskExecutionRequest(
      ExecutionContext context, AzureAppServiceStateData azureAppServiceStateData, String activityId) {
    AzureWebAppSlotSetupParameters slotSetupParameters =
        buildSlotSetupParams(context, azureAppServiceStateData, activityId);
    ArtifactConnectorMapper artifactConnectorMapper =
        azureVMSSStateHelper.getConnectorMapper(context, azureAppServiceStateData.getArtifact());
    populateContainerArtifactDetails(context, slotSetupParameters, artifactConnectorMapper);
    return AzureTaskExecutionRequest.builder()
        .azureConfigDTO(azureVMSSStateHelper.createAzureConfigDTO(azureAppServiceStateData.getAzureConfig()))
        .azureConfigEncryptionDetails(azureAppServiceStateData.getAzureEncryptedDataDetails())
        .azureTaskParameters(slotSetupParameters)
        .artifactStreamAttributes(getArtifactStreamAttributes(context, artifactConnectorMapper))
        .build();
  }

  @Override
  protected StateExecutionData buildPreStateExecutionData(
      String activityId, ExecutionContext context, AzureAppServiceStateData azureAppServiceStateData) {
    String appServiceName = context.renderExpression(appService);
    String deploySlotName =
        AzureResourceUtility.fixDeploymentSlotName(context.renderExpression(deploymentSlot), appServiceName);
    String targetSlotName =
        AzureResourceUtility.fixDeploymentSlotName(context.renderExpression(targetSlot), appServiceName);
    return AzureAppServiceSlotSetupExecutionData.builder()
        .activityId(activityId)
        .resourceGroup(azureAppServiceStateData.getResourceGroup())
        .subscriptionId(azureAppServiceStateData.getSubscriptionId())
        .appServiceName(appServiceName)
        .deploySlotName(deploySlotName)
        .targetSlotName(targetSlotName)
        .infrastructureMappingId(azureAppServiceStateData.getInfrastructureMapping().getUuid())
        .appServiceSlotSetupTimeOut(getUserDefinedTimeOut(context))
        .taskType(TaskType.AZURE_APP_SERVICE_TASK)
        .build();
  }

  @Override
  protected StateExecutionData buildPostStateExecutionData(
      ExecutionContext context, AzureTaskExecutionResponse executionResponse, ExecutionStatus executionStatus) {
    AzureWebAppSlotSetupResponse slotSetupTaskResponse =
        (AzureWebAppSlotSetupResponse) executionResponse.getAzureTaskResponse();

    provideInstanceElementDetails(context, executionStatus, slotSetupTaskResponse);

    AzureAppServiceSlotSetupExecutionData stateExecutionData = context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    stateExecutionData.setAppServiceName(slotSetupTaskResponse.getPreDeploymentData().getAppName());
    stateExecutionData.setDeploySlotName(slotSetupTaskResponse.getPreDeploymentData().getSlotName());
    stateExecutionData.setWebAppUrl(getWebAppUrl(slotSetupTaskResponse));
    return stateExecutionData;
  }

  private String getWebAppUrl(AzureWebAppSlotSetupResponse slotSetupTaskResponse) {
    if (isEmpty(slotSetupTaskResponse.getAzureAppDeploymentData())) {
      return EMPTY;
    }
    AzureAppDeploymentData azureAppDeploymentData = slotSetupTaskResponse.getAzureAppDeploymentData().get(0);
    return azureAppDeploymentData.getHostName();
  }

  @Override
  protected ContextElement buildContextElement(ExecutionContext context, AzureTaskExecutionResponse executionResponse) {
    AzureWebAppSlotSetupResponse slotSetupTaskResponse =
        (AzureWebAppSlotSetupResponse) executionResponse.getAzureTaskResponse();
    AzureAppServiceSlotSetupExecutionData stateExecutionData = context.getStateExecutionData();
    List<InstanceElement> instanceElements = getInstanceElements(context, slotSetupTaskResponse, stateExecutionData);
    return InstanceElementListParam.builder().instanceElements(instanceElements).build();
  }

  @Override
  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    AzureAppServiceSlotSetupExecutionData stateExecutionData = context.getStateExecutionData();
    TaskType taskType = stateExecutionData.getTaskType();
    switch (taskType) {
      case GIT_FETCH_FILES_TASK:
        return handleAsyncResponseForGitTask(context, response, stateExecutionData);

      case AZURE_APP_SERVICE_TASK:
        return super.handleAsyncInternal(context, response);

      default:
        throw new InvalidRequestException("Unhandled task type " + taskType);
    }
  }

  private ExecutionResponse handleAsyncResponseForGitTask(ExecutionContext context, Map<String, ResponseData> response,
      AzureAppServiceSlotSetupExecutionData stateExecutionData) {
    stateExecutionData.setGitFetchDone(true);
    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        executionResponse.getGitCommandStatus() == GitCommandExecutionResponse.GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(stateExecutionData.getActivityId(), context.getAppId(), executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    stateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    String activityId = ((AzureAppServiceSlotSetupExecutionData) context.getStateExecutionData()).getActivityId();
    return submitTask(context, activityId, getWebAppPackageArtifact(context));
  }

  @Override
  protected ExecutionResponse processDelegateResponse(
      AzureTaskExecutionResponse executionResponse, ExecutionContext context, ExecutionStatus executionStatus) {
    if (executionResponse.getAzureTaskResponse() == null) {
      // There is no need to save context element and do rollback for the cases
      // when some error happens before starting slot setup on delegate side.
      // Errors could be thrown during decryption slot setup params, collecting pre-deployment data or building docker
      // context and we don't need do rollback, just investigate error.
      log.error("Slot setup response is empty, error happens before starting slot setup, executionStatus {}",
          executionStatus);
      if (executionStatus.equals(ExecutionStatus.FAILED)) {
        return prepareExecutionResponse(executionResponse, context, executionStatus);
      } else {
        // Unexpected behaviour, here execution status should be FAILED, explore logs
        throw new InvalidRequestException("Unable to start slot setup step");
      }
    }
    saveContextElementToSweepingOutput(executionResponse, context);
    return prepareExecutionResponse(executionResponse, context, executionStatus);
  }

  private void saveContextElementToSweepingOutput(
      AzureTaskExecutionResponse executionResponse, ExecutionContext context) {
    AzureWebAppSlotSetupResponse slotSetupTaskResponse =
        (AzureWebAppSlotSetupResponse) executionResponse.getAzureTaskResponse();
    AzureAppServiceSlotSetupExecutionData stateExecutionData = context.getStateExecutionData();
    AzureAppServicePreDeploymentData preDeploymentData = slotSetupTaskResponse.getPreDeploymentData();

    AzureAppServiceSlotSetupContextElement setupContextElement =
        AzureAppServiceSlotSetupContextElement.builder()
            .infraMappingId(stateExecutionData.getInfrastructureMappingId())
            .appServiceSlotSetupTimeOut(getUserDefinedTimeOut(context))
            .commandName(APP_SERVICE_SLOT_SETUP)
            .subscriptionId(stateExecutionData.getSubscriptionId())
            .resourceGroup(stateExecutionData.getResourceGroup())
            .webApp(preDeploymentData.getAppName())
            .deploymentSlot(preDeploymentData.getSlotName())
            .targetSlot(stateExecutionData.getTargetSlotName())
            .preDeploymentData(preDeploymentData)
            .build();

    azureSweepingOutputServiceHelper.saveToSweepingOutPut(setupContextElement, SWEEPING_OUTPUT_APP_SERVICE, context);
  }

  @Override
  protected void emitAnyDataForExternalConsumption(
      ExecutionContext context, AzureTaskExecutionResponse executionResponse) {
    AzureWebAppSlotSetupResponse slotSetupTaskResponse =
        (AzureWebAppSlotSetupResponse) executionResponse.getAzureTaskResponse();
    AzureAppServiceSlotSetupExecutionData stateExecutionData = context.getStateExecutionData();
    List<InstanceElement> instanceElements = getInstanceElements(context, slotSetupTaskResponse, stateExecutionData);
    azureVMSSStateHelper.saveAzureAppInfoToSweepingOutput(
        context, instanceElements, slotSetupTaskResponse.getAzureAppDeploymentData());
  }

  @Override
  protected Artifact getWebAppPackageArtifact(ExecutionContext context) {
    if (azureVMSSStateHelper.isWebAppDockerDeployment(context)) {
      return null;
    }

    return azureVMSSStateHelper.getWebAppPackageArtifact(context);
  }

  @Override
  protected String commandType() {
    return APP_SERVICE_SLOT_SETUP;
  }

  @NotNull
  @Override
  protected CommandUnitType commandUnitType() {
    return AZURE_APP_SERVICE_SLOT_SETUP;
  }

  @Override
  protected List<CommandUnit> commandUnits(OrchestrationWorkflowType workflowType, boolean isGitFetch) {
    List<CommandUnit> commandUnits = new ArrayList<>();
    if (isGitFetch) {
      commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.FETCH_FILES));
    }
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.SAVE_EXISTING_CONFIGURATIONS));
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS));
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.DEPLOY_TO_SLOT));
    commandUnits.add(new AzureWebAppCommandUnit(AzureConstants.DEPLOYMENT_STATUS));
    return commandUnits;
  }

  private AzureWebAppSlotSetupParameters buildSlotSetupParams(
      ExecutionContext context, AzureAppServiceStateData azureAppServiceStateData, String activityId) {
    AzureWebAppSlotSetupParametersBuilder slotSetupParametersBuilder = AzureWebAppSlotSetupParameters.builder();
    provideAppServiceSettings(context, slotSetupParametersBuilder);

    boolean isBasicWorkflowOrchestrationType = isBasicWorkflowOrchestrationType(context);
    String appServiceName = context.renderExpression(appService);
    String deploySlotName =
        AzureResourceUtility.fixDeploymentSlotName(context.renderExpression(deploymentSlot), appServiceName);

    String targetSlotName = null;
    if (!isBasicWorkflowOrchestrationType) {
      targetSlotName = AzureResourceUtility.fixDeploymentSlotName(context.renderExpression(targetSlot), appServiceName);
    }

    return slotSetupParametersBuilder.accountId(azureAppServiceStateData.getApplication().getAccountId())
        .appId(azureAppServiceStateData.getApplication().getAppId())
        .commandName(APP_SERVICE_SLOT_SETUP)
        .activityId(activityId)
        .subscriptionId(azureAppServiceStateData.getSubscriptionId())
        .resourceGroupName(azureAppServiceStateData.getResourceGroup())
        .slotName(deploySlotName)
        .targetSlotName(targetSlotName)
        .webAppName(appServiceName)
        .timeoutIntervalInMin(getUserDefinedTimeOut(context))
        .startupCommand(fetchStartupCommand(context))
        .isBasicDeployment(isBasicWorkflowOrchestrationType)
        .build();
  }

  private void provideAppServiceSettings(
      ExecutionContext context, AzureWebAppSlotSetupParametersBuilder slotSetupParametersBuilder) {
    AzureAppServiceSlotSetupExecutionData setupExecutionData = context.getStateExecutionData();
    AzureAppServiceConfiguration appServiceConfiguration;
    if (setupExecutionData != null && setupExecutionData.isGitFetchDone()) {
      appServiceConfiguration = azureAppServiceManifestUtils.getAzureAppServiceConfigurationGit(setupExecutionData);
    } else {
      appServiceConfiguration = azureAppServiceManifestUtils.getAzureAppServiceConfiguration(context);
    }

    List<AzureAppServiceApplicationSetting> appSettings = appServiceConfiguration.getAppSettings();
    List<AzureAppServiceConnectionString> connStrings = appServiceConfiguration.getConnStrings();

    azureVMSSStateHelper.validateAppSettings(appSettings);
    azureVMSSStateHelper.validateConnStrings(connStrings);

    slotSetupParametersBuilder.applicationSettings(appSettings);
    slotSetupParametersBuilder.connectionStrings(connStrings);
  }

  private void populateContainerArtifactDetails(ExecutionContext context,
      AzureWebAppSlotSetupParameters slotSetupParameters, ArtifactConnectorMapper artifactConnectorMapper) {
    if (!artifactConnectorMapper.isDockerArtifactType()) {
      return;
    }
    AzureRegistryType azureRegistryType = artifactConnectorMapper.getAzureRegistryType();
    ConnectorConfigDTO connectorConfigDTO = artifactConnectorMapper.getConnectorDTO();
    List<EncryptedDataDetail> encryptedDataDetails =
        artifactConnectorMapper.getEncryptableSetting()
            .map(setting -> azureVMSSStateHelper.getEncryptedDataDetails(context, setting))
            .orElse(Collections.emptyList());

    azureVMSSStateHelper.updateEncryptedDataDetailSecretFieldName(encryptedDataDetails, SECRET_REF_FIELS_NAME);

    slotSetupParameters.setConnectorConfigDTO(connectorConfigDTO);
    slotSetupParameters.setEncryptedDataDetails(encryptedDataDetails);
    slotSetupParameters.setAzureRegistryType(azureRegistryType);
    slotSetupParameters.setImageName(artifactConnectorMapper.getFullImageName());
    slotSetupParameters.setImageTag(artifactConnectorMapper.getImageTag());
  }

  protected ArtifactStreamAttributes getArtifactStreamAttributes(
      ExecutionContext context, ArtifactConnectorMapper artifactConnectorMapper) {
    if (artifactConnectorMapper.isDockerArtifactType()) {
      return null;
    }
    ArtifactStreamAttributes artifactStreamAttributes = artifactConnectorMapper.artifactStreamAttributes();
    List<EncryptedDataDetail> encryptedDataDetails =
        artifactConnectorMapper.getEncryptableSetting()
            .map(setting -> azureVMSSStateHelper.getEncryptedDataDetails(context, setting))
            .orElse(Collections.emptyList());
    artifactStreamAttributes.setArtifactServerEncryptedDataDetails(encryptedDataDetails);

    return artifactStreamAttributes;
  }

  private void provideInstanceElementDetails(
      ExecutionContext context, ExecutionStatus executionStatus, AzureWebAppSlotSetupResponse slotSetupTaskResponse) {
    AzureAppServiceSlotSetupExecutionData stateExecutionData = context.getStateExecutionData();
    List<InstanceElement> instanceElements = getInstanceElements(context, slotSetupTaskResponse, stateExecutionData);
    if (isNotEmpty(instanceElements)) {
      List<InstanceStatusSummary> newInstanceStatusSummaries =
          azureVMSSStateHelper.getInstanceStatusSummaries(executionStatus, instanceElements);
      stateExecutionData.setNewInstanceStatusSummaries(newInstanceStatusSummaries);
    }
  }

  private List<InstanceElement> getInstanceElements(ExecutionContext context,
      AzureWebAppSlotSetupResponse slotSetupTaskResponse, AzureAppServiceSlotSetupExecutionData stateExecutionData) {
    AzureWebAppInfrastructureMapping webAppInfrastructureMapping =
        azureVMSSStateHelper.getAzureWebAppInfrastructureMapping(
            stateExecutionData.getInfrastructureMappingId(), context.getAppId());

    return azureSweepingOutputServiceHelper.generateAzureAppInstanceElements(
        context, webAppInfrastructureMapping, slotSetupTaskResponse.getAzureAppDeploymentData());
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();

    if (isBlank(deploymentSlot)) {
      invalidFields.put("deploymentSlot", "Deployment slot cannot be empty");
    }

    if (isBlank(appService)) {
      invalidFields.put("appService", "Application name cannot be empty");
    }

    if (deploymentSlot != null && deploymentSlot.equals(targetSlot)) {
      invalidFields.put("targetSlot", "Target slot cannot be the same as deployment slot");
    }

    if (deploymentSlot != null && deploymentSlot.equals(appService)) {
      invalidFields.put("deploymentSlot", "Deployment slot cannot be production slot");
    }

    return invalidFields;
  }
}
