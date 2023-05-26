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
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.api.ServiceTemplateElement.Builder.aServiceTemplateElement;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.TaskType.AWS_AMI_ASYNC_TASK;
import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.ContainerServiceData;
import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.InstanceElementListParam.InstanceElementListParamBuilder;
import software.wings.api.PhaseElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.dto.Log.Builder;
import software.wings.beans.infrastructure.Host;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.impl.aws.model.AwsAmiResizeData;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;

import com.amazonaws.services.ec2.model.Instance;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AwsAmiServiceDeployState extends State {
  public static final String ASG_COMMAND_NAME = "Resize AutoScaling Group";

  @Attributes(title = "Desired Instances (cumulative)") private String instanceCount;

  @Attributes(title = "Instance Unit Type (Count/Percent)")
  @EnumData(enumDataProvider = InstanceUnitTypeDataProvider.class)
  @DefaultValue("COUNT")
  private InstanceUnitType instanceUnitType = InstanceUnitType.COUNT;

  @Attributes(title = "Command") @DefaultValue(ASG_COMMAND_NAME) private String commandName = ASG_COMMAND_NAME;

  @Inject protected AwsHelperService awsHelperService;
  @Inject protected SettingsService settingsService;
  @Inject protected ServiceResourceService serviceResourceService;
  @Inject protected ServiceTemplateService serviceTemplateService;
  @Inject protected InfrastructureMappingService infrastructureMappingService;
  @Inject protected ArtifactStreamService artifactStreamService;
  @Inject protected SecretManager secretManager;
  @Inject protected EncryptionService encryptionService;
  @Inject protected ActivityService activityService;
  @Inject protected DelegateService delegateService;
  @Inject protected LogService logService;
  @Inject protected SweepingOutputService sweepingOutputService;
  @Inject private HostService hostService;
  @Inject private AwsUtils awsUtils;
  @Inject private AwsAsgHelperServiceManager awsAsgHelperServiceManager;
  @Inject private ServiceTemplateHelper serviceTemplateHelper;
  @Inject private AwsStateHelper awsStateHelper;
  @Inject private AwsAmiServiceStateHelper awsAmiServiceStateHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public AwsAmiServiceDeployState(String name) {
    this(name, StateType.AWS_AMI_SERVICE_DEPLOY.name());
  }

  public AwsAmiServiceDeployState(String name, String type) {
    super(name, type);
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

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    AmiServiceSetupElement serviceSetupElement =
        (AmiServiceSetupElement) awsAmiServiceStateHelper.getSetupElementFromSweepingOutput(
            context, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    return awsStateHelper.getAmiStateTimeout(serviceSetupElement);
  }

  protected Activity crateActivity(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    Application app = workflowStandardParamsExtensionService.getApp(workflowStandardParams);
    notNullCheck("Application", app);
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    notNullCheck("Environment", env);
    Service service = serviceResourceService.getWithDetails(app.getUuid(), serviceId);

    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    if (artifact == null) {
      throw new WingsException(format("Unable to find artifact for service %s", service.getName()));
    }
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
                                          .type(Type.Command)
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
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(workflowStandardParams.getCurrentUser().getEmail())
                                                           .name(workflowStandardParams.getCurrentUser().getName())
                                                           .build());

    Activity build = activityBuilder.build();
    build.setAppId(app.getUuid());
    return activityService.save(build);
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    AmiServiceSetupElement serviceSetupElement =
        (AmiServiceSetupElement) awsAmiServiceStateHelper.getSetupElementFromSweepingOutput(
            context, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    if (serviceSetupElement == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .errorMessage("No service setup element found. Skipping deploy.")
          .build();
    }

    Activity activity = crateActivity(context);

    boolean blueGreen = BLUE_GREEN == context.getOrchestrationWorkflowType();

    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData;
    AwsAmiInfrastructureMapping infrastructureMapping = (AwsAmiInfrastructureMapping) infrastructureMappingService.get(
        activity.getAppId(), context.fetchInfraMappingId());
    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());

    String region = infrastructureMapping.getRegion();
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context, awsConfig);

    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId());

    Integer totalExpectedCount;
    int instanceCountLocal = Integer.parseInt(context.renderExpression(getInstanceCount()));
    if (getInstanceUnitType() == PERCENTAGE) {
      int percent = Math.min(instanceCountLocal, 100);
      int instanceCount1 = (int) Math.round((percent * serviceSetupElement.getDesiredInstances()) / 100.0);
      totalExpectedCount = Math.max(instanceCount1, 1);
    } else {
      totalExpectedCount = instanceCountLocal;
    }

    List<String> oldAsgNames = serviceSetupElement.getOldAsgNames();
    List<String> gpNames = newArrayList();
    if (isNotEmpty(oldAsgNames) && !blueGreen) {
      gpNames.addAll(oldAsgNames);
    }
    String newAutoScalingGroupName = serviceSetupElement.getNewAutoScalingGroupName();
    if (isNotEmpty(newAutoScalingGroupName)) {
      gpNames.add(newAutoScalingGroupName);
    }

    Map<String, Integer> existingDesiredCapacities = awsAsgHelperServiceManager.getDesiredCapacitiesOfAsgs(
        awsConfig, encryptionDetails, region, gpNames, infrastructureMapping.getAppId());

    int newAutoScalingGroupDesiredCapacity = isNotEmpty(newAutoScalingGroupName)
        ? awsStateHelper.fetchRequiredAsgCapacity(existingDesiredCapacities, newAutoScalingGroupName)
        : 0;
    int totalNewInstancesToBeAdded = Math.max(0, totalExpectedCount - newAutoScalingGroupDesiredCapacity);
    Integer newAsgFinalDesiredCount = newAutoScalingGroupDesiredCapacity + totalNewInstancesToBeAdded;
    List<ContainerServiceData> newInstanceData = singletonList(ContainerServiceData.builder()
                                                                   .name(newAutoScalingGroupName)
                                                                   .desiredCount(newAsgFinalDesiredCount)
                                                                   .previousCount(newAutoScalingGroupDesiredCapacity)
                                                                   .build());
    // If the deployment is of B/G type, we will not downscale the old ASGs.
    // For canary Wfs we will downscale old ASGs
    List<AwsAmiResizeData> newDesiredCapacities = new ArrayList<>();
    List<ContainerServiceData> oldInstanceData = newArrayList();
    List<String> classicLbs;
    List<String> targetGroupArns;
    if (blueGreen) {
      classicLbs = infrastructureMapping.getStageClassicLoadBalancers();
      targetGroupArns = infrastructureMapping.getStageTargetGroupArns();
    } else {
      AwsAmiResizeData awsAmiResizeData =
          getNewDesiredCounts(totalNewInstancesToBeAdded, serviceSetupElement.getOldAutoScalingGroupName(),
              existingDesiredCapacities, isFinalDeployState(instanceCountLocal, serviceSetupElement));
      if (awsAmiResizeData != null) {
        newDesiredCapacities.add(awsAmiResizeData);
        String asgName = awsAmiResizeData.getAsgName();
        int newCount = awsAmiResizeData.getDesiredCount();
        Integer oldCount = existingDesiredCapacities.get(asgName);
        if (oldCount == null) {
          oldCount = 0;
        }
        oldInstanceData.add(
            ContainerServiceData.builder().name(asgName).desiredCount(newCount).previousCount(oldCount).build());
      }
      classicLbs = infrastructureMapping.getClassicLoadBalancers();
      targetGroupArns = infrastructureMapping.getTargetGroupArns();
    }

    awsAmiDeployStateExecutionData = prepareStateExecutionData(activity.getUuid(), serviceSetupElement,
        instanceCountLocal, getInstanceUnitType(), newInstanceData, oldInstanceData);
    boolean resizeNewFirst = serviceSetupElement.getResizeStrategy() == ResizeStrategy.RESIZE_NEW_FIRST;

    AmiResizeTaskRequestData amiResizeTaskRequestData =
        AmiResizeTaskRequestData.builder()
            .accountId(infrastructureMapping.getAccountId())
            .activityId(activity.getUuid())
            .appId(infrastructureMapping.getAppId())
            .envId(infrastructureMapping.getEnvId())
            .environmentType(context.fetchRequiredEnvironment().getEnvironmentType())
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .commandName(getCommandName())
            .resizeNewFirst(resizeNewFirst)
            .newAutoScalingGroupName(newAutoScalingGroupName)
            .newAsgFinalDesiredCount(newAsgFinalDesiredCount)
            .resizeData(newDesiredCapacities)
            .serviceSetupElement(serviceSetupElement)
            .classicLBs(classicLbs)
            .targetGroupArns(targetGroupArns)
            .rollback(false)
            .context(context)
            .build();

    createAndQueueResizeTask(amiResizeTaskRequestData, context);

    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(awsAmiDeployStateExecutionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .correlationId(activity.getUuid())
        .build();
  }

  protected void createAndQueueResizeTask(AmiResizeTaskRequestData amiResizeTaskRequestData, ExecutionContext context) {
    String accountId = amiResizeTaskRequestData.getAccountId();
    String appId = amiResizeTaskRequestData.getAppId();
    String envId = amiResizeTaskRequestData.getEnvId();
    String activityId = amiResizeTaskRequestData.getActivityId();

    AmiServiceSetupElement serviceSetupElement = amiResizeTaskRequestData.getServiceSetupElement();
    AwsAmiServiceDeployRequest request =
        AwsAmiServiceDeployRequest.builder()
            .awsConfig(amiResizeTaskRequestData.getAwsConfig())
            .encryptionDetails(amiResizeTaskRequestData.getEncryptionDetails())
            .region(amiResizeTaskRequestData.getRegion())
            .accountId(amiResizeTaskRequestData.getAccountId())
            .appId(amiResizeTaskRequestData.getAppId())
            .activityId(amiResizeTaskRequestData.getActivityId())
            .commandName(commandName)
            .resizeNewFirst(amiResizeTaskRequestData.isResizeNewFirst())
            .newAutoScalingGroupName(amiResizeTaskRequestData.getNewAutoScalingGroupName())
            .newAsgFinalDesiredCount(amiResizeTaskRequestData.getNewAsgFinalDesiredCount())
            .oldAutoScalingGroupName(serviceSetupElement.getOldAutoScalingGroupName())
            .autoScalingSteadyStateTimeout(serviceSetupElement.getAutoScalingSteadyStateTimeout())
            .minInstances(serviceSetupElement.getMinInstances())
            .maxInstances(serviceSetupElement.getMaxInstances())
            .desiredInstances(serviceSetupElement.getDesiredInstances())
            .preDeploymentData(serviceSetupElement.getPreDeploymentData())
            .rollback(amiResizeTaskRequestData.isRollback())
            .baseScalingPolicyJSONs(serviceSetupElement.getBaseScalingPolicyJSONs())
            .asgDesiredCounts(amiResizeTaskRequestData.getResizeData())
            .infraMappingClassisLbs(amiResizeTaskRequestData.getClassicLBs())
            .infraMappingTargetGroupArns(amiResizeTaskRequestData.getTargetGroupArns())
            .amiInServiceHealthyStateFFEnabled(
                featureFlagService.isEnabled(FeatureName.AMI_IN_SERVICE_HEALTHY_WAIT, accountId))
            .baseAsgScheduledActionJSONs(
                featureFlagService.isEnabled(FeatureName.AMI_ASG_CONFIG_COPY, context.getAccountId())
                    ? serviceSetupElement.getBaseAsgScheduledActionJSONs()
                    : null)
            .amiAsgConfigCopyEnabled(
                featureFlagService.isEnabled(FeatureName.AMI_ASG_CONFIG_COPY, context.getAccountId()))
            .build();

    addExistingInstanceIds(amiResizeTaskRequestData, request);

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
            .waitId(activityId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(AWS_AMI_ASYNC_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(serviceSetupElement.getAutoScalingSteadyStateTimeout()))
                      .build())
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, amiResizeTaskRequestData.getEnvironmentType().name())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("AWS AMI service deploy task execution")
            .build();
    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
  }

  @VisibleForTesting
  void addExistingInstanceIds(AmiResizeTaskRequestData amiResizeTaskRequestData, AwsAmiServiceDeployRequest request) {
    ExecutionContext context = amiResizeTaskRequestData.getContext();
    List<SweepingOutput> sweepingOutputs = sweepingOutputService.findSweepingOutputsWithNamePrefix(
        context.prepareSweepingOutputInquiryBuilder().name(InstanceInfoVariables.SWEEPING_OUTPUT_NAME).build(),
        SweepingOutputInstance.Scope.WORKFLOW);

    if (isNotEmpty(sweepingOutputs)) {
      List<InstanceInfoVariables> instanceInfoVariableDeployed = sweepingOutputs.stream()
                                                                     .map(InstanceInfoVariables.class ::cast)
                                                                     .filter(InstanceInfoVariables::isDeployStateInfo)
                                                                     .collect(toList());

      List<InstanceDetails> listInstanceDetails =
          instanceInfoVariableDeployed.stream()
              .flatMap(instanceInfoVars -> instanceInfoVars.getInstanceDetails().stream())
              .collect(toList());

      if (isNotEmpty(listInstanceDetails)) {
        Set<String> instanceIds = listInstanceDetails.stream()
                                      .map(instanceDetail -> instanceDetail.getAws().getInstanceId())
                                      .collect(toSet());
        request.setExistingInstanceIds(new ArrayList<>(instanceIds));
      }
    }
  }

  @VisibleForTesting
  AwsAmiResizeData getNewDesiredCounts(int instancesToBeAdded, String oldAsgName,
      Map<String, Integer> existingDesiredCapacities, boolean isFinalDeployState) {
    Integer desiredCountForOldAsg = 0;
    if (isNotBlank(oldAsgName)) {
      desiredCountForOldAsg = existingDesiredCapacities.get(oldAsgName);
      if (desiredCountForOldAsg == null) {
        desiredCountForOldAsg = 0;
      }
      if (desiredCountForOldAsg <= instancesToBeAdded || isFinalDeployState) {
        desiredCountForOldAsg = 0;
      } else {
        desiredCountForOldAsg -= instancesToBeAdded;
      }
      return AwsAmiResizeData.builder().asgName(oldAsgName).desiredCount(desiredCountForOldAsg).build();
    }

    return null;
  }

  @VisibleForTesting
  boolean isFinalDeployState(int instanceCountLocal, AmiServiceSetupElement element) {
    if (PERCENTAGE == getInstanceUnitType()) {
      return instanceCountLocal >= 100;
    } else {
      return instanceCountLocal >= element.getDesiredInstances();
    }
  }

  protected AwsAmiDeployStateExecutionData prepareStateExecutionData(String activityId,
      AmiServiceSetupElement serviceSetupElement, int instanceCount, InstanceUnitType instanceUnitType,
      List<ContainerServiceData> newInstanceData, List<ContainerServiceData> oldInstanceData) {
    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        AwsAmiDeployStateExecutionData.builder().activityId(activityId).commandName(getCommandName()).build();
    awsAmiDeployStateExecutionData.setAutoScalingSteadyStateTimeout(
        serviceSetupElement.getAutoScalingSteadyStateTimeout());
    awsAmiDeployStateExecutionData.setNewAutoScalingGroupName(serviceSetupElement.getNewAutoScalingGroupName());
    awsAmiDeployStateExecutionData.setOldAutoScalingGroupName(serviceSetupElement.getOldAutoScalingGroupName());
    awsAmiDeployStateExecutionData.setMaxInstances(serviceSetupElement.getMaxInstances());
    awsAmiDeployStateExecutionData.setResizeStrategy(serviceSetupElement.getResizeStrategy());

    awsAmiDeployStateExecutionData.setInstanceCount(instanceCount);
    awsAmiDeployStateExecutionData.setInstanceUnitType(instanceUnitType);

    awsAmiDeployStateExecutionData.setNewInstanceData(newInstanceData);
    awsAmiDeployStateExecutionData.setOldInstanceData(oldInstanceData);

    return awsAmiDeployStateExecutionData;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        (AwsAmiDeployStateExecutionData) context.getStateExecutionData();

    String appId = context.getAppId();

    AwsAmiServiceDeployResponse amiServiceDeployResponse =
        (AwsAmiServiceDeployResponse) response.values().iterator().next();

    awsAmiDeployStateExecutionData.setDelegateMetaInfo(amiServiceDeployResponse.getDelegateMetaInfo());
    Activity activity = activityService.get(awsAmiDeployStateExecutionData.getActivityId(), appId);
    notNullCheck("Activity", activity);

    AmiServiceSetupElement serviceSetupElement =
        (AmiServiceSetupElement) awsAmiServiceStateHelper.getSetupElementFromSweepingOutput(
            context, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);

    Builder logBuilder = aLog()
                             .appId(activity.getAppId())
                             .activityId(activity.getUuid())
                             .logLevel(LogLevel.INFO)
                             .commandUnitName(getCommandName())
                             .executionResult(CommandExecutionStatus.RUNNING);

    ManagerExecutionLogCallback executionLogCallback =
        new ManagerExecutionLogCallback(logService, logBuilder, activity.getUuid());

    InstanceElementListParamBuilder instanceElementListParamBuilder =
        InstanceElementListParam.builder().instanceElements(Collections.emptyList());
    ExecutionStatus executionStatus = amiServiceDeployResponse.getExecutionStatus();
    String errorMessage = null;
    try {
      List<InstanceElement> instanceElements =
          handleAsyncInternal(amiServiceDeployResponse, context, serviceSetupElement, executionLogCallback);

      List<InstanceStatusSummary> instanceStatusSummaries =
          instanceElements.stream()
              .map(instanceElement
                  -> anInstanceStatusSummary()
                         .withInstanceElement((InstanceElement) instanceElement.cloneMin())
                         .withStatus(ExecutionStatus.SUCCESS)
                         .build())
              .collect(toList());

      awsAmiDeployStateExecutionData.setNewInstanceStatusSummaries(instanceStatusSummaries);
      instanceElementListParamBuilder.instanceElements(instanceElements);
    } catch (Exception ex) {
      log.error("Ami deploy step failed with error ", ex);
      executionStatus = FAILED;
      errorMessage = ExceptionUtils.getMessage(ex);
      awsAmiDeployStateExecutionData.setStatus(FAILED);
      awsAmiDeployStateExecutionData.setErrorMsg(errorMessage);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
    }

    activityService.updateStatus(activity.getUuid(), activity.getAppId(), executionStatus);

    executionLogCallback.saveExecutionLog(
        format("AutoScaling Group resize operation completed with status:[%s]", executionStatus),
        ExecutionStatus.SUCCESS == executionStatus ? LogLevel.INFO : LogLevel.ERROR,
        ExecutionStatus.SUCCESS == executionStatus ? CommandExecutionStatus.SUCCESS : CommandExecutionStatus.FAILURE);

    final InstanceElementListParam instanceElementListParam = instanceElementListParamBuilder.build();

    return ExecutionResponse.builder()
        .stateExecutionData(awsAmiDeployStateExecutionData)
        .executionStatus(executionStatus)
        .errorMessage(errorMessage)
        .contextElement(instanceElementListParam)
        .notifyElement(instanceElementListParam)
        .build();
  }

  protected List<InstanceElement> handleAsyncInternal(AwsAmiServiceDeployResponse amiServiceDeployResponse,
      ExecutionContext context, AmiServiceSetupElement serviceSetupElement,
      ManagerExecutionLogCallback executionLogCallback) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
        (AwsAmiDeployStateExecutionData) context.getStateExecutionData();

    Application app = workflowStandardParamsExtensionService.getApp(workflowStandardParams);
    notNullCheck("Application", app);

    Activity activity = activityService.get(awsAmiDeployStateExecutionData.getActivityId(), app.getUuid());
    notNullCheck("Activity", activity);

    AwsAmiInfrastructureMapping infrastructureMapping = (AwsAmiInfrastructureMapping) infrastructureMappingService.get(
        activity.getAppId(), context.fetchInfraMappingId());

    String serviceId = phaseElement.getServiceElement().getUuid();
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    notNullCheck("Environment", env);
    Service service = serviceResourceService.getWithDetails(app.getUuid(), serviceId);
    Key<ServiceTemplate> serviceTemplateKey =
        serviceTemplateService.getTemplateRefKeysByService(app.getUuid(), serviceId, env.getUuid()).get(0);

    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    if (artifact == null) {
      throw new WingsException(format("Unable to find artifact for service %s", service.getName()));
    }

    List<Instance> ec2InstancesAdded = amiServiceDeployResponse.getInstancesAdded();
    List<InstanceElement> instanceElements = new ArrayList<>();
    if (isNotEmpty(ec2InstancesAdded)) {
      String serviceTemplateId = serviceTemplateHelper.fetchServiceTemplateId(infrastructureMapping);

      instanceElements.addAll(generateInstanceElements(amiServiceDeployResponse.getInstancesAdded(), context,
          phaseElement, infrastructureMapping, serviceTemplateKey, serviceTemplateId, true));
    }

    List<InstanceElement> allInstanceElements = new ArrayList<>();
    allInstanceElements.addAll(instanceElements);
    if (isNotEmpty(amiServiceDeployResponse.getInstancesExisting())) {
      String serviceTemplateId = serviceTemplateHelper.fetchServiceTemplateId(infrastructureMapping);
      allInstanceElements.addAll(generateInstanceElements(amiServiceDeployResponse.getInstancesExisting(), context,
          phaseElement, infrastructureMapping, serviceTemplateKey, serviceTemplateId, false));
    }

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

    return instanceElements;
  }

  private List<InstanceElement> generateInstanceElements(List<Instance> instances, ExecutionContext context,
      PhaseElement phaseElement, AwsAmiInfrastructureMapping infrastructureMapping,
      Key<ServiceTemplate> serviceTemplateKey, String serviceTemplateId, boolean isUpsize) {
    if (isEmpty(instances)) {
      return emptyList();
    }

    return instances.stream()
        .map(instance -> {
          Host host = aHost()
                          .withHostName(awsUtils.getHostnameFromPrivateDnsName(instance.getPrivateDnsName()))
                          .withPublicDns(instance.getPublicDnsName())
                          .withEc2Instance(instance)
                          .withAppId(infrastructureMapping.getAppId())
                          .withEnvId(infrastructureMapping.getEnvId())
                          .withHostConnAttr(infrastructureMapping.getHostConnectionAttrs())
                          .withInfraMappingId(infrastructureMapping.getUuid())
                          .withInfraDefinitionId(infrastructureMapping.getInfrastructureDefinitionId())
                          .withServiceTemplateId(serviceTemplateId)
                          .build();
          Host savedHost = hostService.saveHost(host);
          HostElement hostElement = HostElement.builder()
                                        .uuid(savedHost.getUuid())
                                        .publicDns(instance.getPublicDnsName())
                                        .ip(instance.getPrivateIpAddress())
                                        .ec2Instance(instance)
                                        .instanceId(instance.getInstanceId())
                                        .build();

          final Map<String, Object> contextMap = context.asMap();
          contextMap.put("host", hostElement);
          String hostName = awsHelperService.getHostnameFromConvention(contextMap, "");
          hostElement.setHostName(hostName);
          return anInstanceElement()
              .uuid(instance.getInstanceId())
              .hostName(hostName)
              .displayName(instance.getPublicDnsName())
              .host(hostElement)
              .serviceTemplateElement(aServiceTemplateElement()
                                          .withUuid(serviceTemplateKey.getId().toString())
                                          .withServiceElement(phaseElement.getServiceElement())
                                          .build())
              .newInstance(isUpsize)
              .build();
        })
        .collect(toList());
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (!isRollback() && isEmpty(getInstanceCount())) {
      invalidFields.put("instanceCount", "Instance count must be greater than 0");
    }
    if (getCommandName() == null) {
      invalidFields.put("commandName", "Command name must not be null");
    }
    return invalidFields;
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  public String getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(String instanceCount) {
    this.instanceCount = instanceCount;
  }

  public InstanceUnitType getInstanceUnitType() {
    return instanceUnitType;
  }

  public void setInstanceUnitType(InstanceUnitType instanceUnitType) {
    this.instanceUnitType = instanceUnitType;
  }

  public String getCommandName() {
    return commandName;
  }

  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
