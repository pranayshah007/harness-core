/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.spotinst.model.SpotInstConstants.DEPLOYMENT_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static io.harness.spotinst.model.SpotInstConstants.SWAP_ROUTES_COMMAND_UNIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.spotinst.request.SpotInstSwapRoutesTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.tasks.ResponseData;

import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.impl.spotinst.SpotInstCommandRequest.SpotInstCommandRequestBuilder;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
public class SpotInstListenerUpdateState extends State {
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient ActivityService activityService;
  @Inject private transient SpotInstStateHelper spotInstStateHelper;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Getter @Setter private boolean downsizeOldElastiGroup;
  public static final String SPOTINST_LISTENER_UPDATE_COMMAND = "SpotInst Listener Update";

  /**
   * Instantiates a new state.
   *
   * @param name      the name
   */
  public SpotInstListenerUpdateState(String name) {
    super(name, StateType.SPOTINST_LISTENER_UPDATE.name());
  }

  public SpotInstListenerUpdateState(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  public boolean isRollback() {
    return false;
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);
    Application app = appService.get(context.getAppId());
    AwsAmiInfrastructureMapping awsAmiInfrastructureMapping =
        (AwsAmiInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    // retrieve SpotInstSetupContextElement
    SpotInstSetupContextElement spotInstSetupContextElement =
        (SpotInstSetupContextElement) spotInstStateHelper.getSetupElementFromSweepingOutput(
            context, SPOTINST_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    if (spotInstSetupContextElement == null) {
      throw new InvalidRequestException("Did not find Setup element of class SpotinstSetupContextElement");
    }

    // create activity with details
    Activity activity = spotInstStateHelper.createActivity(context, null, getStateType(),
        SPOTINST_LISTENER_UPDATE_COMMAND, CommandUnitType.SPOTINST_UPDATE_LISTENER, getCommandUnits());

    // Generate SpotInstListenerUpdateStateExecutionData
    SpotInstListenerUpdateStateExecutionData stateExecutionData =
        geStateExecutionData(spotInstSetupContextElement, activity);

    SpotInstTaskParameters spotInstTaskParameters =
        getTaskParameters(context, app, activity.getUuid(), awsAmiInfrastructureMapping, spotInstSetupContextElement);

    // Generate CommandRequest to be sent to delegate
    SpotInstCommandRequestBuilder requestBuilder =
        spotInstStateHelper.generateSpotInstCommandRequest(awsAmiInfrastructureMapping, context);
    SpotInstCommandRequest spotInstCommandRequest =
        requestBuilder.spotInstTaskParameters(spotInstTaskParameters).build();

    stateExecutionData.setSpotinstCommandRequest(spotInstCommandRequest);
    setElastigroupFieldsInStateExecutionData(spotInstSetupContextElement, stateExecutionData);

    DelegateTask task = spotInstStateHelper.getDelegateTask(app.getAccountId(), app.getUuid(),
        TaskType.SPOTINST_COMMAND_TASK, activity.getUuid(), env.getUuid(), awsAmiInfrastructureMapping.getUuid(),
        spotInstCommandRequest, env.getEnvironmentType(), awsAmiInfrastructureMapping.getServiceId(),
        isSelectionLogsTrackingForTasksEnabled());

    delegateService.queueTaskV2(task);
    appendDelegateTaskDetails(context, task);

    return ExecutionResponse.builder()
        .stateExecutionData(stateExecutionData)
        .correlationIds(Arrays.asList(activity.getUuid()))
        .async(true)
        .build();
  }

  protected List<CommandUnit> getCommandUnits() {
    return ImmutableList.of(new SpotinstDummyCommandUnit(SWAP_ROUTES_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DOWN_SCALE_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DEPLOYMENT_ERROR));
  }

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    SpotInstTaskExecutionResponse executionResponse =
        (SpotInstTaskExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    SpotInstListenerUpdateStateExecutionData stateExecutionData =
        (SpotInstListenerUpdateStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setStatus(executionStatus);

    if (FAILED == executionStatus && executionResponse.isTimeoutError()) {
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .failureTypes(FailureType.TIMEOUT)
          .errorMessage(executionResponse.getErrorMessage())
          .stateExecutionData(stateExecutionData)
          .build();
    }

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .stateExecutionData(stateExecutionData)
        .errorMessage(executionResponse.getErrorMessage())
        .build();
  }

  private void setElastigroupFieldsInStateExecutionData(
      SpotInstSetupContextElement spotinstSetupContextElement, SpotInstListenerUpdateStateExecutionData data) {
    ElastiGroup oldElastiGroupOriginalConfig = spotinstSetupContextElement.getOldElastiGroupOriginalConfig();
    if (oldElastiGroupOriginalConfig != null) {
      data.setOldElastiGroupId(oldElastiGroupOriginalConfig.getId());
      data.setOldElastiGroupName(oldElastiGroupOriginalConfig.getName());
    }
    ElastiGroup newElastiGroupOriginalConfig = spotinstSetupContextElement.getNewElastiGroupOriginalConfig();
    if (newElastiGroupOriginalConfig != null) {
      data.setNewElastiGroupId(newElastiGroupOriginalConfig.getId());
      data.setNewElastiGroupName(newElastiGroupOriginalConfig.getName());
    }
  }

  protected SpotInstSwapRoutesTaskParameters getTaskParameters(ExecutionContext context, Application app,
      String activityId, AwsAmiInfrastructureMapping awsAmiInfrastructureMapping,
      SpotInstSetupContextElement setupContextElement) {
    SpotInstCommandRequest commandRequest = setupContextElement.getCommandRequest();
    return SpotInstSwapRoutesTaskParameters.builder()
        .accountId(app.getAccountId())
        .appId(app.getAppId())
        .activityId(activityId)
        .awsRegion(awsAmiInfrastructureMapping.getRegion())
        .commandName(SPOTINST_LISTENER_UPDATE_COMMAND)
        .workflowExecutionId(context.getWorkflowExecutionId())
        .downsizeOldElastiGroup(downsizeOldElastiGroup)
        .newElastiGroup(setupContextElement.getNewElastiGroupOriginalConfig())
        .oldElastiGroup(setupContextElement.getOldElastiGroupOriginalConfig())
        .elastiGroupNamePrefix(setupContextElement.getElstiGroupNamePrefix())
        .lBdetailsForBGDeploymentList(setupContextElement.getLbDetailsForBGDeployment())
        .timeoutIntervalInMin(spotInstStateHelper.getTimeoutFromCommandRequest(commandRequest))
        .rollback(isRollback())
        .build();
  }

  private SpotInstListenerUpdateStateExecutionData geStateExecutionData(
      SpotInstSetupContextElement setupContextElement, Activity activity) {
    return SpotInstListenerUpdateStateExecutionData.builder()
        .activityId(activity.getUuid())
        .commandName(SPOTINST_LISTENER_UPDATE_COMMAND)
        .appId(setupContextElement.getAppId())
        .envId(setupContextElement.getEnvId())
        .infraId(setupContextElement.getInfraMappingId())
        .serviceId(setupContextElement.getServiceId())
        .downsizeOldElastiGroup(downsizeOldElastiGroup)
        .lbDetails(setupContextElement.getLbDetailsForBGDeployment())
        .isRollback(isRollback())
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
