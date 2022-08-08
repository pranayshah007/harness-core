/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.Misc.normalizeExpression;

import static software.wings.beans.Log.Builder.aLog;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SETUP_COMMAND_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_DESIRED_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MAX_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MIN_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_TIMEOUT_MIN;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.LOAD_BALANCER_DETAILS;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.tasks.ResponseData;

import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsAmiSetupExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Log;
import software.wings.beans.Service;
import software.wings.beans.TaskType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupResponse;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.spotinst.SpotInstStateHelper;
import software.wings.utils.AsgConvention;

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
public class AwsAmiServiceTrafficShiftAlbSetup extends State {
  @Getter @Setter private String autoScalingGroupName;
  @Getter @Setter private String autoScalingSteadyStateTimeout;
  @Getter @Setter private boolean useCurrentRunningCount;
  @Getter @Setter private String minInstancesExpr;
  @Getter @Setter private String maxInstancesExpr;
  @Getter @Setter private String targetInstancesExpr;
  @Getter @Setter private List<LbDetailsForAlbTrafficShift> lbDetails;

  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ActivityService activityService;
  @Inject private LogService logService;
  @Inject private DelegateService delegateService;
  @Inject private SpotInstStateHelper spotinstStateHelper;
  @Inject private AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private AwsStateHelper awsStateHelper;
  @Inject private FeatureFlagService featureFlagService;

  private static final String COMMAND_NAME = AMI_SETUP_COMMAND_NAME;

