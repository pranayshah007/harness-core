/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.Log.Builder.aLog;
import static software.wings.beans.TaskType.AWS_AMI_ASYNC_TASK;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.sm.states.AwsAmiServiceDeployState.ASG_COMMAND_NAME;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.tasks.ResponseData;

import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.ContainerServiceData;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.Log.Builder;
import software.wings.beans.Service;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbDeployRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.spotinst.SpotInstStateHelper;
import software.wings.stencils.DefaultValue;

import com.amazonaws.services.ec2.model.Instance;
import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
public class AwsAmiServiceTrafficShiftAlbDeployState extends State {
  @Getter @Setter private InstanceUnitType instanceUnitType = PERCENTAGE;
  @Getter @Setter private String instanceCountExpr = "100";
  @Inject protected ServiceResourceService serviceResourceService;
  @Inject protected ArtifactStreamService artifactStreamService;
  @Inject protected ActivityService activityService;
  @Inject protected DelegateService delegateService;
  @Inject protected LogService logService;
  @Inject protected SweepingOutputService sweepingOutputService;
  @Inject protected AwsStateHelper awsStateHelper;
  @Inject protected AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Inject private SpotInstStateHelper spotinstStateHelper;
  @Inject private FeatureFlagService featureFlagService;

  @Attributes(title = "Command")
  @DefaultValue(ASG_COMMAND_NAME)
  private static final String COMMAND_NAME = ASG_COMMAND_NAME;

  public AwsAmiServiceTrafficShiftAlbDeployState(String name) {
    super(name, StateType.ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY.name());
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    AwsAmiServiceDeployResponse amiServiceDeployResponse =
        (AwsAmiServiceDeployResponse) response.values().iterator().next();

    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        (AwsAmiDeployStateExecutionData) context.getStateExecutionData();
    awsAmiDeployStateExecutionData.setDelegateMetaInfo(amiServiceDeployResponse.getDelegateMetaInfo());

    Activity activity = activityService.get(awsAmiDeployStateExecutionData.getActivityId(), context.getAppId());
    notNullCheck("Activity", activity);
    ManagerExecutionLogCallback executionLogCallback = getExecutionLogCallback(context);

    try {
      List<InstanceElement> instanceElements = handleAsyncInternal(amiServiceDeployResponse, context);

      List<InstanceStatusSummary> instanceStatusSummaries =
          instanceElements.stream()
              .map(instanceElement
                  -> anInstanceStatusSummary()
                         .withInstanceElement((InstanceElement) instanceElement.cloneMin())
                         .withStatus(ExecutionStatus.SUCCESS)
                         .build())
              .collect(toList());

      awsAmiDeployStateExecutionData.setNewInstanceStatusSummaries(instanceStatusSummaries);
      return taskCompletionSuccessResponse(
          activity, awsAmiDeployStateExecutionData, instanceElements, executionLogCallback);
    } catch (Exception ex) {
      return taskCompletionFailureResponse(activity, awsAmiDeployStateExecutionData, ex, executionLogCallback);
    }
  }

  private ManagerExecutionLogCallback getExecutionLogCallback(ExecutionContext context) {
    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        (AwsAmiDeployStateExecutionData) context.getStateExecutionData();

    Activity activity = activityService.get(awsAmiDeployStateExecutionData.getActivityId(), context.getAppId());

    Builder logBuilder = getLogBuilder(activity, getCommandName(), RUNNING);
    return new ManagerExecutionLogCallback(logService, logBuilder, activity.getUuid());
  }

