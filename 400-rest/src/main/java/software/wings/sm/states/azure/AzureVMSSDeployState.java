/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.DELETE_NEW_VMSS;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_ERROR;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_STATUS;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.SKIP_VMSS_DEPLOY;
import static io.harness.azure.model.AzureConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;

import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.sm.StateType.AZURE_VMSS_DEPLOY;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.concurrent.TimeUnit.MINUTES;

import io.harness.azure.model.AzureConstants;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.azure.request.AzureVMSSDeployTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSDeployTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.Service;
import software.wings.beans.TaskType;
import software.wings.beans.command.AzureVMSSDummyCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;
import software.wings.service.intfc.DelegateService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.StateType;
import software.wings.sm.states.InstanceUnitTypeDataProvider;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class AzureVMSSDeployState extends AbstractAzureState {
  public static final String AZURE_VMSS_DEPLOY_COMMAND_NAME = AzureConstants.AZURE_VMSS_DEPLOY_COMMAND_NAME;

  @Attributes(title = "Desired Instances (cumulative)") @Getter @Setter private Integer instanceCount;

  @Attributes(title = "Instance Unit Type (Count/Percent)")
  @EnumData(enumDataProvider = InstanceUnitTypeDataProvider.class)
  @DefaultValue("COUNT")
  @Getter
  @Setter
  private InstanceUnitType instanceUnitType = InstanceUnitType.COUNT;

  @Inject private transient AzureVMSSStateHelper azureVMSSStateHelper;
  @Inject private transient DelegateService delegateService;

  public AzureVMSSDeployState(String name) {
    super(name, AZURE_VMSS_DEPLOY);
  }

  public AzureVMSSDeployState(String name, StateType stateType) {
    super(name, stateType);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Do nothing on abort
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    return azureVMSSStateHelper.getAzureVMSSStateTimeoutFromContext(context);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  @Override
  public boolean isRollback() {
    return false;
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        context.getContextElement(ContextElementType.AZURE_VMSS_SETUP);
    if (azureVMSSSetupContextElement == null) {
      return ExecutionResponse.builder().executionStatus(SKIPPED).errorMessage(getSkipMessage()).build();
    }

    Application app = azureVMSSStateHelper.getApplication(context);
    String appId = app.getUuid();

    Environment env = azureVMSSStateHelper.getEnvironment(context);
    String envId = env.getUuid();

    Service service = azureVMSSStateHelper.getServiceByAppId(context, appId);
    Artifact artifact = azureVMSSStateHelper.getArtifact((DeploymentExecutionContext) context, service.getUuid());

    // create and save activity
    Activity activity = azureVMSSStateHelper.createAndSaveActivity(context, artifact, AZURE_VMSS_DEPLOY_COMMAND_NAME,
        getStateType(), CommandUnitDetails.CommandUnitType.AZURE_VMSS_DEPLOY,
        getCommandUnits(context, azureVMSSSetupContextElement.getResizeStrategy()));
    String activityId = activity.getUuid();

    ManagerExecutionLogCallback executionLogCallback = azureVMSSStateHelper.getExecutionLogCallback(activity);

    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
        azureVMSSStateHelper.getAzureVMSSInfrastructureMapping(context.fetchInfraMappingId(), appId);
    AzureConfig azureConfig =
        azureVMSSStateHelper.getAzureConfig(azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> azureEncryptionDetails = azureVMSSStateHelper.getEncryptedDataDetails(
        context, azureVMSSInfrastructureMapping.getComputeProviderSettingId());

    int newDesiredCount = getNewDesiredCount(azureVMSSSetupContextElement);
    int oldDesiredCount = getOldDesiredCount(azureVMSSSetupContextElement, newDesiredCount);

    AzureVMSSDeployStateExecutionData azureVMSSDeployStateExecutionData = buildAzureVMSSDeployStateExecutionData(
        azureVMSSSetupContextElement, azureVMSSInfrastructureMapping, activity, newDesiredCount, oldDesiredCount);

    AzureVMSSTaskParameters azureVmssTaskParameters = buildAzureVMSSTaskParameters(app, activityId,
        azureVMSSInfrastructureMapping, azureVMSSSetupContextElement, newDesiredCount, oldDesiredCount);

    AzureVMSSCommandRequest commandRequest =
        buildAzureVMSSCommandRequest(azureConfig, azureEncryptionDetails, azureVmssTaskParameters);

    executionLogCallback.saveExecutionLog("Starting Azure VMSS Deploy");
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
            .waitId(activityId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.AZURE_VMSS_COMMAND_TASK.name())
                      .parameters(new Object[] {commandRequest})
                      .timeout(MINUTES.toMillis(azureVMSSSetupContextElement.getAutoScalingSteadyStateVMSSTimeout()))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, azureVMSSInfrastructureMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, azureVMSSInfrastructureMapping.getServiceId())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Azure VMSS Deploy task execution")
            .build();

    delegateService.queueTask(delegateTask);

    appendDelegateTaskDetails(context, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(azureVMSSDeployStateExecutionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .correlationId(activity.getUuid())
        .build();
  }

  protected String getSkipMessage() {
    return SKIP_VMSS_DEPLOY;
  }

  protected int getNewDesiredCount(AzureVMSSSetupContextElement azureVMSSSetupContextElement) {
    int desiredInstances = azureVMSSSetupContextElement.getDesiredInstances();
    return getTotalExpectedCount(desiredInstances);
  }

  @VisibleForTesting
  int getTotalExpectedCount(int desiredInstances) {
    int updateCount;
    if (PERCENTAGE == instanceUnitType) {
      int percent = Math.min(instanceCount, 100);
      int count = (int) Math.round((percent * desiredInstances) / 100.0);
      // if use inputs 30%, means count after this phase deployment should be 30% of maxInstances
      updateCount = Math.max(count, 1);
    } else {
      updateCount = Math.min(instanceCount, desiredInstances);
    }
    return updateCount;
  }

  protected int getOldDesiredCount(AzureVMSSSetupContextElement azureVMSSSetupContextElement, int newDesiredCount) {
    // If it's final phase then old desired count = 0, else oldDesiredCount minus the new count
    int oldDesiredCount = azureVMSSSetupContextElement.getOldDesiredCount();
    return isFinalDeployState(oldDesiredCount) ? 0 : Math.max(0, oldDesiredCount - newDesiredCount);
  }

  private boolean isFinalDeployState(int oldDesiredCount) {
    return (PERCENTAGE == instanceUnitType) ? instanceCount == 100 : oldDesiredCount <= instanceCount;
  }

  private AzureVMSSDeployStateExecutionData buildAzureVMSSDeployStateExecutionData(
      AzureVMSSSetupContextElement azureVMSSSetupContextElement,
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping, Activity activity, int newDesiredCount,
      int oldDesiredCount) {
    String subscriptionId = azureVMSSInfrastructureMapping.getSubscriptionId();
    String resourceGroupName = azureVMSSInfrastructureMapping.getResourceGroupName();
    String newVirtualMachineScaleSetName = azureVMSSSetupContextElement.getNewVirtualMachineScaleSetName();
    String newVirtualMachineScaleSetId =
        azureVMSSStateHelper.getVMSSIdFromName(subscriptionId, resourceGroupName, newVirtualMachineScaleSetName);
    String oldVirtualMachineScaleSetName = azureVMSSSetupContextElement.getOldVirtualMachineScaleSetName();
    String oldVirtualMachineScaleSetId =
        azureVMSSStateHelper.getVMSSIdFromName(subscriptionId, resourceGroupName, oldVirtualMachineScaleSetName);

    return AzureVMSSDeployStateExecutionData.builder()
        .activityId(activity.getUuid())
        .infraMappingId(azureVMSSSetupContextElement.getInfraMappingId())
        .commandName(AZURE_VMSS_DEPLOY_COMMAND_NAME)
        .newVirtualMachineScaleSetName(newVirtualMachineScaleSetName)
        .newVirtualMachineScaleSetId(newVirtualMachineScaleSetId)
        .newDesiredCount(newDesiredCount)
        .oldVirtualMachineScaleSetName(oldVirtualMachineScaleSetName)
        .oldVirtualMachineScaleSetId(oldVirtualMachineScaleSetId)
        .oldDesiredCount(oldDesiredCount)
        .build();
  }

  private AzureVMSSTaskParameters buildAzureVMSSTaskParameters(Application app, String activityId,
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping,
      AzureVMSSSetupContextElement azureVMSSSetupContextElement, int newDesiredCount, int oldDesiredCount) {
    String accountId = app.getAccountId();
    String appId = app.getAppId();

    return AzureVMSSDeployTaskParameters.builder()
        .accountId(accountId)
        .appId(appId)
        .activityId(activityId)
        .commandName(AZURE_VMSS_DEPLOY_COMMAND_NAME)
        .resizeNewFirst(ResizeStrategy.RESIZE_NEW_FIRST == azureVMSSSetupContextElement.getResizeStrategy())
        .newVirtualMachineScaleSetName(azureVMSSSetupContextElement.getNewVirtualMachineScaleSetName())
        .oldVirtualMachineScaleSetName(azureVMSSSetupContextElement.getOldVirtualMachineScaleSetName())
        .newDesiredCount(newDesiredCount)
        .oldDesiredCount(oldDesiredCount)
        .autoScalingSteadyStateVMSSTimeout(azureVMSSSetupContextElement.getAutoScalingSteadyStateVMSSTimeout())
        .baseScalingPolicyJSONs(azureVMSSSetupContextElement.getBaseVMSSScalingPolicyJSONs())
        .minInstances(azureVMSSSetupContextElement.getMinInstances())
        .maxInstances(azureVMSSSetupContextElement.getMaxInstances())
        .desiredInstances(azureVMSSSetupContextElement.getDesiredInstances())
        .resourceGroupName(azureVMSSInfrastructureMapping.getResourceGroupName())
        .subscriptionId(azureVMSSInfrastructureMapping.getSubscriptionId())
        .rollback(isRollback())
        .isBlueGreen(azureVMSSSetupContextElement.isBlueGreen())
        .preDeploymentData(azureVMSSSetupContextElement.getPreDeploymentData())
        .build();
  }

  private AzureVMSSCommandRequest buildAzureVMSSCommandRequest(AzureConfig azureConfig,
      List<EncryptedDataDetail> azureEncryptionDetails, AzureVMSSTaskParameters azureVmssTaskParameters) {
    return AzureVMSSCommandRequest.builder()
        .azureConfigDTO(azureVMSSStateHelper.createAzureConfigDTO(azureConfig))
        .azureConfigEncryptionDetails(azureEncryptionDetails)
        .azureVMSSTaskParameters(azureVmssTaskParameters)
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    String appId = context.getAppId();
    // Execution Response
    AzureVMSSTaskExecutionResponse executionResponse =
        (AzureVMSSTaskExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = azureVMSSStateHelper.getExecutionStatus(executionResponse);
    if (executionStatus == ExecutionStatus.FAILED) {
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .errorMessage(executionResponse.getErrorMessage())
          .build();
    }

    // Execution Task Response
    AzureVMSSDeployTaskResponse azureVMSSDeployTaskResponse =
        (AzureVMSSDeployTaskResponse) executionResponse.getAzureVMSSTaskResponse();
    // Execution State Data
    AzureVMSSDeployStateExecutionData stateExecutionData =
        (AzureVMSSDeployStateExecutionData) context.getStateExecutionData();

    azureVMSSStateHelper.updateActivityStatus(appId, activityId, executionStatus);

    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
        azureVMSSStateHelper.getAzureVMSSInfrastructureMapping(stateExecutionData.getInfraMappingId(), appId);

    List<InstanceElement> instanceElements = newArrayList();

    List<InstanceElement> newInstanceElements =
        getNewInstanceElements(context, azureVMSSDeployTaskResponse, azureVMSSInfrastructureMapping);
    addInstanceElements(instanceElements, newInstanceElements, true);

    populateAzureVMSSDeployStateExecutionData(
        stateExecutionData, executionResponse, executionStatus, newInstanceElements);

    List<InstanceElement> existingInstanceElements =
        getExistingInstanceElements(context, azureVMSSDeployTaskResponse, azureVMSSInfrastructureMapping);
    addInstanceElements(instanceElements, existingInstanceElements, false);

    azureVMSSStateHelper.saveInstanceInfoToSweepingOutput(context, instanceElements);

    InstanceElementListParam instanceElementListParam =
        InstanceElementListParam.builder().instanceElements(instanceElements).build();

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(executionResponse.getErrorMessage())
        .stateExecutionData(stateExecutionData)
        .contextElement(instanceElementListParam)
        .notifyElement(instanceElementListParam)
        .build();
  }

  private List<InstanceElement> getNewInstanceElements(ExecutionContext context,
      AzureVMSSDeployTaskResponse azureVMSSDeployTaskResponse,
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping) {
    return azureVMSSStateHelper.generateInstanceElements(
        context, azureVMSSInfrastructureMapping, azureVMSSDeployTaskResponse.getVmInstancesAdded());
  }

  private void addInstanceElements(
      List<InstanceElement> instanceElements, List<InstanceElement> instanceElementsToAdd, boolean newInstance) {
    if (isNotEmpty(instanceElementsToAdd)) {
      azureVMSSStateHelper.setNewInstance(instanceElementsToAdd, newInstance);
      instanceElements.addAll(instanceElementsToAdd);
    }
  }

  private void populateAzureVMSSDeployStateExecutionData(AzureVMSSDeployStateExecutionData stateExecutionData,
      AzureVMSSTaskExecutionResponse executionResponse, ExecutionStatus executionStatus,
      List<InstanceElement> newInstanceElements) {
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    if (isNotEmpty(newInstanceElements)) {
      List<InstanceStatusSummary> newInstanceStatusSummaries =
          azureVMSSStateHelper.getInstanceStatusSummaries(executionStatus, newInstanceElements);
      stateExecutionData.setNewInstanceStatusSummaries(newInstanceStatusSummaries);
    }
  }

  private List<InstanceElement> getExistingInstanceElements(ExecutionContext context,
      AzureVMSSDeployTaskResponse azureVMSSDeployTaskResponse,
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping) {
    return azureVMSSStateHelper.generateInstanceElements(
        context, azureVMSSInfrastructureMapping, azureVMSSDeployTaskResponse.getVmInstancesExisting());
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (instanceCount == null || instanceCount < 0) {
      invalidFields.put("instanceCount", "Instance count needs to be populated");
    }
    return invalidFields;
  }

  @Override
  public List<CommandUnit> getCommandUnits(ExecutionContext context, ResizeStrategy resizeStrategy) {
    List<CommandUnit> commandUnitList;
    if (OrchestrationWorkflowType.BLUE_GREEN == context.getOrchestrationWorkflowType()) {
      commandUnitList = ImmutableList.of(new AzureVMSSDummyCommandUnit(UP_SCALE_COMMAND_UNIT),
          new AzureVMSSDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
          new AzureVMSSDummyCommandUnit(DEPLOYMENT_STATUS), new AzureVMSSDummyCommandUnit(DEPLOYMENT_ERROR));
    } else {
      commandUnitList = newArrayList();
      if (isRollback() || ResizeStrategy.RESIZE_NEW_FIRST == resizeStrategy) {
        commandUnitList.add(new AzureVMSSDummyCommandUnit(UP_SCALE_COMMAND_UNIT));
        commandUnitList.add(new AzureVMSSDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
        commandUnitList.add(new AzureVMSSDummyCommandUnit(DOWN_SCALE_COMMAND_UNIT));
        commandUnitList.add(new AzureVMSSDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
      } else {
        commandUnitList.add(new AzureVMSSDummyCommandUnit(DOWN_SCALE_COMMAND_UNIT));
        commandUnitList.add(new AzureVMSSDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
        commandUnitList.add(new AzureVMSSDummyCommandUnit(UP_SCALE_COMMAND_UNIT));
        commandUnitList.add(new AzureVMSSDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
      }
      if (isRollback()) {
        commandUnitList.add(new AzureVMSSDummyCommandUnit(DELETE_NEW_VMSS));
      }
      commandUnitList.add(new AzureVMSSDummyCommandUnit(DEPLOYMENT_STATUS));
      commandUnitList.add(new AzureVMSSDummyCommandUnit(DEPLOYMENT_ERROR));
    }
    return commandUnitList;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
