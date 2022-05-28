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
import static io.harness.beans.FeatureName.ECS_BG_DOWNSIZE;
import static io.harness.beans.FeatureName.ECS_REGISTER_TASK_DEFINITION_TAGS;
import static io.harness.beans.FeatureName.ENABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.deployment.InstanceDetails.InstanceType.AWS;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.ECS_COMMAND_TASK;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_STATE_TIMEOUT_BUFFER_MIN;
import static software.wings.service.impl.aws.model.AwsConstants.ECS_ALL_PHASE_ROLLBACK_DONE;
import static software.wings.service.impl.aws.model.AwsConstants.ECS_SERVICE_DEPLOY_SWEEPING_OUTPUT_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.sm.states.ContainerServiceSetup.DEFAULT_MAX;
import static software.wings.sm.states.EcsRunTaskDeploy.ECS_RUN_TASK_COMMAND;
import static software.wings.sm.states.EcsRunTaskDeploy.GIT_FETCH_FILES_TASK_NAME;
import static software.wings.sm.states.EcsServiceDeploy.ECS_SERVICE_DEPLOY;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.container.ContainerInfo;
import io.harness.context.ContextElementType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerRollbackRequestElement;
import software.wings.api.ContainerServiceData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContainerServiceElement.ContainerServiceElementBuilder;
import software.wings.api.DeploymentType;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ecs.EcsBGRoute53SetupStateExecutionData;
import software.wings.api.ecs.EcsBGSetupData;
import software.wings.api.ecs.EcsListenerUpdateStateExecutionData;
import software.wings.api.ecs.EcsSetupStateExecutionData;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsElbConfig;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Log;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.ContainerTaskMapper;
import software.wings.beans.container.EcsContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.EcsServiceSpecificationMapper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsBGListenerUpdateRequest;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsDeployRollbackDataFetchRequest;
import software.wings.helpers.ext.ecs.request.EcsListenerUpdateRequestConfigData;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsDeployRollbackDataFetchResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.aws.model.AwsEcsAllPhaseRollbackData;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingValue;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.ContainerServiceSetup.ContainerServiceSetupKeys;
import software.wings.sm.states.EcsSetupContextVariableHolder.EcsSetupContextVariableHolderBuilder;
import software.wings.utils.EcsConvention;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Slf4j
@Singleton
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EcsStateHelper {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private StateExecutionService stateExecutionService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public ContainerSetupParams buildContainerSetupParams(
      ExecutionContext context, EcsSetupStateConfig ecsSetupStateConfig) {
    Application app = ecsSetupStateConfig.getApp();
    Environment env = ecsSetupStateConfig.getEnv();
    ContainerTask containerTask = ecsSetupStateConfig.getContainerTask();

    String taskFamily = isNotBlank(ecsSetupStateConfig.getEcsServiceName())
        ? Misc.normalizeExpression(context.renderExpression(ecsSetupStateConfig.getEcsServiceName()))
        : EcsConvention.getTaskFamily(app.getName(), ecsSetupStateConfig.getServiceName(), env.getName());

    List<AwsElbConfig> renderedAwsElbConfigList = null;
    if (EmptyPredicate.isNotEmpty(ecsSetupStateConfig.getAwsElbConfigs())) {
      renderedAwsElbConfigList =
          ecsSetupStateConfig.getAwsElbConfigs()
              .stream()
              .map(awsElbConfig -> {
                AwsElbConfig renderedAwsElbConfig = new AwsElbConfig();
                renderedAwsElbConfig.setTargetContainerName(
                    context.renderExpression(awsElbConfig.getTargetContainerName()));
                renderedAwsElbConfig.setLoadBalancerName(context.renderExpression(awsElbConfig.getLoadBalancerName()));
                renderedAwsElbConfig.setTargetGroupArn(context.renderExpression(awsElbConfig.getTargetGroupArn()));
                renderedAwsElbConfig.setTargetPort(context.renderExpression(awsElbConfig.getTargetPort()));
                return renderedAwsElbConfig;
              })
              .collect(toList());
    }

    if (containerTask != null) {
      EcsContainerTask ecsContainerTask = (EcsContainerTask) containerTask;
      ecsContainerTask.getContainerDefinitions()
          .stream()
          .filter(containerDefinition -> isNotEmpty(containerDefinition.getCommands()))
          .forEach(containerDefinition
              -> containerDefinition.setCommands(
                  containerDefinition.getCommands().stream().map(context::renderExpression).collect(toList())));
      if (ecsContainerTask.getAdvancedConfig() != null) {
        ecsContainerTask.setAdvancedConfig(context.renderExpression(ecsContainerTask.getAdvancedConfig()));
      }
    }

    int serviceSteadyStateTimeout = ecsSetupStateConfig.getServiceSteadyStateTimeout();

    EcsInfrastructureMapping ecsInfrastructureMapping =
        (EcsInfrastructureMapping) ecsSetupStateConfig.getInfrastructureMapping();

    return EcsSetupParamsBuilder.anEcsSetupParams()
        .withAppName(app.getName())
        .withEnvName(env.getName())
        .withServiceName(ecsSetupStateConfig.getServiceName())
        .withClusterName(ecsSetupStateConfig.getClusterName())
        .withImageDetails(ecsSetupStateConfig.getImageDetails())
        .withContainerTask(ContainerTaskMapper.toEcsContainerTaskDTO((EcsContainerTask) containerTask))
        .withLoadBalancerName(context.renderExpression(ecsSetupStateConfig.getLoadBalancerName()))
        .withInfraMappingId(ecsSetupStateConfig.getInfrastructureMapping().getUuid())
        .withRoleArn(context.renderExpression(ecsSetupStateConfig.getRoleArn()))
        .withAwsElbConfigs(renderedAwsElbConfigList)
        .withIsMultipleLoadBalancersFeatureFlagActive(ecsSetupStateConfig.isMultipleLoadBalancersFeatureFlagActive())
        .withTargetGroupArn(context.renderExpression(ecsSetupStateConfig.getTargetGroupArn()))
        // Next 3 are to be used by Ecs BG only
        .withBlueGreen(ecsSetupStateConfig.isBlueGreen())
        .withTargetGroupArn2(context.renderExpression(ecsSetupStateConfig.getTargetGroupArn2()))
        .withProdListenerArn(context.renderExpression(ecsSetupStateConfig.getProdListenerArn()))
        .withStageListenerArn(context.renderExpression(ecsSetupStateConfig.getStageListenerArn()))
        .withStageListenerPort(context.renderExpression(ecsSetupStateConfig.getStageListenerPort()))
        .withUseSpecificListenerRuleArn(ecsSetupStateConfig.isUseSpecificListenerRuleArn())
        .withProdListenerRuleArn(context.renderExpression(ecsSetupStateConfig.getProdListenerRuleArn()))
        .withStageListenerRuleArn(context.renderExpression(ecsSetupStateConfig.getStageListenerRuleArn()))
        .withUseDNSRoute53Swap(ecsSetupStateConfig.isUseRoute53DNSSwap())
        .withServiceDiscoveryService1JSON(
            context.renderExpression(ecsSetupStateConfig.getServiceDiscoveryService1JSON()))
        .withServiceDiscoveryService2JSON(
            context.renderExpression(ecsSetupStateConfig.getServiceDiscoveryService2JSON()))
        .withParentRecordHostedZoneId(context.renderExpression(ecsSetupStateConfig.getParentRecordHostedZoneId()))
        .withParentRecordName(context.renderExpression(ecsSetupStateConfig.getParentRecordName()))
        .withTaskFamily(taskFamily)
        .withUseLoadBalancer(ecsSetupStateConfig.isUseLoadBalancer())
        .withRegion(ecsInfrastructureMapping.getRegion())
        .withVpcId(ecsInfrastructureMapping.getVpcId())
        .withSubnetIds(getArrayFromList(ecsInfrastructureMapping.getSubnetIds()))
        .withSecurityGroupIds(getArrayFromList(ecsInfrastructureMapping.getSecurityGroupIds()))
        .withAssignPublicIps(ecsInfrastructureMapping.isAssignPublicIp())
        .withExecutionRoleArn(ecsInfrastructureMapping.getExecutionRole())
        .withLaunchType(ecsInfrastructureMapping.getLaunchType())
        .withTargetContainerName(context.renderExpression(ecsSetupStateConfig.getTargetContainerName()))
        .withTargetPort(context.renderExpression(ecsSetupStateConfig.getTargetPort()))
        .withServiceSteadyStateTimeout(serviceSteadyStateTimeout)
        .withRollback(ecsSetupStateConfig.isRollback())
        .withPreviousEcsServiceSnapshotJson(ecsSetupStateConfig.getPreviousEcsServiceSnapshotJson())
        .withEcsServiceSpecification(EcsServiceSpecificationMapper.toEcsServiceSpecificationDTO(
            getServiceSpecWithRenderedExpression(ecsSetupStateConfig.getEcsServiceSpecification(), context)))
        .withEcsServiceArn(ecsSetupStateConfig.getEcsServiceArn())
        .withIsDaemonSchedulingStrategy(ecsSetupStateConfig.isDaemonSchedulingStrategy())
        .withNewAwsAutoScalarConfigList(
            getNewAwsAutoScalarConfigListWithRenderedExpression(ecsSetupStateConfig.getAwsAutoScalarConfigs(), context))
        .withEcsRegisterTaskDefinitionTagsEnabled(
            featureFlagService.isEnabled(ECS_REGISTER_TASK_DEFINITION_TAGS, app.getAccountId()))
        .build();
  }

  private EcsServiceSpecification getServiceSpecWithRenderedExpression(
      EcsServiceSpecification ecsServiceSpecification, ExecutionContext context) {
    if (ecsServiceSpecification == null || StringUtils.isBlank(ecsServiceSpecification.getServiceSpecJson())) {
      return ecsServiceSpecification;
    }

    ecsServiceSpecification.setServiceSpecJson(context.renderExpression(ecsServiceSpecification.getServiceSpecJson()));
    return ecsServiceSpecification;
  }

  private List<AwsAutoScalarConfig> getNewAwsAutoScalarConfigListWithRenderedExpression(
      List<AwsAutoScalarConfig> awsAutoScalarConfigs, ExecutionContext context) {
    if (isEmpty(awsAutoScalarConfigs)) {
      return awsAutoScalarConfigs;
    }

    awsAutoScalarConfigs.forEach(awsAutoScalarConfig -> {
      if (isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        awsAutoScalarConfig.setScalableTargetJson(
            context.renderExpression(awsAutoScalarConfig.getScalableTargetJson()));
      }

      if (isNotBlank(awsAutoScalarConfig.getScalingPolicyForTarget())) {
        awsAutoScalarConfig.setScalingPolicyForTarget(
            context.renderExpression(awsAutoScalarConfig.getScalingPolicyForTarget()));
        // Field " String ScalingPolicyForTarget", is only used when UI sends policyJson.
        // actual field used in all delegate tasks is "String[] ScalingPolicyJson". So we initialize this field using
        // vaoue set by UI.
        awsAutoScalarConfig.setScalingPolicyJson(new String[] {awsAutoScalarConfig.getScalingPolicyForTarget()});
      }
    });

    return awsAutoScalarConfigs;
  }

  private String[] getArrayFromList(List<String> input) {
    if (CollectionUtils.isEmpty(input)) {
      return new String[0];
    } else {
      return input.toArray(new String[0]);
    }
  }

  public ManagerExecutionLogCallback getExecutionLogCallback(
      ExecutionContext context, String activityId, String commandUnitName, LogService logService) {
    Log.Builder logBuilder = Log.Builder.aLog()
                                 .appId(context.getAppId())
                                 .activityId(activityId)
                                 .commandUnitName(commandUnitName)
                                 .logLevel(INFO)
                                 .executionResult(CommandExecutionStatus.RUNNING);
    return new ManagerExecutionLogCallback(logService, logBuilder, activityId);
  }

  public ActivityBuilder getActivityBuilder(String appName, String appId, String commandName, Type type,
      ExecutionContext executionContext, String commandType, CommandUnitType commandUnitType, Environment environment) {
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    return Activity.builder()
        .applicationName(appName)
        .appId(appId)
        .commandName(commandName)
        .type(type)
        .workflowType(executionContext.getWorkflowType())
        .workflowExecutionName(executionContext.getWorkflowExecutionName())
        .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
        .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
        .commandType(commandType)
        .workflowExecutionId(executionContext.getWorkflowExecutionId())
        .workflowId(executionContext.getWorkflowId())
        .commandUnits(Collections.emptyList())
        .status(ExecutionStatus.RUNNING)
        .commandUnitType(commandUnitType)
        .environmentId(environment.getUuid())
        .environmentName(environment.getName())
        .environmentType(environment.getEnvironmentType())
        .triggeredBy(TriggeredBy.builder()
                         .email(workflowStandardParams.getCurrentUser().getEmail())
                         .name(workflowStandardParams.getCurrentUser().getName())
                         .build());
  }

  public ExecutionResponse queueDelegateTaskForEcsListenerUpdate(Application app, AwsConfig awsConfig,
      DelegateService delegateService, EcsInfrastructureMapping ecsInfrastructureMapping, String activityId,
      Environment environment, String commandName, EcsListenerUpdateRequestConfigData requestConfigData,
      List<EncryptedDataDetail> encryptedDataDetails, int serviceSteadyStateTimeout,
      boolean selectionLogsTrackingForTasksEnabled, String stateExecutionInstanceId) {
    EcsCommandRequest ecsCommandRequest = getEcsCommandListenerUpdateRequest(commandName, app.getUuid(),
        app.getAccountId(), activityId, awsConfig, requestConfigData, serviceSteadyStateTimeout);

    EcsListenerUpdateStateExecutionData stateExecutionData = getListenerUpdateStateExecutionData(
        activityId, app.getAccountId(), app.getUuid(), ecsCommandRequest, commandName, requestConfigData);

    DelegateTask delegateTask = getDelegateTask(app.getAccountId(), app.getUuid(), TaskType.ECS_COMMAND_TASK,
        activityId, environment, ecsInfrastructureMapping, new Object[] {ecsCommandRequest, encryptedDataDetails},
        serviceSteadyStateTimeout);
    delegateTask.setSelectionLogsTrackingEnabled(selectionLogsTrackingForTasksEnabled);
    delegateTask.setDescription("ECS Listener Update task execution");
    delegateTask.setTags(isNotEmpty(awsConfig.getTag()) ? singletonList(awsConfig.getTag()) : null);

    delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(delegateTask, stateExecutionInstanceId);

    return ExecutionResponse.builder()
        .correlationIds(Arrays.asList(activityId))
        .stateExecutionData(stateExecutionData)
        .async(true)
        .build();
  }

  private void appendDelegateTaskDetails(DelegateTask delegateTask, String stateExecutionInstanceId) {
    if (isBlank(delegateTask.getUuid())) {
      delegateTask.setUuid(generateUuid());
    }

    stateExecutionService.appendDelegateTaskDetails(stateExecutionInstanceId,
        DelegateTaskDetails.builder()
            .delegateTaskId(delegateTask.getUuid())
            .taskDescription(delegateTask.calcDescription())
            .setupAbstractions(delegateTask.getSetupAbstractions())
            .build());
  }

  public Activity createActivity(ExecutionContext executionContext, String commandName, String stateType,
      CommandUnitType commandUnitType, String addTaskDefinition, ActivityService activityService) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();

    List<CommandUnit> commandUnitList = createCommandUnitList("Remote".equalsIgnoreCase(addTaskDefinition));
    ActivityBuilder activityBuilder = getActivityBuilder(
        app.getName(), app.getUuid(), commandName, Type.Command, executionContext, stateType, commandUnitType, env);
    return activityService.save(activityBuilder.commandUnits(commandUnitList).build());
  }

  public Activity createActivity(ExecutionContext executionContext, String commandName, String stateType,
      CommandUnitType commandUnitType, ActivityService activityService) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();

    ActivityBuilder activityBuilder = getActivityBuilder(
        app.getName(), app.getUuid(), commandName, Type.Command, executionContext, stateType, commandUnitType, env);
    return activityService.save(activityBuilder.build());
  }

  List<CommandUnit> createCommandUnitList(boolean remoteStoreType) {
    final ImmutableList.Builder<CommandUnit> commandUnitBuilder = ImmutableList.builder();

    if (remoteStoreType) {
      commandUnitBuilder.add(new PcfDummyCommandUnit(GIT_FETCH_FILES_TASK_NAME));
    }
    commandUnitBuilder.add(new PcfDummyCommandUnit(ECS_RUN_TASK_COMMAND));
    return commandUnitBuilder.build();
  }

  public EcsCommandRequest getEcsCommandListenerUpdateRequest(String commandName, String appId, String accountId,
      String activityId, AwsConfig awsConfig, EcsListenerUpdateRequestConfigData requestConfigData,
      int serviceSteadyStateTimeout) {
    return EcsBGListenerUpdateRequest.builder()
        .commandName(commandName)
        .appId(appId)
        .accountId(accountId)
        .activityId(activityId)
        .clusterName(requestConfigData.getClusterName())
        .prodListenerArn(requestConfigData.getProdListenerArn())
        .stageListenerArn(requestConfigData.getStageListenerArn())
        .isUseSpecificListenerRuleArn(requestConfigData.isUseSpecificRuleArn())
        .prodListenerRuleArn(requestConfigData.getProdListenerRuleArn())
        .stageListenerRuleArn(requestConfigData.getStageListenerRuleArn())
        .region(requestConfigData.getRegion())
        .serviceName(requestConfigData.getServiceName())
        .awsConfig(awsConfig)
        .downsizeOldService(requestConfigData.isDownsizeOldService())
        .downsizeOldServiceDelayInSecs(requestConfigData.getDownsizeOldServiceDelayInSecs())
        .ecsBgDownsizeDelayEnabled(featureFlagService.isEnabled(ECS_BG_DOWNSIZE, accountId))
        .serviceNameDownsized(requestConfigData.getServiceNameDownsized())
        .serviceCountDownsized(requestConfigData.getServiceCountDownsized())
        .rollback(requestConfigData.isRollback())
        .targetGroupForNewService(requestConfigData.getTargetGroupForNewService())
        .targetGroupForExistingService(requestConfigData.getTargetGroupForExistingService())
        .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
        .timeoutErrorSupported(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, accountId))
        .build();
  }

  public EcsListenerUpdateStateExecutionData getListenerUpdateStateExecutionData(String activityId, String appId,
      String accountId, EcsCommandRequest ecsCommandRequest, String commandName,
      EcsListenerUpdateRequestConfigData requestConfigData) {
    return EcsListenerUpdateStateExecutionData.builder()
        .activityId(activityId)
        .accountId(accountId)
        .appId(appId)
        .ecsCommandRequest(ecsCommandRequest)
        .commandName(commandName)
        .ecsListenerUpdateRequestConfigData(requestConfigData)
        .build();
  }

  public DelegateTask getDelegateTask(String accountId, String appId, TaskType taskType, String waitId, Environment env,
      InfrastructureMapping infrastructureMapping, Object[] parameters, long timeout) {
    return DelegateTask.builder()
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
        .waitId(waitId)
        .data(TaskData.builder()
                  .async(true)
                  .taskType(taskType.name())
                  .parameters(parameters)
                  .timeout(TimeUnit.MINUTES.toMillis(timeout))
                  .build())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMapping.getUuid())
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, infrastructureMapping.getServiceId())
        .build();
  }

  public ContainerServiceElement buildContainerServiceElement(ExecutionContext context,
      ContainerSetupCommandUnitExecutionData setupExecutionData, ExecutionStatus status, ImageDetails imageDetails,
      String maxInstanceStr, String fixedInstanceStr, String desiredInstanceCountStr, ResizeStrategy resizeStrategy,
      int serviceSteadyStateTimeout, Logger logger) {
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    EcsSetupParams setupParams = (EcsSetupParams) executionData.getContainerSetupParams();
    Integer maxVal = null;
    if (isNotBlank(maxInstanceStr)) {
      try {
        maxVal = Integer.valueOf(context.renderExpression(maxInstanceStr));
      } catch (NumberFormatException e) {
        log.error(format("Invalid number format for max instances: %s", context.renderExpression(maxInstanceStr)), e);
      }
    }

    int evaluatedMaxInstances = maxVal != null ? maxVal : DEFAULT_MAX;
    int maxInstances = evaluatedMaxInstances == 0 ? DEFAULT_MAX : evaluatedMaxInstances;
    int evaluatedFixedInstances =
        isNotBlank(fixedInstanceStr) ? Integer.parseInt(context.renderExpression(fixedInstanceStr)) : maxInstances;
    int fixedInstances = evaluatedFixedInstances == 0 ? maxInstances : evaluatedFixedInstances;
    resizeStrategy = resizeStrategy == null ? RESIZE_NEW_FIRST : resizeStrategy;
    serviceSteadyStateTimeout =
        serviceSteadyStateTimeout > 0 ? serviceSteadyStateTimeout : DEFAULT_STEADY_STATE_TIMEOUT;
    ContainerServiceElementBuilder containerServiceElementBuilder =
        ContainerServiceElement.builder()
            .uuid(executionData.getServiceId())
            .image(imageDetails.getName() + ":" + imageDetails.getTag())
            .useFixedInstances(ContainerServiceSetupKeys.fixedInstances.equals(desiredInstanceCountStr))
            .fixedInstances(fixedInstances)
            .maxInstances(maxInstances)
            .resizeStrategy(resizeStrategy)
            .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
            .clusterName(executionData.getClusterName())
            .deploymentType(DeploymentType.ECS)
            .infraMappingId(setupParams.getInfraMappingId());

    if (setupExecutionData != null) {
      containerServiceElementBuilder.name(setupExecutionData.getContainerServiceName())
          .activeServiceCounts(setupExecutionData.getActiveServiceCounts());

      Integer instanceCountForLatestVersion = setupExecutionData.getInstanceCountForLatestVersion();
      if (instanceCountForLatestVersion != null && instanceCountForLatestVersion.intValue() > 0) {
        containerServiceElementBuilder.maxInstances(instanceCountForLatestVersion.intValue());
      }
    }

    containerServiceElementBuilder.newServiceAutoScalarConfig(setupParams.getNewAwsAutoScalarConfigList());
    return containerServiceElementBuilder.build();
  }

  public EcsInfrastructureMapping getInfrastructureMappingFromInfraMappingService(
      InfrastructureMappingService infrastructureMappingService, String appUuid, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appUuid, infraMappingId);
    if (!(infrastructureMapping instanceof EcsInfrastructureMapping)) {
      throw new InvalidRequestException("Invalid infrastructure type");
    }
    return (EcsInfrastructureMapping) infrastructureMapping;
  }

  public Application getApplicationFromExecutionContext(ExecutionContext executionContext) {
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    return workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
  }

  public Environment getEnvironmentFromExecutionContext(ExecutionContext executionContext) {
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    return workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
  }

  public EcsRunTaskDataBag prepareBagForEcsRunTask(ExecutionContext executionContext, Long timeout,
      boolean skipSteadyStateCheck, InfrastructureMappingService infrastructureMappingService,
      SettingsService settingsService, List<String> listTaskDefinitionJson, SecretManager secretManager,
      String runTaskFamilyName) {
    Application app = getApplicationFromExecutionContext(executionContext);
    Environment env = getEnvironmentFromExecutionContext(executionContext);

    EcsInfrastructureMapping ecsInfrastructureMapping = getInfrastructureMappingFromInfraMappingService(
        infrastructureMappingService, app.getUuid(), executionContext.fetchInfraMappingId());

    SettingAttribute settingAttribute = settingsService.get(ecsInfrastructureMapping.getComputeProviderSettingId());
    if (settingAttribute == null) {
      throw new InvalidArgumentsException(Pair.of("Cloud Provider", "Missing, check service infrastructure"));
    }
    SettingValue settingValue = settingAttribute.getValue();
    if (!(settingValue instanceof AwsConfig)) {
      throw new InvalidArgumentsException(Pair.of("Cloud Provider", "Must be of type Aws Config"));
    }
    AwsConfig awsConfig = (AwsConfig) settingValue;
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(
        awsConfig, executionContext.getAppId(), executionContext.getWorkflowExecutionId());

    return EcsRunTaskDataBag.builder()
        .applicationAccountId(app.getAccountId())
        .applicationUuid(app.getUuid())
        .applicationAppId(app.getAppId())
        .envUuid(env.getUuid())
        .awsConfig(awsConfig)
        .listTaskDefinitionJson(listTaskDefinitionJson)
        .ecsRunTaskFamilyName(runTaskFamilyName)
        .serviceSteadyStateTimeout(timeout)
        .skipSteadyStateCheck(skipSteadyStateCheck)
        .build();
  }

  public EcsSetUpDataBag prepareBagForEcsSetUp(ExecutionContext context, int timeout,
      ArtifactCollectionUtils artifactCollectionUtils, ServiceResourceService serviceResourceService,
      InfrastructureMappingService infrastructureMappingService, SettingsService settingsService,
      SecretManager secretManager) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    if (artifact == null) {
      throw new InvalidArgumentsException(Pair.of("args", "Artifact is null"));
    }

    ImageDetails imageDetails =
        artifactCollectionUtils.fetchContainerImageDetails(artifact, context.getWorkflowExecutionId());
    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);

    Service service = serviceResourceService.getWithDetails(app.getUuid(), serviceId);
    ContainerTask containerTask =
        serviceResourceService.getContainerTaskByDeploymentType(app.getUuid(), serviceId, DeploymentType.ECS.name());
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    if (!(infrastructureMapping instanceof EcsInfrastructureMapping)) {
      throw new InvalidRequestException("Invalid infrastructure type");
    }
    EcsInfrastructureMapping ecsInfrastructureMapping = (EcsInfrastructureMapping) infrastructureMapping;
    SettingAttribute settingAttribute = settingsService.get(ecsInfrastructureMapping.getComputeProviderSettingId());
    if (settingAttribute == null) {
      throw new InvalidArgumentsException(Pair.of("Cloud Provider", "Missing, check service infrastructure"));
    }
    SettingValue settingValue = settingAttribute.getValue();
    if (!(settingValue instanceof AwsConfig)) {
      throw new InvalidArgumentsException(Pair.of("Cloud Provider", "Must be of type Aws Config"));
    }
    AwsConfig awsConfig = (AwsConfig) settingValue;
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId());
    EcsServiceSpecification serviceSpecification =
        serviceResourceService.getEcsServiceSpecification(app.getUuid(), service.getUuid());
    if (timeout <= 0) {
      timeout = DEFAULT_STEADY_STATE_TIMEOUT;
    }

    return EcsSetUpDataBag.builder()
        .application(app)
        .service(service)
        .environment(env)
        .awsConfig(awsConfig)
        .imageDetails(imageDetails)
        .containerTask(containerTask)
        .serviceSteadyStateTimeout(timeout)
        .encryptedDataDetails(encryptedDataDetails)
        .serviceSpecification(serviceSpecification)
        .ecsInfrastructureMapping(ecsInfrastructureMapping)
        .build();
  }

  public EcsSetupContextVariableHolder renderEcsSetupContextVariables(ExecutionContext context) {
    if (featureFlagService.isNotEnabled(ENABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC, context.getAccountId())) {
      return EcsSetupContextVariableHolder.builder()
          .serviceVariables(Collections.EMPTY_MAP)
          .safeDisplayServiceVariables(Collections.EMPTY_MAP)
          .build();
    }

    Map<String, String> serviceVariables = context.getServiceVariables().entrySet().stream().collect(
        toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    EcsSetupContextVariableHolderBuilder builder = EcsSetupContextVariableHolder.builder();
    if (isNotEmpty(serviceVariables)) {
      serviceVariables.replaceAll((name, value) -> context.renderExpression(value));
      builder.serviceVariables(serviceVariables);
    }
    Map<String, String> safeDisplayServiceVariables = context.getSafeDisplayServiceVariables();
    if (isNotEmpty(safeDisplayServiceVariables)) {
      safeDisplayServiceVariables.replaceAll((name, value) -> context.renderExpression(value));
      builder.safeDisplayServiceVariables(safeDisplayServiceVariables);
    }
    return builder.build();
  }

  public void populateFromDelegateResponse(ContainerSetupCommandUnitExecutionData setupExecutionData,
      CommandStateExecutionData executionData, ContainerServiceElement containerServiceElement) {
    if (setupExecutionData != null) {
      // This will be non-null, when its ECS Daemon workflow
      executionData.setPreviousEcsServiceSnapshotJson(setupExecutionData.getPreviousEcsServiceSnapshotJson());
      executionData.setEcsServiceArn(setupExecutionData.getEcsServiceArn());
      executionData.setNewInstanceData(singletonList(ContainerServiceData.builder()
                                                         .name(setupExecutionData.getContainerServiceName())
                                                         .uniqueIdentifier(setupExecutionData.getEcsTaskDefintion())
                                                         .build()));
      executionData.setServiceName(setupExecutionData.getContainerServiceName());
      executionData.setLoadBalancer(containerServiceElement.getLoadBalancer());
      executionData.setPreviousAwsAutoScalarConfigs(setupExecutionData.getPreviousAwsAutoScalarConfigs());

      containerServiceElement.setPreviousAwsAutoScalarConfigs(setupExecutionData.getPreviousAwsAutoScalarConfigs());
      containerServiceElement.setNewEcsServiceName(setupExecutionData.getContainerServiceName());
      containerServiceElement.setEcsRegion(setupExecutionData.getEcsRegion());
      containerServiceElement.setTargetGroupForNewService(setupExecutionData.getTargetGroupForNewService());
      containerServiceElement.setTargetGroupForExistingService(setupExecutionData.getTargetGroupForExistingService());
      containerServiceElement.setMultipleLoadBalancersFeatureFlagActive(
          setupExecutionData.isMultipleLoadBalancersFeatureFlagActive());
      containerServiceElement.setAwsElbConfigs(setupExecutionData.getAwsElbConfigs());

      if (((EcsSetupParams) executionData.getContainerSetupParams()).isBlueGreen()) {
        EcsSetupParams ecsSetupParams = (EcsSetupParams) executionData.getContainerSetupParams();
        containerServiceElement.setEcsBGSetupData(
            EcsBGSetupData.builder()
                .prodEcsListener(ecsSetupParams.getProdListenerArn())
                .stageEcsListener(setupExecutionData.getStageEcsListener())
                .ecsBGTargetGroup1(ecsSetupParams.getTargetGroupArn())
                .ecsBGTargetGroup2(ecsSetupParams.getTargetGroupArn2())
                .isUseSpecificListenerRuleArn(ecsSetupParams.isUseSpecificListenerRuleArn())
                .stageListenerRuleArn(ecsSetupParams.getStageListenerRuleArn())
                .prodListenerRuleArn(ecsSetupParams.getProdListenerRuleArn())
                .ecsBlueGreen(true)
                .downsizedServiceName(setupExecutionData.getEcsServiceToBeDownsized())
                .downsizedServiceCount(setupExecutionData.getCountToBeDownsizedForOldService())
                .parentRecordName(setupExecutionData.getParentRecordName())
                .parentRecordHostedZoneId(setupExecutionData.getParentRecordHostedZoneId())
                .useRoute53Swap(setupExecutionData.isUseRoute53Swap())
                .oldServiceDiscoveryArn(setupExecutionData.getOldServiceDiscoveryArn())
                .newServiceDiscoveryArn(setupExecutionData.getNewServiceDiscoveryArn())
                .build());
      }
    }
  }

  public CommandStateExecutionData getStateExecutionData(
      EcsSetUpDataBag dataBag, String commandName, EcsSetupParams ecsSetupParams, Activity activity) {
    return getStateExecutionData(dataBag, commandName, ecsSetupParams, activity.getUuid());
  }

  public CommandStateExecutionData getStateExecutionData(
      EcsSetUpDataBag ecsSetUpDataBag, String commandName, EcsSetupParams ecsSetupParams, String activityId) {
    return CommandStateExecutionData.Builder.aCommandStateExecutionData()
        .withServiceId(ecsSetUpDataBag.getService().getUuid())
        .withServiceName(ecsSetUpDataBag.getService().getName())
        .withAppId(ecsSetUpDataBag.getApplication().getUuid())
        .withCommandName(commandName)
        .withContainerSetupParams(ecsSetupParams)
        .withClusterName(ecsSetUpDataBag.getEcsInfrastructureMapping().getClusterName())
        .withActivityId(activityId)
        .build();
  }

  public DelegateTask createAndQueueDelegateTaskForEcsServiceSetUp(EcsCommandRequest request, EcsSetUpDataBag dataBag,
      Activity activity, DelegateService delegateService, boolean selectionLogsEnabled) {
    return createAndQueueDelegateTaskForEcsServiceSetUp(
        request, dataBag, activity.getUuid(), delegateService, selectionLogsEnabled);
  }

  public DelegateTask createAndQueueDelegateTaskForEcsServiceSetUp(EcsCommandRequest ecsCommandRequest,
      EcsSetUpDataBag ecsSetUpDataBag, String activityId, DelegateService delegateService,
      boolean selectionLogsEnabled) {
    DelegateTask task =
        DelegateTask.builder()
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, ecsSetUpDataBag.getApplication().getUuid())
            .accountId(ecsSetUpDataBag.getApplication().getAccountId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ecsSetUpDataBag.getEnvironment().getUuid())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, ecsSetUpDataBag.getEnvironment().getEnvironmentType().name())
            .waitId(activityId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(ECS_COMMAND_TASK.name())
                      .parameters(new Object[] {ecsCommandRequest, ecsSetUpDataBag.getEncryptedDataDetails()})
                      .timeout(MINUTES.toMillis(ecsSetUpDataBag.getServiceSteadyStateTimeout()))
                      .build())
            .tags(isNotEmpty(ecsSetUpDataBag.getAwsConfig().getTag())
                    ? singletonList(ecsSetUpDataBag.getAwsConfig().getTag())
                    : null)
            .setupAbstraction(
                Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, ecsSetUpDataBag.getEcsInfrastructureMapping().getUuid())
            .setupAbstraction(
                Cd1SetupFields.SERVICE_ID_FIELD, ecsSetUpDataBag.getEcsInfrastructureMapping().getServiceId())
            .description("ECS command task execution")
            .selectionLogsTrackingEnabled(selectionLogsEnabled)
            .build();
    delegateService.queueTask(task);
    return task;
  }

  private void setUpEcsServiceAndContainerSpecFromDataBagAndAppManifest(EcsSetUpDataBag ecsSetUpDataBag,
      ApplicationManifest applicationManifest, List<GitFile> gitFiles, ExecutionContext executionContext) {
    EcsServiceSpecification ecsServiceSpecification = getOrCreateServiceSpec(ecsSetUpDataBag);
    ContainerTask containerTask = getOrCreateTaskSpec(ecsSetUpDataBag);

    String containerTaskFilePath = applicationManifest.getGitFileConfig().getTaskSpecFilePath();
    String containerSpec = getFileContentByPathFromGitFiles(gitFiles, containerTaskFilePath);
    containerTask.setAdvancedConfig(executionContext.renderExpression(containerSpec));
    ecsSetUpDataBag.setContainerTask(containerTask);

    if (!applicationManifest.getGitFileConfig().isUseInlineServiceDefinition()) {
      String serviceSpecFilePath = applicationManifest.getGitFileConfig().getServiceSpecFilePath();
      String serviceSpec = getFileContentByPathFromGitFiles(gitFiles, serviceSpecFilePath);
      ecsServiceSpecification.setServiceSpecJson(executionContext.renderExpression(serviceSpec));
      ecsSetUpDataBag.setServiceSpecification(ecsServiceSpecification);
    }
  }

  public void setUpRemoteContainerTaskAndServiceSpecForEcsRoute53IfRequired(
      ExecutionContext executionContext, EcsSetUpDataBag ecsSetUpDataBag, Logger logger) {
    if (!(executionContext.getStateExecutionData() instanceof EcsBGRoute53SetupStateExecutionData)) {
      return;
    }
    EcsBGRoute53SetupStateExecutionData ecsBGRoute53SetupStateExecutionData =
        (EcsBGRoute53SetupStateExecutionData) executionContext.getStateExecutionData();

    if (ecsBGRoute53SetupStateExecutionData != null && ecsSetUpDataBag != null) {
      ApplicationManifest applicationManifest =
          ecsBGRoute53SetupStateExecutionData.getApplicationManifestMap().get(K8sValuesLocation.ServiceOverride);
      GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
      if (gitFileConfig != null
          && (gitFileConfig.getServiceSpecFilePath() != null || gitFileConfig.getTaskSpecFilePath() != null)) {
        List<GitFile> gitFiles = ecsBGRoute53SetupStateExecutionData.getFetchFilesResult()
                                     .getFilesFromMultipleRepo()
                                     .get("ServiceOverride")
                                     .getFiles();

        setUpEcsServiceAndContainerSpecFromDataBagAndAppManifest(
            ecsSetUpDataBag, applicationManifest, gitFiles, executionContext);
      } else {
        log.error("Manifest does not contain the proper git file config, git fetch files response can not be read.");
        throw new InvalidRequestException("Manifest does not contain the proper git file config");
      }
    }
  }

  public void setUpRemoteContainerTaskAndServiceSpecIfRequired(
      ExecutionContext executionContext, EcsSetUpDataBag ecsSetUpDataBag, Logger logger) {
    if (!(executionContext.getStateExecutionData() instanceof EcsSetupStateExecutionData)) {
      return;
    }
    EcsSetupStateExecutionData ecsSetupStateExecutionData =
        (EcsSetupStateExecutionData) executionContext.getStateExecutionData();

    if (ecsSetupStateExecutionData != null && ecsSetUpDataBag != null) {
      ApplicationManifest applicationManifest =
          ecsSetupStateExecutionData.getApplicationManifestMap().get(K8sValuesLocation.ServiceOverride);
      GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
      if (gitFileConfig != null
          && (gitFileConfig.getServiceSpecFilePath() != null || gitFileConfig.getTaskSpecFilePath() != null)) {
        List<GitFile> gitFiles = ecsSetupStateExecutionData.getFetchFilesResult()
                                     .getFilesFromMultipleRepo()
                                     .get("ServiceOverride")
                                     .getFiles();

        setUpEcsServiceAndContainerSpecFromDataBagAndAppManifest(
            ecsSetUpDataBag, applicationManifest, gitFiles, executionContext);
      } else {
        log.error("Manifest does not contain the proper git file config, git fetch files response can not be read.");
        throw new InvalidRequestException("Manifest does not contain the proper git file config");
      }
    }
  }

  public String getFileContentByPathFromGitFiles(List<GitFile> gitFiles, String filePath) {
    Optional<GitFile> gitFile = gitFiles.stream().filter(f -> f.getFilePath().equals(filePath)).findFirst();
    if (gitFile.isPresent()) {
      return gitFile.get().getFileContent();
    } else {
      throw new InvalidArgumentsException("No file with path " + filePath + " found");
    }
  }

  private EcsServiceSpecification getOrCreateServiceSpec(EcsSetUpDataBag ecsSetUpDataBag) {
    EcsServiceSpecification ecsServiceSpecification = ecsSetUpDataBag.getServiceSpecification();
    if (ecsServiceSpecification == null) {
      ecsServiceSpecification =
          EcsServiceSpecification.builder().serviceId(ecsSetUpDataBag.getService().getUuid()).build();
      ecsServiceSpecification.setAppId(ecsSetUpDataBag.application.getAppId());
      ecsServiceSpecification.resetToDefaultSpecification();
    }
    return ecsServiceSpecification;
  }

  private EcsContainerTask getOrCreateTaskSpec(EcsSetUpDataBag ecsSetUpDataBag) {
    EcsContainerTask ecsContainerTask = (EcsContainerTask) ecsSetUpDataBag.getContainerTask();
    if (ecsContainerTask == null) {
      ecsContainerTask = new EcsContainerTask();
      software.wings.beans.container.ContainerDefinition containerDefinition =
          software.wings.beans.container.ContainerDefinition.builder()
              .memory(256)
              .cpu(1d)
              .portMappings(emptyList())
              .build();
      ecsContainerTask.setContainerDefinitions(newArrayList(containerDefinition));
      ecsContainerTask.setServiceId(ecsSetUpDataBag.getService().getUuid());
      ecsContainerTask.setAccountId(ecsSetUpDataBag.getApplication().getAccountId());
      ecsContainerTask.setAppId(ecsSetUpDataBag.getApplication().getAppId());
    }

    return ecsContainerTask;
  }

  private void updateContainerElementAfterSuccessfulResize(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ContainerServiceElement containerElement =
        context.<ContainerServiceElement>getContextElementList(ContextElementType.CONTAINER_SERVICE)
            .stream()
            .filter(cse -> phaseElement.getDeploymentType().equals(cse.getDeploymentType().name()))
            .filter(cse -> context.fetchInfraMappingId().equals(cse.getInfraMappingId()))
            .findFirst()
            .orElse(ContainerServiceElement.builder().build());
    containerElement.setPrevAutoscalarsAlreadyRemoved(true);
  }

  // This will be used later to add existing containerInfos as well.
  private void setNewInstanceFlag(
      List<InstanceElement> instanceElements, boolean flag, List<InstanceElement> finalInstanceElements) {
    if (isNotEmpty(instanceElements)) {
      instanceElements.forEach(instanceElement -> { instanceElement.setNewInstance(flag); });
      finalInstanceElements.addAll(instanceElements);
    }
  }

  public ExecutionResponse handleDelegateResponseForEcsDeploy(ExecutionContext context,
      Map<String, ResponseData> response, boolean rollback, ActivityService activityService, boolean rollbackAllPhases,
      ContainerDeploymentManagerHelper containerDeploymentHelper) {
    String activityId = response.keySet().iterator().next();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();

    EcsServiceDeployResponse deployResponse = (EcsServiceDeployResponse) executionResponse.getEcsCommandResponse();
    InstanceElementListParam listParam = InstanceElementListParam.builder().build();

    if (deployResponse != null) {
      List<InstanceStatusSummary> instanceStatusSummaries =
          containerDeploymentHelper.getInstanceStatusSummaries(context, deployResponse.getContainerInfos());
      executionData.setNewInstanceStatusSummaries(instanceStatusSummaries);

      List<InstanceElement> finalInstanceElements = new ArrayList<>();
      List<InstanceElement> instanceElements =
          instanceStatusSummaries.stream().map(InstanceStatusSummary::getInstanceElement).collect(toList());
      setNewInstanceFlag(instanceElements, true, finalInstanceElements);

      listParam = InstanceElementListParam.builder().instanceElements(finalInstanceElements).build();

      List<InstanceElement> allInstanceElements =
          getAllInstanceElements(context, containerDeploymentHelper, deployResponse, finalInstanceElements);
      // This sweeping element will be used by verification or other consumers.
      List<InstanceDetails> instanceDetails = generateEcsInstanceDetails(allInstanceElements);
      boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                     .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                     .value(InstanceInfoVariables.builder()
                                                .instanceElements(allInstanceElements)
                                                .instanceDetails(instanceDetails)
                                                .skipVerification(skipVerification)
                                                .build())
                                     .build());

      executionData.setOldInstanceData(deployResponse.getOldInstanceData());
      executionData.setNewInstanceData(deployResponse.getNewInstanceData());
      executionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

      if (!rollback) {
        saveDeploySweepingOutputForRollback(
            context, deployResponse.getOldInstanceData(), deployResponse.getNewInstanceData());
      }
    }

    if (rollback && rollbackAllPhases && SUCCESS == executionStatus) {
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                     .name(ECS_ALL_PHASE_ROLLBACK_DONE)
                                     .value(AwsEcsAllPhaseRollbackData.builder().allPhaseRollbackDone(true).build())
                                     .build());
    }

    if (!rollback && SUCCESS == executionStatus) {
      updateContainerElementAfterSuccessfulResize(context);
    }

    ExecutionResponseBuilder builder = ExecutionResponse.builder()
                                           .stateExecutionData(executionData)
                                           .executionStatus(executionStatus)
                                           .contextElement(listParam)
                                           .notifyElement(listParam);

    if (null != deployResponse && deployResponse.isTimeoutFailure()) {
      builder.failureTypes(TIMEOUT);
    }
    return builder.build();
  }

  private List<InstanceDetails> generateEcsInstanceDetails(List<InstanceElement> allInstanceElements) {
    if (isEmpty(allInstanceElements)) {
      return emptyList();
    }

    return allInstanceElements.stream()
        .filter(instanceElement -> instanceElement != null)
        .map(instanceElement
            -> InstanceDetails.builder()
                   .instanceType(AWS)
                   .newInstance(instanceElement.isNewInstance())
                   .hostName(instanceElement.getHostName())
                   .aws(InstanceDetails.AWS.builder()
                            .ec2Instance(
                                instanceElement.getHost() != null ? instanceElement.getHost().getEc2Instance() : null)
                            .publicDns(
                                instanceElement.getHost() != null ? instanceElement.getHost().getPublicDns() : null)
                            .instanceId(
                                instanceElement.getHost() != null ? instanceElement.getHost().getInstanceId() : null)
                            .ip(instanceElement.getHost() != null ? instanceElement.getHost().getIp() : null)
                            .dockerId(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getDockerId()
                                    : null)
                            .completeDockerId(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getCompleteDockerId()
                                    : null)
                            .taskId(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getTaskId()
                                    : null)
                            .taskArn(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getTaskArn()
                                    : null)
                            .containerInstanceId(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getContainerInstanceId()
                                    : null)
                            .containerInstanceArn(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getContainerInstanceArn()
                                    : null)
                            .containerId(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getContainerId()
                                    : null)
                            .ecsServiceName(instanceElement.getEcsContainerDetails() != null
                                    ? instanceElement.getEcsContainerDetails().getEcsServiceName()
                                    : null)
                            .build())
                   .build())
        .collect(toList());
  }

  @NotNull
  private List<InstanceElement> getAllInstanceElements(ExecutionContext context,
      ContainerDeploymentManagerHelper containerDeploymentHelper, EcsServiceDeployResponse deployResponse,
      List<InstanceElement> finalInstanceElements) {
    List<InstanceElement> allInstanceElements = new ArrayList<>();
    allInstanceElements.addAll(finalInstanceElements);

    List<ContainerInfo> previousContainerInfos = deployResponse.getPreviousContainerInfos();

    List<InstanceStatusSummary> instanceStatusSummariesForPreviousContainers = new ArrayList<>();

    if (isNotEmpty(previousContainerInfos)) {
      instanceStatusSummariesForPreviousContainers.addAll(
          containerDeploymentHelper.getInstanceStatusSummaries(context, previousContainerInfos));
      allInstanceElements.addAll(instanceStatusSummariesForPreviousContainers.stream()
                                     .map(instanceStatusSummary -> instanceStatusSummary.getInstanceElement())
                                     .collect(toList()));
    }

    return allInstanceElements;
  }

  public DelegateTask createAndQueueDelegateTaskForEcsServiceDeploy(EcsDeployDataBag deployDataBag,
      EcsServiceDeployRequest request, Activity activity, DelegateService delegateService,
      boolean selectionLogsEnabled) {
    DelegateTask task =
        DelegateTask.builder()
            .accountId(deployDataBag.getApp().getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, deployDataBag.getApp().getUuid())
            .waitId(activity.getUuid())
            .tags(isNotEmpty(deployDataBag.getAwsConfig().getTag())
                    ? singletonList(deployDataBag.getAwsConfig().getTag())
                    : null)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.ECS_COMMAND_TASK.name())
                      .parameters(new Object[] {request, deployDataBag.getEncryptedDataDetails()})
                      .timeout(MINUTES.toMillis(getTimeout(deployDataBag)))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, deployDataBag.getEnv().getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, deployDataBag.getEnv().getEnvironmentType().name())
            .setupAbstraction(
                Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, deployDataBag.getEcsInfrastructureMapping().getUuid())
            .setupAbstraction(
                Cd1SetupFields.SERVICE_ID_FIELD, deployDataBag.getEcsInfrastructureMapping().getServiceId())
            .selectionLogsTrackingEnabled(selectionLogsEnabled)
            .description("ECS Command task execution")
            .build();
    delegateService.queueTask(task);
    return task;
  }

  public DelegateTask createAndQueueDelegateTaskForEcsRunTaskDeploy(EcsRunTaskDataBag ecsRunTaskDataBag,
      InfrastructureMappingService infrastructureMappingService, SecretManager secretManager, Application application,
      ExecutionContext executionContext, EcsRunTaskDeployRequest request, String activityId,
      DelegateService delegateService, boolean selectionLogsEnabled) {
    String waitId = generateUuid();

    EcsInfrastructureMapping ecsInfrastructureMapping = getInfrastructureMappingFromInfraMappingService(
        infrastructureMappingService, application.getUuid(), executionContext.fetchInfraMappingId());

    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(
        ecsRunTaskDataBag.getAwsConfig(), executionContext.getAppId(), executionContext.getWorkflowExecutionId());

    DelegateTask task =
        DelegateTask.builder()
            .accountId(ecsRunTaskDataBag.getApplicationAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, ecsRunTaskDataBag.getApplicationUuid())
            .waitId(waitId)
            .tags(isNotEmpty(ecsRunTaskDataBag.getAwsConfig().getTag())
                    ? singletonList(ecsRunTaskDataBag.getAwsConfig().getTag())
                    : null)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.ECS_COMMAND_TASK.name())
                      .parameters(new Object[] {request, encryptedDataDetails})
                      .timeout(MINUTES.toMillis(getTimeout(ecsRunTaskDataBag)))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ecsRunTaskDataBag.getEnvUuid())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, executionContext.fetchRequiredEnvironment().getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, ecsInfrastructureMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, ecsInfrastructureMapping.getServiceId())
            .selectionLogsTrackingEnabled(selectionLogsEnabled)
            .description("ECS Run task deploy execution")
            .build();
    delegateService.queueTask(task);
    return task;
  }

  long getTimeout(EcsRunTaskDataBag deployDataBag) {
    return deployDataBag.getServiceSteadyStateTimeout() + 30l;
  }

  long getTimeout(EcsDeployDataBag deployDataBag) {
    return deployDataBag.getContainerElement().getServiceSteadyStateTimeout();
  }

  public EcsDeployDataBag prepareBagForEcsDeploy(ExecutionContext context,
      ServiceResourceService serviceResourceService, InfrastructureMappingService infrastructureMappingService,
      SettingsService settingsService, SecretManager secretManager, boolean rollback) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    Service svc = serviceResourceService.getWithDetails(app.getUuid(), serviceId);
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    if (!(infrastructureMapping instanceof EcsInfrastructureMapping)) {
      throw new InvalidRequestException(
          format("Invalid infrmapping type: [%s]", infrastructureMapping.getClass().getName()));
    }
    EcsInfrastructureMapping ecsInfrastructureMapping = (EcsInfrastructureMapping) infrastructureMapping;
    SettingAttribute settingAttribute = settingsService.get(ecsInfrastructureMapping.getComputeProviderSettingId());
    SettingValue settingValue = settingAttribute.getValue();
    if (!(settingValue instanceof AwsConfig)) {
      throw new InvalidRequestException(format("Invalid setting value type: [%s]", settingValue.getClass().getName()));
    }
    AwsConfig awsConfig = (AwsConfig) settingValue;
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId());
    String region = ecsInfrastructureMapping.getRegion();
    ContainerServiceElement containerElement = getSetupElementFromSweepingOutput(context, rollback);

    ContainerRollbackRequestElement rollbackElement = null;
    if (rollback) {
      rollbackElement = getDeployElementFromSweepingOutput(context);
    }

    return EcsDeployDataBag.builder()
        .app(app)
        .env(env)
        .service(svc)
        .region(region)
        .awsConfig(awsConfig)
        .rollbackElement(rollbackElement)
        .containerElement(containerElement)
        .encryptedDataDetails(encryptionDetails)
        .ecsInfrastructureMapping(ecsInfrastructureMapping)
        .build();
  }

  ContainerRollbackRequestElement getDeployElementFromSweepingOutput(ExecutionContext context) {
    String sweepingOutputName = getSweepingOutputName(context, true, ECS_SERVICE_DEPLOY_SWEEPING_OUTPUT_NAME);
    SweepingOutputInquiry inquiry = context.prepareSweepingOutputInquiryBuilder().name(sweepingOutputName).build();
    return sweepingOutputService.findSweepingOutput(inquiry);
  }

  ContainerServiceElement getSetupElementFromSweepingOutput(ExecutionContext context, boolean rollback) {
    String sweepingOutputName = getSweepingOutputName(context, rollback, ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    SweepingOutputInquiry inquiry = context.prepareSweepingOutputInquiryBuilder().name(sweepingOutputName).build();
    return sweepingOutputService.findSweepingOutput(inquiry);
  }

  private void saveDeploySweepingOutputForRollback(ExecutionContext context, List<ContainerServiceData> oldInstanceData,
      List<ContainerServiceData> newInstanceData) {
    ContainerRollbackRequestElement element = ContainerRollbackRequestElement.builder()
                                                  .oldInstanceData(reverse(newInstanceData))
                                                  .newInstanceData(reverse(oldInstanceData))
                                                  .build();
    sweepingOutputService.ensure(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(getSweepingOutputName(context, false, ECS_SERVICE_DEPLOY_SWEEPING_OUTPUT_NAME))
            .value(element)
            .build());
  }

  @VisibleForTesting
  List<ContainerServiceData> reverse(List<ContainerServiceData> serviceData) {
    if (isEmpty(serviceData)) {
      return emptyList();
    }
    return serviceData.stream()
        .map(sc
            -> ContainerServiceData.builder()
                   .name(sc.getName())
                   .image(sc.getImage())
                   .previousCount(sc.getDesiredCount())
                   .desiredCount(sc.getPreviousCount())
                   .previousTraffic(sc.getDesiredTraffic())
                   .desiredTraffic(sc.getPreviousTraffic())
                   .build())
        .collect(toList());
  }

  @NotNull
  String getSweepingOutputName(ExecutionContext context, boolean rollback, String prefix) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String suffix = "";
    if (ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME.equals(prefix)) {
      suffix = phaseElement.getServiceElement().getUuid().trim();
    } else if (ECS_SERVICE_DEPLOY_SWEEPING_OUTPUT_NAME.equals(prefix)) {
      if (rollback) {
        suffix = phaseElement.getPhaseNameForRollback().trim();
      } else {
        suffix = phaseElement.getPhaseName().trim();
      }
    }
    return prefix + suffix;
  }

  public Integer getEcsStateTimeoutFromContext(ExecutionContext context, boolean isRollback) {
    ContainerServiceElement containerElement = getSetupElementFromSweepingOutput(context, isRollback);
    if (containerElement == null || containerElement.getServiceSteadyStateTimeout() == 0) {
      return null;
    }
    return getTimeout(containerElement.getServiceSteadyStateTimeout());
  }

  public boolean isRemoteManifest(Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    for (Map.Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      ApplicationManifest applicationManifest = entry.getValue();
      if (StoreType.Remote == applicationManifest.getStoreType()) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public Integer getTimeout(Integer timeoutInMinutes) {
    try {
      return Ints.checkedCast(TimeUnit.MINUTES.toMillis(timeoutInMinutes + DEFAULT_STATE_TIMEOUT_BUFFER_MIN));
    } catch (Exception e) {
      log.warn("Could not convert {} minutes to millis, falling back to default timeout", timeoutInMinutes);
      return null;
    }
  }

  boolean allPhaseRollbackDone(ExecutionContext context) {
    SweepingOutputInquiry inquiry =
        context.prepareSweepingOutputInquiryBuilder().name(ECS_ALL_PHASE_ROLLBACK_DONE).build();
    SweepingOutput sweepingOutput = sweepingOutputService.findSweepingOutput(inquiry);
    if (sweepingOutput == null) {
      return false;
    }
    return ((AwsEcsAllPhaseRollbackData) sweepingOutput).isAllPhaseRollbackDone();
  }

  int renderTimeout(String expr, ExecutionContext context, int defaultValue) {
    int retVal = defaultValue;
    if (isNotEmpty(expr)) {
      try {
        retVal = Integer.parseInt(context.renderExpression(expr));
      } catch (NumberFormatException e) {
        log.error(format("Number format Exception while evaluating: [%s]", expr), e);
        retVal = defaultValue;
      }
    }
    return retVal;
  }

  public void createSweepingOutputForRollback(EcsDeployDataBag deployDataBag, Activity activity,
      DelegateService delegateService, EcsResizeParams resizeParams, ExecutionContext context, boolean blueGreen) {
    EcsDeployRollbackDataFetchRequest request =
        EcsDeployRollbackDataFetchRequest.builder()
            .accountId(deployDataBag.getApp().getAccountId())
            .appId(deployDataBag.getApp().getUuid())
            .commandName(ECS_SERVICE_DEPLOY)
            .activityId(activity.getUuid())
            .region(deployDataBag.getRegion())
            .cluster(deployDataBag.getEcsInfrastructureMapping().getClusterName())
            .awsConfig(deployDataBag.getAwsConfig())
            .ecsResizeParams(resizeParams)
            .blueGreen(blueGreen)
            .build();

    DelegateTask task =
        DelegateTask.builder()
            .accountId(deployDataBag.getApp().getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, deployDataBag.getApp().getUuid())
            .waitId(activity.getUuid())
            .tags(isNotEmpty(deployDataBag.getAwsConfig().getTag())
                    ? singletonList(deployDataBag.getAwsConfig().getTag())
                    : null)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.ECS_COMMAND_TASK.name())
                      .parameters(new Object[] {request, deployDataBag.getEncryptedDataDetails()})
                      .timeout(MINUTES.toMillis(getTimeout(deployDataBag)))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, deployDataBag.getEnv().getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, deployDataBag.getEnv().getEnvironmentType().name())
            .setupAbstraction(
                Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, deployDataBag.getEcsInfrastructureMapping().getUuid())
            .setupAbstraction(
                Cd1SetupFields.SERVICE_ID_FIELD, deployDataBag.getEcsInfrastructureMapping().getServiceId())
            .build();

    EcsCommandExecutionResponse delegateResponse;
    try {
      delegateResponse = delegateService.executeTask(task);
    } catch (InterruptedException e) {
      log.error("", e);
      Thread.currentThread().interrupt();
      throw new InvalidRequestException("Failed to generate rollback information", e, WingsException.USER);
    }

    EcsDeployRollbackDataFetchResponse response =
        (EcsDeployRollbackDataFetchResponse) delegateResponse.getEcsCommandResponse();
    saveDeploySweepingOutputForRollback(context, response.getOldInstanceData(), response.getNewInstanceData());
  }
}
