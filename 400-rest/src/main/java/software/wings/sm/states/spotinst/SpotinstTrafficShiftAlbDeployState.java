/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.spotinst.model.SpotInstConstants.DEPLOYMENT_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.PHASE_PARAM;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_SERVICE_ALB_SETUP_SWEEPING_OUTPUT_NAME;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.sm.states.spotinst.SpotInstDeployState.SPOTINST_DEPLOY_COMMAND;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbDeployParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbDeployResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.AwsStateHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbDeployState extends State {
  @Getter @Setter private InstanceUnitType instanceUnitType = PERCENTAGE;
  @Getter @Setter private String instanceCountExpr = "100";

  @Inject private AwsStateHelper awsStateHelper;
  @Inject private DelegateService delegateService;
  @Inject private ActivityService activityService;
  @Inject private SpotInstStateHelper spotinstStateHelper;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  public SpotinstTrafficShiftAlbDeployState(String name) {
    super(name, StateType.SPOTINST_ALB_SHIFT_DEPLOY.name());
  }

  @VisibleForTesting
  SpotinstTrafficShiftAlbDeployState() {
    super("stateName", StateType.SPOTINST_ALB_SHIFT_DEPLOY.name());
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    SpotInstTaskExecutionResponse executionResponse =
        (SpotInstTaskExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    SpotinstTrafficShiftAlbDeployExecutionData stateExecutionData =
        (SpotinstTrafficShiftAlbDeployExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

    SpotinstTrafficShiftAlbDeployResponse taskResponse =
        (SpotinstTrafficShiftAlbDeployResponse) executionResponse.getSpotInstTaskResponse();
    List<InstanceElement> instanceElements = newArrayList();
    if (taskResponse != null) {
      AwsAmiInfrastructureMapping awsAmiInfrastructureMapping =
          (AwsAmiInfrastructureMapping) infrastructureMappingService.get(
              stateExecutionData.getAppId(), stateExecutionData.getInfraMappingId());
      List<InstanceElement> newInstanceElements = awsStateHelper.generateInstanceElements(
          taskResponse.getEc2InstancesAdded(), awsAmiInfrastructureMapping, context);
      List<InstanceElement> oldInstanceElements = awsStateHelper.generateInstanceElements(
          taskResponse.getEc2InstancesExisting(), awsAmiInfrastructureMapping, context);
      if (isNotEmpty(newInstanceElements)) {
        // These are newly launched instances, set NewInstance = true for verification service
        newInstanceElements.forEach(instanceElement -> instanceElement.setNewInstance(true));
        instanceElements.addAll(newInstanceElements);
      }

      if (isNotEmpty(oldInstanceElements)) {
        instanceElements.addAll(oldInstanceElements);
      }
    }
    InstanceElementListParam instanceElementListParam =
        InstanceElementListParam.builder().instanceElements(instanceElements).build();

    if (FAILED == executionStatus && executionResponse.isTimeoutError()) {
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .failureTypes(FailureType.TIMEOUT)
          .errorMessage(executionResponse.getErrorMessage())
          .stateExecutionData(stateExecutionData)
          .build();
    }

    spotinstStateHelper.saveInstanceInfoToSweepingOutput(context, instanceElements);

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(executionResponse.getErrorMessage())
        .stateExecutionData(stateExecutionData)
        .contextElement(instanceElementListParam)
        .notifyElement(instanceElementListParam)
        .build();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    SpotinstTrafficShiftAlbSetupElement setupElement =
        (SpotinstTrafficShiftAlbSetupElement) spotinstStateHelper.getSetupElementFromSweepingOutput(
            context, SPOTINST_SERVICE_ALB_SETUP_SWEEPING_OUTPUT_NAME);
    if (setupElement == null) {
      throw new InvalidRequestException("Did not find Setup element of class SpotinstTrafficShiftAlbSetupElement");
    }

    SpotinstTrafficShiftDataBag dataBag = spotinstStateHelper.getDataBag(context);

    Activity activity = spotinstStateHelper.createActivity(context, null, getStateType(), SPOTINST_DEPLOY_COMMAND,
        CommandUnitDetails.CommandUnitType.SPOTINST_DEPLOY,
        ImmutableList.of(new SpotinstDummyCommandUnit(UP_SCALE_COMMAND_UNIT),
            new SpotinstDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
            new SpotinstDummyCommandUnit(DEPLOYMENT_ERROR)));

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();

    SpotinstTrafficShiftAlbDeployExecutionData stateExecutionData =
        SpotinstTrafficShiftAlbDeployExecutionData.builder()
            .activityId(activity.getUuid())
            .serviceId(serviceElement.getUuid())
            .envId(dataBag.getEnv().getUuid())
            .appId(dataBag.getApp().getUuid())
            .infraMappingId(dataBag.getInfrastructureMapping().getUuid())
            .commandName(SPOTINST_DEPLOY_COMMAND)
            .newElastigroupOriginalConfig(setupElement.getNewElastiGroupOriginalConfig())
            .oldElastigroupOriginalConfig(setupElement.getOldElastiGroupOriginalConfig())
            .build();

    SpotinstTrafficShiftAlbDeployParameters parameters =
        SpotinstTrafficShiftAlbDeployParameters.builder()
            .appId(dataBag.getApp().getUuid())
            .accountId(dataBag.getApp().getAccountId())
            .activityId(activity.getUuid())
            .commandName(SPOTINST_DEPLOY_COMMAND)
            .workflowExecutionId(context.getWorkflowExecutionId())
            .timeoutIntervalInMin(setupElement.getTimeoutIntervalInMin())
            .awsRegion(dataBag.getInfrastructureMapping().getRegion())
            .newElastigroup(setupElement.getNewElastiGroupOriginalConfig())
            .oldElastigroup(setupElement.getOldElastiGroupOriginalConfig())
            .build();

    SpotInstCommandRequest commandRequest = SpotInstCommandRequest.builder()
                                                .spotInstTaskParameters(parameters)
                                                .awsConfig(dataBag.getAwsConfig())
                                                .spotInstConfig(dataBag.getSpotinstConfig())
                                                .awsEncryptionDetails(dataBag.getAwsEncryptedDataDetails())
                                                .spotinstEncryptionDetails(dataBag.getSpotinstEncryptedDataDetails())
                                                .build();

    DelegateTask delegateTask = spotinstStateHelper.getDelegateTask(dataBag.getApp().getAccountId(),
        dataBag.getApp().getUuid(), TaskType.SPOTINST_COMMAND_TASK, activity.getUuid(), dataBag.getEnv().getUuid(),
        dataBag.getInfrastructureMapping().getUuid(), commandRequest, dataBag.getEnv().getEnvironmentType(),
        dataBag.getInfrastructureMapping().getServiceId(), isSelectionLogsTrackingForTasksEnabled());

    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .correlationIds(singletonList(activity.getUuid()))
        .stateExecutionData(stateExecutionData)
        .async(true)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Do nothing on abort
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
