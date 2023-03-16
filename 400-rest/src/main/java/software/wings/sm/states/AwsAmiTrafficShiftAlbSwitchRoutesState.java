/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static software.wings.beans.TaskType.AWS_AMI_ASYNC_TASK;
import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.sm.states.AwsAmiSwitchRoutesState.SWAP_AUTO_SCALING_ROUTES;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.tasks.ResponseData;

import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsAmiSetupExecutionData;
import software.wings.api.AwsAmiTrafficShiftAlbStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.beans.dto.Log;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesResponse;
import software.wings.service.impl.aws.model.AwsAmiTrafficShiftAlbSwitchRouteRequest;
import software.wings.service.impl.aws.model.AwsConstants;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.spotinst.SpotInstStateHelper;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AwsAmiTrafficShiftAlbSwitchRoutesState extends State {
  @Getter @Setter private boolean downsizeOldAsg;
  @Getter @Setter private String newAutoScalingGroupWeightExpr;

  @Inject private SpotInstStateHelper spotinstStateHelper;
  @Inject private AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Inject protected DelegateService delegateService;
  @Inject private ActivityService activityService;
  @Inject private LogService logService;
  @Inject private FeatureFlagService featureFlagService;

  public AwsAmiTrafficShiftAlbSwitchRoutesState(String name) {
    super(name, StateType.ASG_AMI_ALB_SHIFT_SWITCH_ROUTES.name());
  }

  public AwsAmiTrafficShiftAlbSwitchRoutesState(String name, String stateType) {
    super(name, stateType);
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
    AwsAmiSwitchRoutesResponse executionResponse = (AwsAmiSwitchRoutesResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getExecutionStatus();
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    AwsAmiTrafficShiftAlbStateExecutionData stateExecutionData =
        (AwsAmiTrafficShiftAlbStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setStatus(executionStatus);

    spotinstStateHelper.saveInstanceInfoToSweepingOutput(context, getNewAutoscalingGroupWeight(context));

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .stateExecutionData(stateExecutionData)
        .errorMessage(executionResponse.getErrorMessage())
        .build();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    Activity activity = spotinstStateHelper.createActivity(context, null, getStateType(), getName(),
        CommandUnitDetails.CommandUnitType.AWS_AMI_SWITCH_ROUTES, getCommandUnits());

    ManagerExecutionLogCallback executionLogCallback =
        new ManagerExecutionLogCallback(logService, getLogBuilder(activity), activity.getUuid());
    try {
      AmiServiceTrafficShiftAlbSetupElement setupElement =
          (AmiServiceTrafficShiftAlbSetupElement) awsAmiServiceHelper.getSetupElementFromSweepingOutput(
              context, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME);
      if (setupElement == null) {
        return skipResponse();
      }
      AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData =
          awsAmiServiceHelper.populateAlbTrafficShiftSetupData(context);

      AwsAmiTrafficShiftAlbSwitchRouteRequest trafficShiftRequest =
          buildAmiTrafficShiftSwitchRouteRequest(context, setupElement, awsAmiTrafficShiftAlbData, activity);

      AwsAmiTrafficShiftAlbStateExecutionData executionData =
          createAndEnqueueDelegateTask(context, setupElement, awsAmiTrafficShiftAlbData, activity, trafficShiftRequest);
      return successResponse(activity, executionData);
    } catch (Exception exception) {
      return taskCreationFailureResponse(activity, executionLogCallback, exception);
    }
  }

  private AwsAmiTrafficShiftAlbStateExecutionData createAndEnqueueDelegateTask(ExecutionContext context,
      AmiServiceTrafficShiftAlbSetupElement setupElement, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData,
      Activity activity, AwsAmiTrafficShiftAlbSwitchRouteRequest trafficShiftRequest) {
    AwsAmiTrafficShiftAlbStateExecutionData executionData =
        AwsAmiTrafficShiftAlbStateExecutionData.builder()
            .activityId(activity.getUuid())
            .oldAutoScalingGroupName(setupElement.getOldAutoScalingGroupName())
            .newAutoScalingGroupName(setupElement.getNewAutoScalingGroupName())
            .newAutoscalingGroupWeight(getNewAutoscalingGroupWeight(context))
            .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(awsAmiTrafficShiftAlbData.getApp().getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, awsAmiTrafficShiftAlbData.getApp().getAppId())
            .waitId(activity.getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(AWS_AMI_ASYNC_TASK.name())
                      .parameters(new Object[] {trafficShiftRequest})
                      .timeout(TimeUnit.MINUTES.toMillis(setupElement.getAutoScalingSteadyStateTimeout()))
                      .build())
            .tags(isNotEmpty(trafficShiftRequest.getAwsConfig().getTag())
                    ? singletonList(trafficShiftRequest.getAwsConfig().getTag())
                    : null)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, awsAmiTrafficShiftAlbData.getEnv().getUuid())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, awsAmiTrafficShiftAlbData.getEnv().getEnvironmentType().name())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("AWS AMI Traffic shift ALB switch routes task execution")
            .build();
    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return executionData;
  }

  private AwsAmiTrafficShiftAlbSwitchRouteRequest buildAmiTrafficShiftSwitchRouteRequest(ExecutionContext context,
      AmiServiceTrafficShiftAlbSetupElement setupElement, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData,
      Activity activity) {
    return AwsAmiTrafficShiftAlbSwitchRouteRequest.builder()
        .accountId(awsAmiTrafficShiftAlbData.getApp().getAccountId())
        .appId(awsAmiTrafficShiftAlbData.getApp().getUuid())
        .activityId(activity.getUuid())
        .commandName(SWAP_AUTO_SCALING_ROUTES)
        .oldAsgName(setupElement.getOldAutoScalingGroupName())
        .newAsgName(setupElement.getNewAutoScalingGroupName())
        .downscaleOldAsg(downsizeOldAsg)
        .rollback(isRollback())
        .newAutoscalingGroupWeight(getNewAutoscalingGroupWeight(context))
        .timeoutIntervalInMin(setupElement.getAutoScalingSteadyStateTimeout())
        .preDeploymentData(setupElement.getPreDeploymentData())
        .baseScalingPolicyJSONs(setupElement.getBaseScalingPolicyJSONs())
        .lbDetails(setupElement.getDetailsWithTargetGroups())
        .awsConfig(awsAmiTrafficShiftAlbData.getAwsConfig())
        .encryptionDetails(awsAmiTrafficShiftAlbData.getAwsEncryptedDataDetails())
        .region(awsAmiTrafficShiftAlbData.getRegion())
        .amiInServiceHealthyStateFFEnabled(false)
        .amiAsgConfigCopyEnabled(featureFlagService.isEnabled(
            FeatureName.AMI_ASG_CONFIG_COPY, awsAmiTrafficShiftAlbData.getApp().getAccountId()))
        .build();
  }

  protected List<CommandUnit> getCommandUnits() {
    return ImmutableList.of(new SpotinstDummyCommandUnit(SWAP_AUTO_SCALING_ROUTES),
        new SpotinstDummyCommandUnit(AwsConstants.DOWN_SCALE_ASG_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
  }

  protected int getNewAutoscalingGroupWeight(ExecutionContext context) {
    return spotinstStateHelper.renderCount(newAutoScalingGroupWeightExpr, context, DEFAULT_TRAFFIC_SHIFT_WEIGHT);
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(newAutoScalingGroupWeightExpr)) {
      invalidFields.put("newAutoScalingGroupWeightExpr", "New Autoscaling Group weight is needed");
    }
    return invalidFields;
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Do nothing on abort
  }

  @Override
  public boolean isRollback() {
    return false;
  }

  @NotNull
  private Log.Builder getLogBuilder(Activity activity) {
    return aLog()
        .appId(activity.getAppId())
        .activityId(activity.getUuid())
        .commandUnitName(activity.getCommandUnits().get(0).getName());
  }

  private ExecutionResponse taskCreationFailureResponse(
      Activity activity, ManagerExecutionLogCallback executionLogCallback, Exception exception) {
    log.error("Aws Ami traffic shift failed with error ", exception);
    Misc.logAllMessages(exception, executionLogCallback, CommandExecutionStatus.FAILURE);
    AwsAmiSetupExecutionData awsAmiExecutionData = AwsAmiSetupExecutionData.builder().build();
    String errorMessage = getMessage(exception);
    ExecutionResponseBuilder responseBuilder = ExecutionResponse.builder();
    return responseBuilder.correlationIds(singletonList(activity.getUuid()))
        .executionStatus(FAILED)
        .errorMessage(errorMessage)
        .stateExecutionData(awsAmiExecutionData)
        .async(true)
        .build();
  }

  private ExecutionResponse skipResponse() {
    return ExecutionResponse.builder()
        .executionStatus(SKIPPED)
        .errorMessage("No Aws Ami service setup element found. Skipping rollback.")
        .build();
  }

  private ExecutionResponse successResponse(Activity activity, AwsAmiTrafficShiftAlbStateExecutionData executionData) {
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
