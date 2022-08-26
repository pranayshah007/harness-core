/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_TIMEOUT_MIN;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_STATE_TIMEOUT_BUFFER_MIN;
import static software.wings.service.impl.aws.model.AwsConstants.ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.sm.StateType.ECS_SERVICE_SETUP;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsSetupStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsElbConfig;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.EcsContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceSetupResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
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
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class EcsServiceSetup extends State {
  public static final String ECS_SERVICE_SETUP_COMMAND = "ECS Service Setup";

  @Getter @Setter private String roleArn;
  @Getter @Setter private String targetPort;
  @Getter @Setter private String maxInstances;
  @Getter @Setter private String fixedInstances;
  @Getter @Setter private String ecsServiceName;
  @Getter @Setter private String targetGroupArn;
  @Getter @Setter private boolean useLoadBalancer;
  @Getter @Setter private String loadBalancerName;
  @Getter @Setter private String targetContainerName;
  @Getter @Setter private String desiredInstanceCount;
  @Getter @Setter private String serviceSteadyStateTimeout;
  @Getter @Setter private ResizeStrategy resizeStrategy;
  @Getter @Setter private List<AwsAutoScalarConfig> awsAutoScalarConfigs;
  @Getter @Setter private List<AwsElbConfig> awsElbConfigs;
  @Getter @Setter private boolean isMultipleLoadBalancersFeatureFlagActive;

  @Inject private SecretManager secretManager;
  @Inject private AppService appService;
  @Inject private EcsStateHelper ecsStateHelper;
  @Inject private ActivityService activityService;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private LogService logService;

  public EcsServiceSetup(String name) {
    super(name, ECS_SERVICE_SETUP.name());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

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
  public Integer getTimeoutMillis(ExecutionContext context) {
    if (isEmpty(serviceSteadyStateTimeout)) {
      return null;
    }
    return Ints.checkedCast(TimeUnit.MINUTES.toMillis(DEFAULT_STATE_TIMEOUT_BUFFER_MIN
        + ecsStateHelper.renderTimeout(serviceSteadyStateTimeout, context, DEFAULT_AMI_ASG_TIMEOUT_MIN)));
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    boolean valuesInGit = false;
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();

    EcsSetUpDataBag dataBag = ecsStateHelper.prepareBagForEcsSetUp(context,
        ecsStateHelper.renderTimeout(serviceSteadyStateTimeout, context, DEFAULT_AMI_ASG_TIMEOUT_MIN),
        artifactCollectionUtils, serviceResourceService, infrastructureMappingService, settingsService, secretManager);

    this.isMultipleLoadBalancersFeatureFlagActive =
        featureFlagService.isEnabled(FeatureName.ECS_MULTI_LBS, context.getAccountId());
    appManifestMap = applicationManifestUtils.getApplicationManifests(context, AppManifestKind.K8S_MANIFEST);
    valuesInGit = isRemoteManifest(appManifestMap);

    Activity activity = ecsStateHelper.createActivity(
        context, ECS_SERVICE_SETUP_COMMAND, getStateType(), CommandUnitType.AWS_ECS_SERVICE_SETUP, activityService);

    if (valuesInGit) {
      return executeGitFetchFilesTask(context, appManifestMap, activity.getUuid(), dataBag);
    } else {
      return executeEcsTask(context, appManifestMap, activity.getUuid(), dataBag);
    }
  }

  public DelegateTask createGitFetchFileAsyncTask(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String activityId) {
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_SERVICE_SETUP_COMMAND, logService);

    String logMessage = "Creating a task to fetch files from git.";
    executionLogCallback.saveExecutionLog(logMessage, CommandExecutionStatus.RUNNING);
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
    fetchFilesTaskParams.setExecutionLogName("Download Remote Manifest Files");

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
                  .timeout(TimeUnit.MINUTES.toMillis(Math.max(
                      ecsStateHelper.renderTimeout(serviceSteadyStateTimeout, context, DEFAULT_AMI_ASG_TIMEOUT_MIN),
                      GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT)))
                  .build())
        .build();
  }

  private EcsSetupStateExecutionData createStateExecutionData(String activityId,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, ExecutionContext context,
      EcsSetUpDataBag ecsSetUpDataBag) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Application app = appService.get(context.getAppId());

    return EcsSetupStateExecutionData.builder()
        .activityId(activityId)
        .accountId(ecsSetUpDataBag.getApplication().getAccountId())
        .appId(app.getUuid())
        .commandName(ECS_SERVICE_SETUP_COMMAND)
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
        .isMultipleLoadBalancersFeatureFlagActive(isMultipleLoadBalancersFeatureFlagActive)
        .awsElbConfigs(awsElbConfigs)
        .serviceSteadyStateTimeout(
            ecsStateHelper.renderTimeout(serviceSteadyStateTimeout, context, DEFAULT_AMI_ASG_TIMEOUT_MIN))
        .resizeStrategy(resizeStrategy)
        .awsAutoScalarConfigs(awsAutoScalarConfigs)
        .build();
  }

  private ExecutionResponse executeGitFetchFilesTask(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, String activityId,
      EcsSetUpDataBag ecsSetUpDataBag) {
    final DelegateTask gitFetchFileTask = createGitFetchFileAsyncTask(context, applicationManifestMap, activityId);
    delegateService.queueTask(gitFetchFileTask);

    EcsSetupStateExecutionData stateExecutionData =
        createStateExecutionData(activityId, applicationManifestMap, context, ecsSetUpDataBag);
    appendDelegateTaskDetails(context, gitFetchFileTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(gitFetchFileTask.getWaitId()))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private ExecutionResponse executeEcsTask(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, String activityId,
      EcsSetUpDataBag ecsSetUpDataBag) {
    Application app = appService.get(context.getAppId());

    setUpRemoteContainerTaskAndServiceSpecIfRequired(context, ecsSetUpDataBag, log);

    EcsSetupParams ecsSetupParams = (EcsSetupParams) ecsStateHelper.buildContainerSetupParams(context,
        EcsSetupStateConfig.builder()
            .service(ecsSetUpDataBag.getService())
            .app(ecsSetUpDataBag.getApplication())
            .env(ecsSetUpDataBag.getEnvironment())
            .infrastructureMapping(ecsSetUpDataBag.getEcsInfrastructureMapping())
            .clusterName(ecsSetUpDataBag.getEcsInfrastructureMapping().getClusterName())
            .containerTask(ecsSetUpDataBag.getContainerTask())
            .roleArn(roleArn)
            .ecsServiceName(ecsServiceName)
            .imageDetails(ecsSetUpDataBag.getImageDetails())
            .loadBalancerName(loadBalancerName)
            .serviceSteadyStateTimeout(ecsSetUpDataBag.getServiceSteadyStateTimeout())
            .targetContainerName(targetContainerName)
            .serviceName(ecsSetUpDataBag.getService().getName())
            .targetGroupArn(targetGroupArn)
            .targetPort(targetPort)
            .useLoadBalancer(useLoadBalancer)
            .ecsServiceSpecification(ecsSetUpDataBag.getServiceSpecification())
            .isDaemonSchedulingStrategy(false)
            .awsAutoScalarConfigs(awsAutoScalarConfigs)
            .awsElbConfigs(awsElbConfigs)
            .isMultipleLoadBalancersFeatureFlagActive(isMultipleLoadBalancersFeatureFlagActive)
            .build());

    CommandStateExecutionData stateExecutionData =
        ecsStateHelper.getStateExecutionData(ecsSetUpDataBag, ECS_SERVICE_SETUP_COMMAND, ecsSetupParams, activityId);

    EcsSetupContextVariableHolder variables = ecsStateHelper.renderEcsSetupContextVariables(context);

    EcsServiceSetupRequest request = EcsServiceSetupRequest.builder()
                                         .ecsSetupParams(ecsSetupParams)
                                         .accountId(ecsSetUpDataBag.getApplication().getAccountId())
                                         .appId(ecsSetUpDataBag.getApplication().getUuid())
                                         .commandName(ECS_SERVICE_SETUP_COMMAND)
                                         .activityId(activityId)
                                         .awsConfig(ecsSetUpDataBag.getAwsConfig())
                                         .clusterName(ecsSetupParams.getClusterName())
                                         .region(ecsSetupParams.getRegion())
                                         .safeDisplayServiceVariables(variables.getSafeDisplayServiceVariables())
                                         .serviceVariables(variables.getServiceVariables())
                                         .timeoutErrorSupported(featureFlagService.isEnabled(
                                             TIMEOUT_FAILURE_SUPPORT, ecsSetUpDataBag.getApplication().getAccountId()))
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

  private boolean isRemoteManifest(Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    for (Map.Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      ApplicationManifest applicationManifest = entry.getValue();
      if (StoreType.Remote == applicationManifest.getStoreType()) {
        return true;
      }
    }

    return false;
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
    StateExecutionData stateExecutionData = context.getStateExecutionData();

    if (stateExecutionData instanceof EcsSetupStateExecutionData) {
      return handleAsyncInternalGitTask(context, response);
    } else if (stateExecutionData instanceof CommandStateExecutionData) {
      return handleAsyncInternalEcsTask(context, response);
    } else {
      throw new InvalidRequestException("Unhandled task type for state " + stateExecutionData.getStateName()
          + " and class " + stateExecutionData.getClass());
    }
  }

  private ExecutionResponse handleAsyncInternalGitTask(ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    EcsSetupStateExecutionData ecsSetupStateExecutionData =
        (EcsSetupStateExecutionData) context.getStateExecutionData();

    String activityId = ecsSetupStateExecutionData.getActivityId();
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_SERVICE_SETUP_COMMAND, logService);

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

    restoreStateDataAfterGitFetch((EcsSetupStateExecutionData) context.getStateExecutionData());
    ecsSetupStateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    return executeEcsTask(context, ecsSetupStateExecutionData.getApplicationManifestMap(), activityId,
        ecsSetupStateExecutionData.getEcsSetUpDataBag());
  }

  private ExecutionResponse handleAsyncInternalEcsTask(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    EcsServiceSetupResponse ecsServiceSetupResponse =
        (EcsServiceSetupResponse) executionResponse.getEcsCommandResponse();
    ContainerSetupCommandUnitExecutionData setupExecutionData = ecsServiceSetupResponse.getSetupData();
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
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
            getFixedInstances(), getDesiredInstanceCount(), getResizeStrategy(),
            ecsStateHelper.renderTimeout(serviceSteadyStateTimeout, context, DEFAULT_AMI_ASG_TIMEOUT_MIN));
    ecsStateHelper.populateFromDelegateResponse(setupExecutionData, executionData, containerServiceElement);
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(ecsStateHelper.getSweepingOutputName(context, false, ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME))
            .value(containerServiceElement)
            .build());

    executionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

    ExecutionResponseBuilder builder =
        ExecutionResponse.builder().stateExecutionData(executionData).executionStatus(executionStatus);
    if (ecsServiceSetupResponse.isTimeoutFailure()) {
      builder.failureTypes(TIMEOUT);
    }
    return builder.build();
  }

  public void setUpRemoteContainerTaskAndServiceSpecIfRequired(
      ExecutionContext executionContext, EcsSetUpDataBag ecsSetUpDataBag, Logger logger) {
    if (!(executionContext.getStateExecutionData() instanceof EcsSetupStateExecutionData)) {
      return;
    }
    EcsSetupStateExecutionData ecsSetupStateExecutionData =
        (EcsSetupStateExecutionData) executionContext.getStateExecutionData();

    if (ecsSetupStateExecutionData != null && ecsSetUpDataBag != null) {
      EcsServiceSpecification ecsServiceSpecification = getOrCreateServiceSpec(ecsSetUpDataBag);
      ContainerTask containerTask = getOrCreateTaskSpec(ecsSetUpDataBag);

      ApplicationManifest applicationManifest =
          ecsSetupStateExecutionData.getApplicationManifestMap().get(K8sValuesLocation.ServiceOverride);
      GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
      if (gitFileConfig != null
          && (gitFileConfig.getServiceSpecFilePath() != null || gitFileConfig.getTaskSpecFilePath() != null)) {
        List<GitFile> gitFiles = ecsSetupStateExecutionData.getFetchFilesResult()
                                     .getFilesFromMultipleRepo()
                                     .get("ServiceOverride")
                                     .getFiles();

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
      } else {
        log.error("Manifest does not contain the proper git file config, git fetch files response can not be read.");
        throw new InvalidRequestException("Manifest does not contain the proper git file config");
      }
    }
  }

  public EcsServiceSpecification getOrCreateServiceSpec(EcsSetUpDataBag ecsSetUpDataBag) {
    EcsServiceSpecification ecsServiceSpecification = ecsSetUpDataBag.getServiceSpecification();
    if (ecsServiceSpecification == null) {
      ecsServiceSpecification =
          EcsServiceSpecification.builder().serviceId(ecsSetUpDataBag.getService().getUuid()).build();
      ecsServiceSpecification.setAppId(ecsSetUpDataBag.application.getAppId());
      ecsServiceSpecification.resetToDefaultSpecification();
    }
    return ecsServiceSpecification;
  }

  public EcsContainerTask getOrCreateTaskSpec(EcsSetUpDataBag ecsSetUpDataBag) {
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

  public String getFileContentByPathFromGitFiles(List<GitFile> gitFiles, String filePath) {
    Optional<GitFile> gitFile = gitFiles.stream().filter(f -> f.getFilePath().equals(filePath)).findFirst();
    if (gitFile.isPresent()) {
      return gitFile.get().getFileContent();
    } else {
      throw new InvalidArgumentsException("No file with path " + filePath + " found");
    }
  }

  public void restoreStateDataAfterGitFetch(EcsSetupStateExecutionData stateExecutionData) {
    this.roleArn = stateExecutionData.getRoleArn();
    this.awsAutoScalarConfigs = stateExecutionData.getAwsAutoScalarConfigs();
    this.desiredInstanceCount = stateExecutionData.getDesiredInstanceCount();
    this.ecsServiceName = stateExecutionData.getEcsServiceName();
    this.fixedInstances = stateExecutionData.getFixedInstances();
    this.loadBalancerName = stateExecutionData.getLoadBalancerName();
    this.maxInstances = stateExecutionData.getMaxInstances();
    this.serviceSteadyStateTimeout = String.valueOf(stateExecutionData.getServiceSteadyStateTimeout());
    this.awsElbConfigs = stateExecutionData.getAwsElbConfigs();
    this.isMultipleLoadBalancersFeatureFlagActive = stateExecutionData.isMultipleLoadBalancersFeatureFlagActive();
    this.targetContainerName = stateExecutionData.getTargetContainerName();
    this.targetGroupArn = stateExecutionData.getTargetGroupArn();
    this.targetPort = stateExecutionData.getTargetPort();
    this.useLoadBalancer = stateExecutionData.isUseLoadBalancer();
    this.resizeStrategy = stateExecutionData.getResizeStrategy();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