  protected List<InstanceElement> handleAsyncInternal(
      AwsAmiServiceDeployResponse amiServiceDeployResponse, ExecutionContext context) {
    AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData = awsAmiServiceHelper.populateAlbTrafficShiftSetupData(context);
    AwsAmiInfrastructureMapping infrastructureMapping = awsAmiTrafficShiftAlbData.getInfrastructureMapping();

    List<InstanceElement> newInstances =
        getInstanceElementDetails(amiServiceDeployResponse.getInstancesAdded(), context, infrastructureMapping);
    List<InstanceElement> existingInstances =
        getInstanceElementDetails(amiServiceDeployResponse.getInstancesExisting(), context, infrastructureMapping);

    List<InstanceElement> allInstanceElements = new ArrayList<>();
    allInstanceElements.addAll(newInstances);
    allInstanceElements.addAll(existingInstances);

    // This sweeping element will be used by verification or other consumers.
    List<InstanceDetails> instanceDetails = awsStateHelper.generateAmInstanceDetails(allInstanceElements);
    boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                   .value(InstanceInfoVariables.builder()
                                              .instanceElements(allInstanceElements)
                                              .instanceDetails(instanceDetails)
                                              .skipVerification(skipVerification)
                                              .build())
                                   .build());

    return newInstances;
  }

  private List<InstanceElement> getInstanceElementDetails(
      List<Instance> instances, ExecutionContext context, AwsAmiInfrastructureMapping infrastructureMapping) {
    if (isEmpty(instances)) {
      return Collections.emptyList();
    }
    return awsStateHelper.generateInstanceElements(instances, infrastructureMapping, context);
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
    AmiServiceTrafficShiftAlbSetupElement serviceSetupElement =
        (AmiServiceTrafficShiftAlbSetupElement) awsAmiServiceHelper.getSetupElementFromSweepingOutput(
            context, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME);
    AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData = awsAmiServiceHelper.populateAlbTrafficShiftSetupData(context);
    Activity activity = crateActivity(context, awsAmiTrafficShiftAlbData);
    ManagerExecutionLogCallback executionLogCallback =
        new ManagerExecutionLogCallback(logService, getLogBuilder(activity), activity.getUuid());
    try {
      AwsAmiServiceTrafficShiftAlbDeployRequest request =
          createAwsAmiTrafficShiftDeployRequest(serviceSetupElement, awsAmiTrafficShiftAlbData, activity);
      createAndEnqueueDelegateTask(request, awsAmiTrafficShiftAlbData.getInfrastructureMapping().getEnvId(),
          awsAmiTrafficShiftAlbData.getEnv().getEnvironmentType().name(), context);
    } catch (Exception exception) {
      return taskCreationFailureResponse(exception, activity.getUuid(), executionLogCallback);
    }
    return taskCreationSuccessResponse(activity.getUuid(), serviceSetupElement, context);
  }

  protected Activity crateActivity(ExecutionContext context, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData) {
    Application app = awsAmiTrafficShiftAlbData.getApp();
    Service service = awsAmiTrafficShiftAlbData.getService();
    Environment env = awsAmiTrafficShiftAlbData.getEnv();
    Artifact artifact = awsAmiTrafficShiftAlbData.getArtifact();
    String serviceId = awsAmiTrafficShiftAlbData.getServiceId();

    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    Command command =
        serviceResourceService.getCommandByName(app.getUuid(), serviceId, env.getUuid(), getCommandName()).getCommand();
    List<CommandUnit> commandUnitList =
        serviceResourceService.getFlattenCommandUnitList(app.getUuid(), serviceId, env.getUuid(), getCommandName());

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .environmentId(env.getUuid())
                                          .environmentName(env.getName())
                                          .environmentType(env.getEnvironmentType())
                                          .serviceId(service.getUuid())
                                          .serviceName(service.getName())
                                          .commandName(getCommandName())
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
                                          .artifactStreamId(artifactStream.getUuid())
                                          .artifactStreamName(artifactStream.getSourceName())
                                          .artifactName(artifact.getDisplayName())
                                          .artifactId(artifact.getUuid())
                                          .artifactId(artifact.getUuid())
                                          .artifactName(artifact.getDisplayName())
                                          .appId(app.getUuid())
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(awsAmiTrafficShiftAlbData.getCurrentUser().getEmail())
                                                           .name(awsAmiTrafficShiftAlbData.getCurrentUser().getName())
                                                           .build());

    return activityService.save(activityBuilder.build());
  }

  protected void createAndEnqueueDelegateTask(
      AwsAmiServiceTrafficShiftAlbDeployRequest request, String envId, String envType, ExecutionContext context) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(request.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, request.getAppId())
            .waitId(request.getActivityId())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(AWS_AMI_ASYNC_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(request.getAutoScalingSteadyStateTimeout()))
                      .build())
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, envType)
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("AWS AMI service traffic shift ALB deploy task execution")
            .build();
    delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
  }

  protected AwsAmiDeployStateExecutionData prepareStateExecutionData(
      String activityId, AmiServiceTrafficShiftAlbSetupElement serviceSetupElement, ExecutionContext context) {
    List<ContainerServiceData> newInstanceData =
        singletonList(ContainerServiceData.builder()
                          .name(serviceSetupElement.getNewAutoScalingGroupName())
                          .desiredCount(serviceSetupElement.getDesiredInstances())
                          .previousCount(0)
                          .build());

    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        AwsAmiDeployStateExecutionData.builder().activityId(activityId).commandName(getCommandName()).build();
    awsAmiDeployStateExecutionData.setAutoScalingSteadyStateTimeout(
        serviceSetupElement.getAutoScalingSteadyStateTimeout());
    awsAmiDeployStateExecutionData.setNewAutoScalingGroupName(serviceSetupElement.getNewAutoScalingGroupName());
    awsAmiDeployStateExecutionData.setOldAutoScalingGroupName(serviceSetupElement.getOldAutoScalingGroupName());
    awsAmiDeployStateExecutionData.setMaxInstances(serviceSetupElement.getMaxInstances());
    awsAmiDeployStateExecutionData.setNewInstanceData(newInstanceData);
    awsAmiDeployStateExecutionData.setInstanceCount(spotinstStateHelper.renderCount(instanceCountExpr, context, 100));
    awsAmiDeployStateExecutionData.setInstanceUnitType(instanceUnitType);
    return awsAmiDeployStateExecutionData;
  }

  private AwsAmiServiceTrafficShiftAlbDeployRequest createAwsAmiTrafficShiftDeployRequest(
      AmiServiceTrafficShiftAlbSetupElement serviceSetupElement, AwsAmiTrafficShiftAlbData awsAmiTrafficShiftAlbData,
      Activity activity) {
    return AwsAmiServiceTrafficShiftAlbDeployRequest.builder()
        .awsConfig(awsAmiTrafficShiftAlbData.getAwsConfig())
        .encryptionDetails(awsAmiTrafficShiftAlbData.getAwsEncryptedDataDetails())
        .region(awsAmiTrafficShiftAlbData.getRegion())
        .accountId(awsAmiTrafficShiftAlbData.getApp().getAccountId())
        .appId(awsAmiTrafficShiftAlbData.getApp().getAppId())
        .activityId(activity.getUuid())
        .commandName(COMMAND_NAME)
        .rollback(false)
        .newAutoScalingGroupName(serviceSetupElement.getNewAutoScalingGroupName())
        .oldAutoScalingGroupName(serviceSetupElement.getOldAutoScalingGroupName())
        .autoScalingSteadyStateTimeout(serviceSetupElement.getAutoScalingSteadyStateTimeout())
        .minInstances(serviceSetupElement.getMinInstances())
        .maxInstances(serviceSetupElement.getMaxInstances())
        .desiredInstances(serviceSetupElement.getDesiredInstances())
        .preDeploymentData(serviceSetupElement.getPreDeploymentData())
        .baseScalingPolicyJSONs(serviceSetupElement.getBaseScalingPolicyJSONs())
        .infraMappingTargetGroupArns(serviceSetupElement.getDetailsWithTargetGroups()
                                         .stream()
                                         .map(LbDetailsForAlbTrafficShift::getStageTargetGroupArn)
                                         .collect(toList()))
        .amiInServiceHealthyStateFFEnabled(false)
        .baseAsgScheduledActionJSONs(featureFlagService.isEnabled(FeatureName.AMI_ASG_CONFIG_COPY,
                                         awsAmiTrafficShiftAlbData.getApp().getAccountId())
                ? serviceSetupElement.getBaseAsgScheduledActionJSONs()
                : null)
        .amiAsgConfigCopyEnabled(featureFlagService.isEnabled(
            FeatureName.AMI_ASG_CONFIG_COPY, awsAmiTrafficShiftAlbData.getApp().getAccountId()))
        .build();
  }

  private ExecutionResponse taskCompletionFailureResponse(Activity activity,
      AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData, Exception exception,
      ManagerExecutionLogCallback executionLogCallback) {
    log.error("Ami deploy step failed with error ", exception);
    String errorMessage = ExceptionUtils.getMessage(exception);

    activityService.updateStatus(activity.getUuid(), activity.getAppId(), FAILED);

    executionLogCallback.saveExecutionLog(
        format("AutoScaling Group resize operation completed with status:[%s]", FAILED), ERROR,
        CommandExecutionStatus.FAILURE);
    executionLogCallback.saveExecutionLog(errorMessage, ERROR);

    awsAmiDeployStateExecutionData.setStatus(FAILED);
    awsAmiDeployStateExecutionData.setErrorMsg(errorMessage);

    InstanceElementListParam instanceElementListParam =
        InstanceElementListParam.builder().instanceElements(Collections.emptyList()).build();

    return createResponse(
        activity.getUuid(), FAILED, errorMessage, awsAmiDeployStateExecutionData, instanceElementListParam, false);
  }

  private ExecutionResponse taskCompletionSuccessResponse(Activity activity,
      AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData, List<InstanceElement> instanceElements,
      ManagerExecutionLogCallback executionLogCallback) {
    activityService.updateStatus(activity.getUuid(), activity.getAppId(), ExecutionStatus.SUCCESS);

    InstanceElementListParam instanceElementListParam =
        InstanceElementListParam.builder().instanceElements(instanceElements).build();

    executionLogCallback.saveExecutionLog(
        format("AutoScaling Group resize operation completed with status:[%s]", ExecutionStatus.SUCCESS), INFO,
        CommandExecutionStatus.SUCCESS);

    return createResponse(
        activity.getUuid(), SUCCESS, null, awsAmiDeployStateExecutionData, instanceElementListParam, false);
  }

  private ExecutionResponse taskCreationSuccessResponse(
      String activityId, AmiServiceTrafficShiftAlbSetupElement serviceSetupElement, ExecutionContext context) {
    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        prepareStateExecutionData(activityId, serviceSetupElement, context);
    return createResponse(activityId, SUCCESS, null, awsAmiDeployStateExecutionData, null, true);
  }

  private ExecutionResponse taskCreationFailureResponse(
      Exception exception, String activityId, ManagerExecutionLogCallback executionLogCallback) {
    log.error("Ami deploy step failed with error ", exception);
    Misc.logAllMessages(exception, executionLogCallback, CommandExecutionStatus.FAILURE);
    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData = AwsAmiDeployStateExecutionData.builder().build();
    String errorMessage = getMessage(exception);
    return createResponse(activityId, ExecutionStatus.FAILED, errorMessage, awsAmiDeployStateExecutionData, null, true);
  }

  private ExecutionResponse createResponse(String activityId, ExecutionStatus status, String errorMessage,
      AwsAmiDeployStateExecutionData executionData, ContextElement contextElement, boolean isAsync) {
    ExecutionResponseBuilder responseBuilder = ExecutionResponse.builder();
    if (contextElement != null) {
      responseBuilder.contextElement(contextElement);
      responseBuilder.notifyElement(contextElement);
    }
    return responseBuilder.correlationIds(singletonList(activityId))
        .executionStatus(status)
        .errorMessage(errorMessage)
        .stateExecutionData(executionData)
        .async(isAsync)
        .build();
  }

  private Builder getLogBuilder(Activity activity) {
    return getLogBuilder(activity, null, null);
  }

  @NotNull
  private Builder getLogBuilder(Activity activity, String commandName, CommandExecutionStatus status) {
    Builder logBuilder =
        aLog()
            .appId(activity.getAppId())
            .activityId(activity.getUuid())
            .commandUnitName(commandName != null ? commandName : activity.getCommandUnits().get(0).getName())
            .logLevel(INFO);

    if (status != null) {
      logBuilder.executionResult(status);
    }
    return logBuilder;
  }

  public String getCommandName() {
    return COMMAND_NAME;
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