  public AwsAmiServiceTrafficShiftAlbSetup(String name) {
    super(name, StateType.ASG_AMI_SERVICE_ALB_SHIFT_SETUP.name());
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

  private ExecutionResponse executeInternal(ExecutionContext context) {
    AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData = awsAmiServiceHelper.populateAlbTrafficShiftSetupData(context);
    Activity activity = createActivity(context, awsAmiTrafficShiftAlbData);
    ManagerExecutionLogCallback executionLogCallback =
        new ManagerExecutionLogCallback(logService, getLogBuilder(activity), activity.getUuid());
    try {
      executionLogCallback.saveExecutionLog("Starting AWS AMI Setup");
      createAndEnqueueDelegateTask(context, activity, awsAmiTrafficShiftAlbData);
    } catch (Exception exception) {
      return failureResponse(exception, activity.getUuid(), executionLogCallback);
    }
    return successResponse(context, activity.getUuid());
  }

  private void createAndEnqueueDelegateTask(
      ExecutionContext context, Activity activity, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData) {
    AwsAmiServiceTrafficShiftAlbSetupRequest amiTrafficShiftRequest =
        buildAmiTrafficShiftSetupRequest(context, awsAmiTrafficShiftAlbData, activity);

    long timeout = TimeUnit.MINUTES.toMillis(
        renderExpression(context, autoScalingSteadyStateTimeout, (int) DEFAULT_ASYNC_CALL_TIMEOUT));
    setTimeoutMillis((int) timeout);

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(awsAmiTrafficShiftAlbData.getApp().getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, awsAmiTrafficShiftAlbData.getApp().getUuid())
            .waitId(activity.getUuid())
            .tags(isNotEmpty(amiTrafficShiftRequest.getAwsConfig().getTag())
                    ? singletonList(amiTrafficShiftRequest.getAwsConfig().getTag())
                    : null)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.AWS_AMI_ASYNC_TASK.name())
                      .parameters(new Object[] {amiTrafficShiftRequest})
                      .timeout(timeout)
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, awsAmiTrafficShiftAlbData.getEnv().getUuid())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, awsAmiTrafficShiftAlbData.getEnv().getEnvironmentType().name())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("AWS AMI service traffic shift ALB setup task execution")
            .build();
    delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
  }

  private Activity createActivity(ExecutionContext context, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData) {
    Application app = awsAmiTrafficShiftAlbData.getApp();
    Service service = awsAmiTrafficShiftAlbData.getService();
    Environment env = awsAmiTrafficShiftAlbData.getEnv();
    String envId = awsAmiTrafficShiftAlbData.getEnv().getUuid();

    Command command =
        serviceResourceService
            .getCommandByName(app.getUuid(), awsAmiTrafficShiftAlbData.getServiceId(), envId, COMMAND_NAME)
            .getCommand();

    List<CommandUnit> commandUnitList = serviceResourceService.getFlattenCommandUnitList(
        app.getUuid(), awsAmiTrafficShiftAlbData.getServiceId(), envId, command.getName());

    Activity activity = Activity.builder()
                            .applicationName(app.getName())
                            .appId(app.getUuid())
                            .environmentId(envId)
                            .environmentName(env.getName())
                            .environmentType(env.getEnvironmentType())
                            .serviceId(service.getUuid())
                            .serviceName(service.getName())
                            .commandName(command.getName())
                            .type(Activity.Type.Command)
                            .workflowExecutionId(context.getWorkflowExecutionId())
                            .workflowId(context.getWorkflowId())
                            .workflowType(context.getWorkflowType())
                            .workflowExecutionName(context.getWorkflowExecutionName())
                            .stateExecutionInstanceId(context.getStateExecutionInstanceId())
                            .stateExecutionInstanceName(context.getStateExecutionInstanceName())
                            .commandUnits(commandUnitList)
                            .commandType(command.getCommandUnitType().name())
                            .status(ExecutionStatus.RUNNING)
                            .triggeredBy(TriggeredBy.builder()
                                             .email(awsAmiTrafficShiftAlbData.getCurrentUser().getEmail())
                                             .name(awsAmiTrafficShiftAlbData.getCurrentUser().getName())
                                             .build())
                            .build();

    return activityService.save(activity);
  }

  private AwsAmiServiceTrafficShiftAlbSetupRequest buildAmiTrafficShiftSetupRequest(
      ExecutionContext context, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData, Activity activity) {
    return AwsAmiServiceTrafficShiftAlbSetupRequest.builder()
        .accountId(awsAmiTrafficShiftAlbData.getApp().getAccountId())
        .appId(awsAmiTrafficShiftAlbData.getApp().getAppId())
        .activityId(activity.getUuid())
        .commandName(COMMAND_NAME)
        .awsConfig(awsAmiTrafficShiftAlbData.getAwsConfig())
        .encryptionDetails(awsAmiTrafficShiftAlbData.getAwsEncryptedDataDetails())
        .region(awsAmiTrafficShiftAlbData.getRegion())
        .infraMappingAsgName(awsAmiTrafficShiftAlbData.getInfrastructureMapping().getAutoScalingGroupName())
        .infraMappingId(awsAmiTrafficShiftAlbData.getInfrastructureMapping().getUuid())
        .artifactRevision(awsAmiTrafficShiftAlbData.getArtifact().getRevision())
        .newAsgNamePrefix(getAsgNamePrefix(context, awsAmiTrafficShiftAlbData))
        .minInstances(renderExpression(context, minInstancesExpr, DEFAULT_AMI_ASG_MIN_INSTANCES))
        .maxInstances(renderExpression(context, maxInstancesExpr, DEFAULT_AMI_ASG_MAX_INSTANCES))
        .desiredInstances(renderExpression(context, targetInstancesExpr, DEFAULT_AMI_ASG_DESIRED_INSTANCES))
        .autoScalingSteadyStateTimeout(
            renderExpression(context, autoScalingSteadyStateTimeout, DEFAULT_AMI_ASG_TIMEOUT_MIN))
        .useCurrentRunningCount(useCurrentRunningCount)
        .lbDetails(spotinstStateHelper.getRenderedLbDetails(context, lbDetails))
        .userData(awsStateHelper.getEncodedUserData(
            awsAmiTrafficShiftAlbData.getApp().getUuid(), awsAmiTrafficShiftAlbData.getServiceId(), context))
        .amiInServiceHealthyStateFFEnabled(false)
        .amiAsgConfigCopyEnabled(featureFlagService.isEnabled(
            FeatureName.AMI_ASG_CONFIG_COPY, awsAmiTrafficShiftAlbData.getApp().getAccountId()))
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    AwsAmiServiceTrafficShiftAlbSetupResponse amiServiceSetupResponse =
        (AwsAmiServiceTrafficShiftAlbSetupResponse) response.values().iterator().next();
    activityService.updateStatus(activityId, context.getAppId(), amiServiceSetupResponse.getExecutionStatus());

    AwsAmiSetupExecutionData awsAmiExecutionData = context.getStateExecutionData();
    awsAmiExecutionData.setNewAutoScalingGroupName(amiServiceSetupResponse.getNewAsgName());
    awsAmiExecutionData.setOldAutoScalingGroupName(amiServiceSetupResponse.getLastDeployedAsgName());
    awsAmiExecutionData.setNewVersion(amiServiceSetupResponse.getHarnessRevision());
    awsAmiExecutionData.setDelegateMetaInfo(amiServiceSetupResponse.getDelegateMetaInfo());
    awsAmiExecutionData.setMaxInstances(amiServiceSetupResponse.getMaxInstances());
    awsAmiExecutionData.setDesiredInstances(amiServiceSetupResponse.getDesiredInstances());

    AmiServiceTrafficShiftAlbSetupElement amiServiceElement = buildContextElement(context, amiServiceSetupResponse);
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(awsAmiServiceHelper.getSweepingOutputName(context, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME))
            .value(amiServiceElement)
            .build());

    awsStateHelper.populateAmiVariables(context, amiServiceElement.fetchAmiVariableInfo());
    return createAsyncResponse(activityId, amiServiceSetupResponse, awsAmiExecutionData);
  }

  private AmiServiceTrafficShiftAlbSetupElement buildContextElement(
      ExecutionContext context, AwsAmiServiceTrafficShiftAlbSetupResponse amiServiceSetupResponse) {
    return AmiServiceTrafficShiftAlbSetupElement.builder()
        .newAutoScalingGroupName(amiServiceSetupResponse.getNewAsgName())
        .oldAutoScalingGroupName(amiServiceSetupResponse.getLastDeployedAsgName())
        .baseScalingPolicyJSONs(amiServiceSetupResponse.getBaseAsgScalingPolicyJSONs())
        .minInstances(amiServiceSetupResponse.getMinInstances())
        .maxInstances(amiServiceSetupResponse.getMaxInstances())
        .desiredInstances(amiServiceSetupResponse.getDesiredInstances())
        .autoScalingSteadyStateTimeout(
            renderExpression(context, autoScalingSteadyStateTimeout, DEFAULT_AMI_ASG_TIMEOUT_MIN))
        .commandName(COMMAND_NAME)
        .oldAsgNames(amiServiceSetupResponse.getOldAsgNames())
        .preDeploymentData(amiServiceSetupResponse.getPreDeploymentData())
        .detailsWithTargetGroups(amiServiceSetupResponse.getLbDetailsWithTargetGroups())
        .baseAsgScheduledActionJSONs(
            featureFlagService.isEnabled(FeatureName.AMI_ASG_CONFIG_COPY, context.getAccountId())
                ? amiServiceSetupResponse.getBaseAsgScheduledActionJSONs()
                : null)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Do nothing on abort
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(autoScalingGroupName)) {
      invalidFields.put(WorkflowServiceHelper.AUTO_SCALING_GROUP_NAME, "Auto Scaling Group name must be specified");
    }
    if (isEmpty(lbDetails)) {
      invalidFields.put(LOAD_BALANCER_DETAILS, "Load balancer details are required");
    }
    return invalidFields;
  }

  private Integer renderExpression(ExecutionContext context, String expression, int defaultValue) {
    return spotinstStateHelper.renderCount(expression, context, defaultValue);
  }

  @NotNull
  private Log.Builder getLogBuilder(Activity activity) {
    return aLog()
        .appId(activity.getAppId())
        .activityId(activity.getUuid())
        .commandUnitName(activity.getCommandUnits().get(0).getName());
  }

  private ExecutionResponse failureResponse(
      Exception exception, String activityId, ManagerExecutionLogCallback executionLogCallback) {
    log.error("Ami setup step failed with error ", exception);
    Misc.logAllMessages(exception, executionLogCallback, CommandExecutionStatus.FAILURE);
    AwsAmiSetupExecutionData awsAmiExecutionData = AwsAmiSetupExecutionData.builder().build();
    String errorMessage = getMessage(exception);
    return createResponse(activityId, ExecutionStatus.FAILED, errorMessage, awsAmiExecutionData, true);
  }

  private ExecutionResponse successResponse(ExecutionContext context, String activityId) {
    AwsAmiSetupExecutionData awsAmiExecutionData =
        AwsAmiSetupExecutionData.builder()
            .activityId(activityId)
            .maxInstances(useCurrentRunningCount
                    ? null
                    : renderExpression(context, maxInstancesExpr, DEFAULT_AMI_ASG_MAX_INSTANCES))
            .desiredInstances(useCurrentRunningCount
                    ? null
                    : renderExpression(context, targetInstancesExpr, DEFAULT_AMI_ASG_DESIRED_INSTANCES))
            .build();
    return createResponse(activityId, ExecutionStatus.SUCCESS, null, awsAmiExecutionData, true);
  }

  private ExecutionResponse createAsyncResponse(String activityId,
      AwsAmiServiceTrafficShiftAlbSetupResponse amiServiceSetupResponse, AwsAmiSetupExecutionData executionData) {
    ExecutionStatus executionStatus = amiServiceSetupResponse.getExecutionStatus();
    String errorMessage = amiServiceSetupResponse.getErrorMessage();
    return createResponse(activityId, executionStatus, errorMessage, executionData, false);
  }

  private ExecutionResponse createResponse(String activityId, ExecutionStatus status, String errorMessage,
      AwsAmiSetupExecutionData executionData, boolean isAsync) {
    ExecutionResponseBuilder responseBuilder = ExecutionResponse.builder();
    return responseBuilder.correlationIds(singletonList(activityId))
        .executionStatus(status)
        .errorMessage(errorMessage)
        .stateExecutionData(executionData)
        .async(isAsync)
        .build();
  }

  private String getAsgNamePrefix(ExecutionContext context, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData) {
    String appName = awsAmiTrafficShiftAlbData.getApp().getName();
    String serviceName = awsAmiTrafficShiftAlbData.getService().getName();
    String envName = awsAmiTrafficShiftAlbData.getEnv().getName();
    String asgNamePrefix = isEmpty(autoScalingGroupName) ? AsgConvention.getAsgNamePrefix(appName, serviceName, envName)
                                                         : context.renderExpression(autoScalingGroupName);
    return normalizeExpression(asgNamePrefix);
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
