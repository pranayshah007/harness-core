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
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.service.impl.aws.model.AwsConstants.ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.PROD_LISTENER;
import static software.wings.service.impl.aws.model.AwsConstants.STAGE_LISTENER;
import static software.wings.sm.StateType.ECS_BG_SERVICE_SETUP;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsBGSetupStateExecutionData;
import software.wings.api.ecs.EcsSetupStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.helpers.ext.ecs.request.EcsBGServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceSetupResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionData;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.ApplicationManifestUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EcsBlueGreenServiceSetup extends State {
  public static final String ECS_SERVICE_SETUP_COMMAND_ELB = "ECS Service Setup ELB";

  @Getter @Setter private String maxInstances;
  @Getter @Setter private String fixedInstances;
  @Getter @Setter private String ecsServiceName;
  @Getter @Setter private boolean useLoadBalancer;
  @Getter @Setter private String loadBalancerName;
  @Getter @Setter @Attributes(title = "Stage TargetGroup Arn", required = false) private String targetGroupArn;
  @Getter @Setter private String roleArn;
  @Getter @Setter @Attributes(title = "Prod Listener ARN", required = true) private String prodListenerArn;
  @Getter @Setter @Attributes(title = "Stage Listener ARN", required = true) private String stageListenerArn;
  @Getter @Setter @Attributes(title = "Prod Listener Rule ARN", required = false) private String stageListenerRuleArn;
  @Getter @Setter @Attributes(title = "Stage Listener Rule ARN", required = false) private String prodListenerRuleArn;
  @Getter @Setter private boolean isUseSpecificListenerRuleArn;
  @Getter @Setter private String desiredInstanceCount;
  @Getter @Setter private String targetContainerName;
  @Getter @Setter private String targetPort;
  @Getter @Setter @Attributes(title = "Stage Listener Port", required = false) private String stageListenerPort;
  @Getter @Setter private int serviceSteadyStateTimeout;
  @Getter @Setter private ResizeStrategy resizeStrategy;
  @Getter @Setter private List<AwsAutoScalarConfig> awsAutoScalarConfigs;

  @Inject private EcsStateHelper ecsStateHelper;
  @Inject private SecretManager secretManager;
  @Inject private ActivityService activityService;
  @Inject private AppService appService;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private LogService logService;

  public EcsBlueGreenServiceSetup(String name) {
    super(name, ECS_BG_SERVICE_SETUP.name());
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
  public Integer getTimeoutMillis() {
    if (serviceSteadyStateTimeout == 0) {
      return null;
    }
    return ecsStateHelper.getTimeout(serviceSteadyStateTimeout);
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    boolean valuesInGit = false;
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();

    EcsSetUpDataBag dataBag = ecsStateHelper.prepareBagForEcsSetUp(context, serviceSteadyStateTimeout,
        artifactCollectionUtils, serviceResourceService, infrastructureMappingService, settingsService, secretManager);

    appManifestMap = applicationManifestUtils.getApplicationManifests(context, AppManifestKind.K8S_MANIFEST);
    valuesInGit = ecsStateHelper.isRemoteManifest(appManifestMap);

    Activity activity = ecsStateHelper.createActivity(context, ECS_SERVICE_SETUP_COMMAND_ELB, getStateType(),
        CommandUnitType.AWS_ECS_SERVICE_SETUP_ELB, activityService);

    if (valuesInGit) {
      return executeGitFetchFilesTask(context, appManifestMap, activity.getUuid(), dataBag);
    } else {
      return executeEcsBGTask(context, appManifestMap, activity.getUuid(), dataBag);
    }
  }

  private ExecutionResponse executeGitFetchFilesTask(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, String activityId,
      EcsSetUpDataBag ecsSetUpDataBag) {
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_SERVICE_SETUP_COMMAND_ELB, logService);

    String logMessage = "Creating a task to fetch files from git.";
    executionLogCallback.saveExecutionLog(logMessage, CommandExecutionStatus.RUNNING);
    final DelegateTask gitFetchFileTask = createGitFetchFileAsyncTask(context, applicationManifestMap, activityId);
    delegateService.queueTask(gitFetchFileTask);

    EcsBGSetupStateExecutionData stateExecutionData =
        createStateExecutionData(activityId, applicationManifestMap, context, ecsSetUpDataBag);
    appendDelegateTaskDetails(context, gitFetchFileTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(gitFetchFileTask.getWaitId()))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private ExecutionResponse executeEcsBGTask(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, String activityId,
      EcsSetUpDataBag ecsSetUpDataBag) {
    isUseSpecificListenerRuleArn = shouldUseSpecificListenerRule(prodListenerRuleArn, stageListenerRuleArn);

    ecsStateHelper.setUpRemoteContainerTaskAndServiceSpecIfRequired(context, ecsSetUpDataBag, log);

    EcsSetupParams ecsSetupParams = (EcsSetupParams) ecsStateHelper.buildContainerSetupParams(context,
        EcsSetupStateConfig.builder()
            .app(ecsSetUpDataBag.getApplication())
            .env(ecsSetUpDataBag.getEnvironment())
            .service(ecsSetUpDataBag.getService())
            .infrastructureMapping(ecsSetUpDataBag.getEcsInfrastructureMapping())
            .clusterName(ecsSetUpDataBag.getEcsInfrastructureMapping().getClusterName())
            .containerTask(ecsSetUpDataBag.getContainerTask())
            .ecsServiceName(ecsServiceName)
            .imageDetails(ecsSetUpDataBag.getImageDetails())
            .loadBalancerName(loadBalancerName)
            .roleArn(roleArn)
            .serviceSteadyStateTimeout(ecsSetUpDataBag.getServiceSteadyStateTimeout())
            .targetContainerName(targetContainerName)
            .prodListenerRuleArn(prodListenerRuleArn)
            .stageListenerRuleArn(stageListenerRuleArn)
            .isUseSpecificListenerRuleArn(isUseSpecificListenerRuleArn)
            .stageListenerArn(stageListenerArn)
            .prodListenerArn(prodListenerArn)
            .stageListenerArn(stageListenerArn)
            .stageListenerPort(stageListenerPort)
            .awsAutoScalarConfigs(awsAutoScalarConfigs)
            .blueGreen(true)
            .targetPort(targetPort)
            .useLoadBalancer(true)
            .serviceName(ecsSetUpDataBag.getService().getName())
            .ecsServiceSpecification(ecsSetUpDataBag.getServiceSpecification())
            .isDaemonSchedulingStrategy(false)
            .targetGroupArn(targetGroupArn)
            .build());

    CommandStateExecutionData stateExecutionData = ecsStateHelper.getStateExecutionData(
        ecsSetUpDataBag, ECS_SERVICE_SETUP_COMMAND_ELB, ecsSetupParams, activityId);

    EcsSetupContextVariableHolder variables = ecsStateHelper.renderEcsSetupContextVariables(context);

    EcsBGServiceSetupRequest request = EcsBGServiceSetupRequest.builder()
                                           .accountId(ecsSetUpDataBag.getApplication().getAccountId())
                                           .appId(ecsSetUpDataBag.getApplication().getUuid())
                                           .commandName(ECS_SERVICE_SETUP_COMMAND_ELB)
                                           .activityId(activityId)
                                           .ecsSetupParams(ecsSetupParams)
                                           .awsConfig(ecsSetUpDataBag.getAwsConfig())
                                           .clusterName(ecsSetUpDataBag.getEcsInfrastructureMapping().getClusterName())
                                           .region(ecsSetUpDataBag.getEcsInfrastructureMapping().getRegion())
                                           .safeDisplayServiceVariables(variables.getSafeDisplayServiceVariables())
                                           .serviceVariables(variables.getServiceVariables())
                                           .timeoutErrorSupported(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT,
                                               ecsSetUpDataBag.getApplication().getAccountId()))
                                           .build();

    DelegateTask task = ecsStateHelper.createAndQueueDelegateTaskForEcsServiceSetUp(
        request, ecsSetUpDataBag, activityId, delegateService, isSelectionLogsTrackingForTasksEnabled());
    appendDelegateTaskDetails(context, task);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activityId))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    StateExecutionData stateExecutionData = context.getStateExecutionData();

    if (stateExecutionData instanceof EcsBGSetupStateExecutionData) {
      return handleAsyncInternalGitTask(context, response);
    } else if (stateExecutionData instanceof CommandStateExecutionData) {
      return handleAsyncInternalEcsBGTask(context, response);
    } else {
      throw new InvalidRequestException("Unhandled task type for state " + stateExecutionData.getStateName()
          + " and class " + stateExecutionData.getClass());
    }
  }

  private ExecutionResponse handleAsyncInternalEcsBGTask(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    EcsServiceSetupResponse ecsServiceSetupResponse =
        (EcsServiceSetupResponse) executionResponse.getEcsCommandResponse();
    ContainerSetupCommandUnitExecutionData setupExecutionData = ecsServiceSetupResponse.getSetupData();

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    if (artifact == null) {
      throw new InvalidArgumentsException(Pair.of("args", "Artifact is null"));
    }

    ImageDetails imageDetails =
        artifactCollectionUtils.fetchContainerImageDetails(artifact, context.getWorkflowExecutionId());
    ContainerServiceElement containerServiceElement =
        ecsStateHelper.buildContainerServiceElement(context, setupExecutionData, imageDetails, getMaxInstances(),
            getFixedInstances(), getDesiredInstanceCount(), getResizeStrategy(), getServiceSteadyStateTimeout());

    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    ecsStateHelper.populateFromDelegateResponse(setupExecutionData, executionData, containerServiceElement);
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(ecsStateHelper.getSweepingOutputName(context, false, ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME))
            .value(containerServiceElement)
            .build());
    executionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

    ExecutionResponseBuilder builder = ExecutionResponse.builder()
                                           .stateExecutionData(context.getStateExecutionData())
                                           .executionStatus(executionStatus);

    if (ecsServiceSetupResponse.isTimeoutFailure()) {
      builder.failureTypes(TIMEOUT);
    }

    return builder.build();
  }

  private ExecutionResponse handleAsyncInternalGitTask(ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    EcsSetupStateExecutionData ecsSetupStateExecutionData =
        (EcsSetupStateExecutionData) context.getStateExecutionData();

    String activityId = ecsSetupStateExecutionData.getActivityId();
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_SERVICE_SETUP_COMMAND_ELB, logService);

    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        executionResponse.getGitCommandStatus() == GitCommandExecutionResponse.GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      executionLogCallback.saveExecutionLog("Failed to download files from Git.", CommandExecutionStatus.FAILURE);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    String logMessage = "SuccessFully Downloaded files from Git!";
    executionLogCallback.saveExecutionLog(logMessage, CommandExecutionStatus.RUNNING);

    restoreStateDataAfterGitFetch((EcsBGSetupStateExecutionData) context.getStateExecutionData());
    ecsSetupStateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    return executeEcsBGTask(context, ecsSetupStateExecutionData.getApplicationManifestMap(), activityId,
        ecsSetupStateExecutionData.getEcsSetUpDataBag());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private boolean shouldUseSpecificListenerRule(String prodListenerRuleArn, String stageListenerRuleArn) {
    if (isNotEmpty(prodListenerRuleArn) && isNotEmpty(stageListenerRuleArn)) {
      return true;
    } else if (isEmpty(prodListenerRuleArn) && isEmpty(stageListenerRuleArn)) {
      return false;
    } else {
      String emptyRuleType = "Prod";
      if (isNotEmpty(prodListenerRuleArn)) {
        emptyRuleType = "Stage";
      }
      throw new InvalidArgumentsException(
          Pair.of("Listener Rule Arn", "The " + emptyRuleType + " listener rule arn should not be empty"));
    }
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

  private EcsBGSetupStateExecutionData createStateExecutionData(String activityId,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, ExecutionContext context,
      EcsSetUpDataBag ecsSetUpDataBag) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Application app = appService.get(context.getAppId());

    return EcsBGSetupStateExecutionData.ecsBGStateExecutionDataBuilder()
        .activityId(activityId)
        .accountId(ecsSetUpDataBag.getApplication().getAccountId())
        .appId(app.getUuid())
        .commandName(ECS_SERVICE_SETUP_COMMAND_ELB)
        .taskType(GIT_FETCH_FILES_TASK)
        .applicationManifestMap(applicationManifestMap)
        .ecsSetUpDataBag(ecsSetUpDataBag)
        .roleArn(roleArn)
        .targetPort(targetPort)
        .maxInstances(maxInstances)
        .fixedInstances(fixedInstances)
        .ecsServiceName(ecsServiceName)
        .targetGroupArn(targetGroupArn)
        .useLoadBalancer(useLoadBalancer)
        .loadBalancerName(loadBalancerName)
        .targetContainerName(targetContainerName)
        .desiredInstanceCount(desiredInstanceCount)
        .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
        .resizeStrategy(resizeStrategy)
        .awsAutoScalarConfigs(awsAutoScalarConfigs)
        .prodListenerArn(prodListenerArn)
        .prodListenerRuleArn(prodListenerRuleArn)
        .stageListenerArn(stageListenerArn)
        .stageListenerRuleArn(stageListenerRuleArn)
        .stageListenerPort(stageListenerPort)
        .isUseSpecificListenerRuleArn(isUseSpecificListenerRuleArn)
        .build();
  }

  private DelegateTask createGitFetchFileAsyncTask(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String activityId) {
    Application app = context.getApp();
    Environment env = ((ExecutionContextImpl) context).getEnv();
    notNullCheck("Environment is null", env, USER);
    InfrastructureMapping infraMapping = infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    notNullCheck("InfraStructureMapping is null", infraMapping, USER);
    GitFetchFilesTaskParams fetchFilesTaskParams =
        applicationManifestUtils.createGitFetchFilesTaskParams(context, app, appManifestMap);
    fetchFilesTaskParams.setActivityId(activityId);
    fetchFilesTaskParams.setFinalState(true);
    fetchFilesTaskParams.setAppManifestKind(AppManifestKind.K8S_MANIFEST);
    fetchFilesTaskParams.setExecutionLogName("Downaload remote manifest ");

    String waitId = generateUuid();
    return DelegateTask.builder()
        .accountId(app.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infraMapping.getUuid())
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, infraMapping.getServiceId())
        .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
        .description("Fetch remote git files")
        .waitId(waitId)
        .data(TaskData.builder()
                  .async(true)
                  .taskType(GIT_FETCH_FILES_TASK.name())
                  .parameters(new Object[] {fetchFilesTaskParams})
                  .timeout(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT))
                  .build())
        .build();
  }

  public void restoreStateDataAfterGitFetch(EcsBGSetupStateExecutionData stateExecutionData) {
    this.roleArn = stateExecutionData.getRoleArn();
    this.awsAutoScalarConfigs = stateExecutionData.getAwsAutoScalarConfigs();
    this.desiredInstanceCount = stateExecutionData.getDesiredInstanceCount();
    this.ecsServiceName = stateExecutionData.getEcsServiceName();
    this.fixedInstances = stateExecutionData.getFixedInstances();
    this.loadBalancerName = stateExecutionData.getLoadBalancerName();
    this.maxInstances = stateExecutionData.getMaxInstances();
    this.serviceSteadyStateTimeout = stateExecutionData.getServiceSteadyStateTimeout();
    this.targetContainerName = stateExecutionData.getTargetContainerName();
    this.targetGroupArn = stateExecutionData.getTargetGroupArn();
    this.targetPort = stateExecutionData.getTargetPort();
    this.useLoadBalancer = stateExecutionData.isUseLoadBalancer();
    this.isUseSpecificListenerRuleArn = stateExecutionData.isUseSpecificListenerRuleArn();
    this.prodListenerArn = stateExecutionData.getProdListenerArn();
    this.prodListenerRuleArn = stateExecutionData.getProdListenerRuleArn();
    this.stageListenerArn = stateExecutionData.getStageListenerArn();
    this.stageListenerRuleArn = stateExecutionData.getStageListenerRuleArn();
    this.stageListenerPort = stateExecutionData.getStageListenerPort();
    this.resizeStrategy = stateExecutionData.getResizeStrategy();
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(prodListenerArn)) {
      invalidFields.put(PROD_LISTENER, "Prod Listener ARN name must be specified");
    }
    if (isEmpty(stageListenerArn)) {
      invalidFields.put(STAGE_LISTENER, "Stage Listener ARN must be specified");
    }
    if (isEmpty(maxInstances) && "runningInstances".equalsIgnoreCase(desiredInstanceCount)) {
      invalidFields.put("Max Instances", "Max Instances must be specified");
    }
    if (isEmpty(loadBalancerName)) {
      invalidFields.put(WorkflowServiceHelper.ELB, "Elastic Load Balancer must be specified");
    }
    return invalidFields;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
