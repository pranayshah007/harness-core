/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.AZURE_VMSS_SWAP_BACKEND_POOL;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.SETUP_ELEMENT_NOT_FOUND;
import static io.harness.azure.model.AzureConstants.SKIP_VMSS_DEPLOY;
import static io.harness.azure.model.AzureConstants.SWAP_ROUTE_FAILURE;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.exception.ExceptionUtils.getMessage;

import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.AZURE_VMSS_SWAP;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;

import io.harness.azure.model.AzureConstants;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.tasks.ResponseData;

import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.TaskType;
import software.wings.beans.command.AzureVMSSDummyCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class AzureVMSSSwitchRoutesState extends State {
  @Getter @Setter private boolean downsizeOldVMSS;

  public static final String AZURE_VMSS_SWAP_ROUTE = AzureConstants.AZURE_VMSS_SWAP_BACKEND_POOL;
  @Inject protected transient DelegateService delegateService;
  @Inject protected transient AzureVMSSStateHelper azureVMSSStateHelper;
  @Inject protected ActivityService activityService;

  public AzureVMSSSwitchRoutesState(String name) {
    super(name, StateType.AZURE_VMSS_SWITCH_ROUTES.name());
  }

  public AzureVMSSSwitchRoutesState(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    return azureVMSSStateHelper.getAzureVMSSStateTimeoutFromContext(context);
  }

  @Override
  public boolean isRollback() {
    return false;
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

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    Activity activity = azureVMSSStateHelper.createAndSaveActivity(
        context, null, getStateType(), AZURE_VMSS_SWAP_ROUTE, AZURE_VMSS_SWAP, getCommandUnits());
    ManagerExecutionLogCallback executionLogCallback = azureVMSSStateHelper.getExecutionLogCallback(activity);

    try {
      Optional<AzureVMSSSetupContextElement> setupElement = validateAndGetSetupElement(context);
      if (!setupElement.isPresent()) {
        return skipResponse();
      }

      AzureVMSSStateData azureVMSSStateData = azureVMSSStateHelper.populateStateData(context);
      AzureVMSSCommandRequest azureVMSSCommandRequest =
          buildAzureVMSSSwitchRouteRequest(setupElement.get(), azureVMSSStateData, activity);

      AzureVMSSSwitchRouteStateExecutionData stateExecutionData = createAndEnqueueDelegateTask(
          setupElement.get(), azureVMSSStateData, activity, azureVMSSCommandRequest, context);
      return successResponse(activity, stateExecutionData);
    } catch (Exception exception) {
      return taskCreationFailureResponse(activity, executionLogCallback, exception);
    }
  }

  private Optional<AzureVMSSSetupContextElement> validateAndGetSetupElement(ExecutionContext context) {
    ContextElement contextElement = context.getContextElement(ContextElementType.AZURE_VMSS_SETUP);
    if (!(contextElement instanceof AzureVMSSSetupContextElement)) {
      if (isRollback()) {
        return Optional.empty();
      }
      throw new InvalidRequestException(SETUP_ELEMENT_NOT_FOUND);
    }
    return Optional.of((AzureVMSSSetupContextElement) contextElement);
  }

  private AzureVMSSCommandRequest buildAzureVMSSSwitchRouteRequest(
      AzureVMSSSetupContextElement azureVMSSSetupContextElement, AzureVMSSStateData azureVMSSStateData,
      Activity activity) {
    AzureVMSSSwitchRouteTaskParameters azureVMSSSwitchRouteTaskParameters =
        buildAzureVMSSTaskParameters(azureVMSSSetupContextElement, azureVMSSStateData, activity);
    return AzureVMSSCommandRequest.builder()
        .azureConfigDTO(azureVMSSStateHelper.createAzureConfigDTO(azureVMSSStateData.getAzureConfig()))
        .azureConfigEncryptionDetails(azureVMSSStateData.getAzureEncryptedDataDetails())
        .azureVMSSTaskParameters(azureVMSSSwitchRouteTaskParameters)
        .build();
  }

  private AzureVMSSSwitchRouteTaskParameters buildAzureVMSSTaskParameters(
      AzureVMSSSetupContextElement azureVMSSSetupContextElement, AzureVMSSStateData azureVMSSStateData,
      Activity activity) {
    Application application = azureVMSSStateData.getApplication();
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping = azureVMSSStateData.getInfrastructureMapping();

    return AzureVMSSSwitchRouteTaskParameters.builder()
        .accountId(application.getAccountId())
        .appId(application.getAppId())
        .activityId(activity.getUuid())
        .commandName(AZURE_VMSS_SWAP_ROUTE)
        .subscriptionId(azureVMSSInfrastructureMapping.getSubscriptionId())
        .resourceGroupName(azureVMSSInfrastructureMapping.getResourceGroupName())
        .oldVMSSName(azureVMSSSetupContextElement.getOldVirtualMachineScaleSetName())
        .newVMSSName(azureVMSSSetupContextElement.getNewVirtualMachineScaleSetName())
        .downscaleOldVMSS(downsizeOldVMSS)
        .rollback(isRollback())
        .preDeploymentData(azureVMSSSetupContextElement.getPreDeploymentData())
        .azureLoadBalancerDetail(azureVMSSSetupContextElement.getAzureLoadBalancerDetail())
        .autoScalingSteadyStateVMSSTimeout(azureVMSSSetupContextElement.getAutoScalingSteadyStateVMSSTimeout())
        .build();
  }

  private AzureVMSSSwitchRouteStateExecutionData createAndEnqueueDelegateTask(AzureVMSSSetupContextElement setupElement,
      AzureVMSSStateData azureVMSSStateData, Activity activity, AzureVMSSCommandRequest commandRequest,
      ExecutionContext context) {
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail = setupElement.getAzureLoadBalancerDetail();
    AzureVMSSSwitchRouteStateExecutionData stateExecutionData =
        AzureVMSSSwitchRouteStateExecutionData.builder()
            .activityId(activity.getUuid())
            .newVirtualMachineScaleSetName(setupElement.getNewVirtualMachineScaleSetName())
            .oldVirtualMachineScaleSetName(setupElement.getOldVirtualMachineScaleSetName())
            .stageBackendPool(azureLoadBalancerDetail.getStageBackendPool())
            .prodBackendPool(azureLoadBalancerDetail.getProdBackendPool())
            .build();

    Application application = azureVMSSStateData.getApplication();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(application.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, application.getUuid())
            .waitId(activity.getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.AZURE_VMSS_COMMAND_TASK.name())
                      .parameters(new Object[] {commandRequest})
                      .timeout(MINUTES.toMillis(setupElement.getAutoScalingSteadyStateVMSSTimeout()))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, azureVMSSStateData.getEnvironment().getUuid())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, azureVMSSStateData.getEnvironment().getEnvironmentType().name())
            .setupAbstraction(
                Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, azureVMSSStateData.getInfrastructureMapping().getUuid())
            .setupAbstraction(
                Cd1SetupFields.SERVICE_ID_FIELD, azureVMSSStateData.getInfrastructureMapping().getServiceId())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Azure VMSS switch routes task execution")
            .build();
    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
    return stateExecutionData;
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

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    AzureVMSSTaskExecutionResponse executionResponse =
        (AzureVMSSTaskExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = azureVMSSStateHelper.getExecutionStatus(executionResponse);

    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    AzureVMSSSwitchRouteStateExecutionData stateExecutionData = context.getStateExecutionData();
    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setStatus(executionStatus);

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .stateExecutionData(stateExecutionData)
        .errorMessage(executionResponse.getErrorMessage())
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Do nothing on abort
  }

  protected List<CommandUnit> getCommandUnits() {
    return ImmutableList.of(new AzureVMSSDummyCommandUnit(AZURE_VMSS_SWAP_BACKEND_POOL),
        new AzureVMSSDummyCommandUnit(DOWN_SCALE_COMMAND_UNIT),
        new AzureVMSSDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
  }

  private ExecutionResponse skipResponse() {
    return ExecutionResponse.builder().executionStatus(SKIPPED).errorMessage(getSkipMessage()).build();
  }

  protected String getSkipMessage() {
    return SKIP_VMSS_DEPLOY;
  }

  private ExecutionResponse taskCreationFailureResponse(
      Activity activity, ManagerExecutionLogCallback executionLogCallback, Exception exception) {
    log.error(SWAP_ROUTE_FAILURE, exception);
    Misc.logAllMessages(exception, executionLogCallback, CommandExecutionStatus.FAILURE);
    AzureVMSSSwitchRouteStateExecutionData switchRouteStateExecutionData =
        AzureVMSSSwitchRouteStateExecutionData.builder().build();
    String errorMessage = getMessage(exception);
    ExecutionResponseBuilder responseBuilder = ExecutionResponse.builder();
    return responseBuilder.correlationIds(singletonList(activity.getUuid()))
        .executionStatus(FAILED)
        .errorMessage(errorMessage)
        .stateExecutionData(switchRouteStateExecutionData)
        .async(true)
        .build();
  }

  private ExecutionResponse successResponse(Activity activity, AzureVMSSSwitchRouteStateExecutionData executionData) {
    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(executionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .correlationId(activity.getUuid())
        .build();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
