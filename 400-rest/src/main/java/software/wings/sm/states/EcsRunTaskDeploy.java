/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.ECS_REGISTER_TASK_DEFINITION_TAGS;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.ECS_COMMAND_TASK;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.sm.StateType.ECS_RUN_TASK;

import static java.util.Collections.singletonList;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsRunTaskStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
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
import com.google.inject.Inject;
import groovy.lang.Tuple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class EcsRunTaskDeploy extends State {
  public static final String ECS_RUN_TASK_COMMAND = "Ecs Run Task Command";
  public static final String GIT_FETCH_FILES_TASK_NAME = "Download Remote Task Definition From Git";

  @Getter @Setter private String addTaskDefinition;
  @Getter @Setter private boolean skipSteadyStateCheck;
  @Getter @Setter private String runTaskFamilyName;
  @Getter @Setter private Long serviceSteadyStateTimeout;
  @Getter @Setter private String taskDefinitionJson;
  @Getter @Setter private GitFileConfig gitFileConfig;

  @Inject private SecretManager secretManager;
  @Inject private AppService appService;
  @Inject private EcsStateHelper ecsStateHelper;
  @Inject private ActivityService activityService;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private LogService logService;
  @Inject private GitConfigHelperService gitConfigHelperService;

  public EcsRunTaskDeploy(String name) {
    super(name, ECS_RUN_TASK.name());
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
    EcsRunTaskDataBag ecsRunTaskDataBag = ecsStateHelper.prepareBagForEcsRunTask(context, serviceSteadyStateTimeout,
        skipSteadyStateCheck, infrastructureMappingService, settingsService, singletonList(taskDefinitionJson),
        secretManager, runTaskFamilyName);

    Activity activity = ecsStateHelper.createActivity(context, ECS_RUN_TASK_COMMAND, getStateType(),
        CommandUnitDetails.CommandUnitType.AWS_ECS_RUN_TASK_DEPLOY, addTaskDefinition, activityService);

    if ("Remote".equalsIgnoreCase(addTaskDefinition)) {
      return executeGitTask(context, activity, ecsRunTaskDataBag);
    } else {
      return executeEcsRunTask(context, activity.getUuid(), ecsRunTaskDataBag);
    }
  }

  private ExecutionResponse executeGitTask(
      ExecutionContext context, Activity activity, EcsRunTaskDataBag ecsRunTaskDataBag) {
    gitFileConfig.setFilePathList(
        gitFileConfig.getFilePathList().stream().map(p -> context.renderExpression(p)).collect(Collectors.toList()));
    gitFileConfig.setBranch(context.renderExpression(gitFileConfig.getBranch()));
    gitFileConfig.setRepoName(context.renderExpression(gitFileConfig.getRepoName()));
    DelegateTask gitFetchFilesAsyncTask = createGitFetchFileAsyncTask(context, activity.getUuid());
    delegateService.queueTask(gitFetchFilesAsyncTask);

    EcsRunTaskStateExecutionData stateExecutionData =
        createRunTaskStateExecutionData(activity.getUuid(), context, ecsRunTaskDataBag, gitFetchFilesAsyncTask);
    appendDelegateTaskDetails(context, gitFetchFilesAsyncTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(gitFetchFilesAsyncTask.getWaitId()))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private ExecutionResponse executeEcsRunTask(
      ExecutionContext context, String activityId, EcsRunTaskDataBag ecsRunTaskDataBag) {
    setupEcsRunTaskDataBagIfRequired(context, ecsRunTaskDataBag, log);
    EcsRunTaskDeployRequest ecsRunTaskDeployRequest = createEcsRunTaskRequest(context, activityId, ecsRunTaskDataBag);

    Application application = ecsStateHelper.getApplicationFromExecutionContext(context);
    DelegateTask delegateTask = ecsStateHelper.createAndQueueDelegateTaskForEcsRunTaskDeploy(ecsRunTaskDataBag,
        infrastructureMappingService, secretManager, application, context, ecsRunTaskDeployRequest, activityId,
        delegateService, isSelectionLogsTrackingForTasksEnabled());
    appendDelegateTaskDetails(context, delegateTask);

    EcsRunTaskStateExecutionData stateExecutionData =
        createRunTaskStateExecutionData(activityId, context, ecsRunTaskDataBag, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(delegateTask.getWaitId()))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private void setupEcsRunTaskDataBagIfRequired(
      ExecutionContext executionContext, EcsRunTaskDataBag ecsRunTaskDataBag, Logger logger) {
    if (!(executionContext.getStateExecutionData() instanceof EcsRunTaskStateExecutionData)) {
      return;
    }

    EcsRunTaskStateExecutionData ecsRunTaskStateExecutionData =
        (EcsRunTaskStateExecutionData) executionContext.getStateExecutionData();

    if (ecsRunTaskStateExecutionData != null && ecsRunTaskDataBag != null) {
      GitFileConfig gitFileConfig = ecsRunTaskStateExecutionData.getGitFileConfig();
      if (gitFileConfig != null && gitFileConfig.getFilePathList() != null) {
        List<GitFile> gitFiles =
            ecsRunTaskStateExecutionData.getFetchFilesResult().getFilesFromMultipleRepo().get("Service").getFiles();

        List<String> listTaskDefFilePaths = gitFileConfig.getFilePathList();
        Map<String, String> mapFilePathsToTaskDefinitions =
            getMapFilePathToContentFromGitFiles(gitFiles, listTaskDefFilePaths);
        ecsRunTaskDataBag.setListTaskDefinitionJson(new ArrayList<>(mapFilePathsToTaskDefinitions.values()));
      } else {
        log.error("Git File Config is not created properly : ", gitFileConfig);
        throw new InvalidRequestException("Git File Config is not created properly");
      }
    }
  }

  private String getFileContentByPathFromGitFiles(List<GitFile> gitFiles, String filePath) {
    Optional<GitFile> gitFile = gitFiles.stream().filter(f -> f.getFilePath().equals(filePath)).findFirst();
    if (gitFile.isPresent()) {
      return gitFile.get().getFileContent();
    } else {
      throw new InvalidArgumentsException("No file with path " + filePath + " found");
    }
  }

  private Map<String, String> getMapFilePathToContentFromGitFiles(
      List<GitFile> gitFiles, List<String> listTaskDefFilePaths) {
    return listTaskDefFilePaths.stream()
        .map(filePath -> new Tuple<>(filePath, getFileContentByPathFromGitFiles(gitFiles, filePath)))
        .collect(Collectors.toMap(e -> e.get(0), e -> e.get(1)));
  }

  private EcsRunTaskStateExecutionData createRunTaskStateExecutionData(String activityId, ExecutionContext context,
      EcsRunTaskDataBag ecsRunTaskDataBag, DelegateTask gitFetchFilesAsyncTask) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Application app = appService.get(context.getAppId());

    EcsRunTaskStateExecutionData ecsRunTaskStateExecutionData =
        EcsRunTaskStateExecutionData.builder()
            .activityId(activityId)
            .addTaskDefinition(addTaskDefinition)
            .ecsServiceName(runTaskFamilyName)
            .skipSteadyStateCheck(skipSteadyStateCheck)
            .taskDefinitionJson(taskDefinitionJson)
            .accountId(ecsRunTaskDataBag.getApplicationAccountId())
            .appId(app.getUuid())
            .commandName(ECS_RUN_TASK_COMMAND)
            .ecsRunTaskDataBag(ecsRunTaskDataBag)
            .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
            .build();

    if (gitFetchFilesAsyncTask.getData().getParameters()[0] instanceof GitFetchFilesTaskParams) {
      GitFetchFilesTaskParams gitFetchFilesTaskParams =
          (GitFetchFilesTaskParams) gitFetchFilesAsyncTask.getData().getParameters()[0];
      GitFileConfig gitFileConfigFromGitFetchTask =
          gitFetchFilesTaskParams.getGitFetchFilesConfigMap().get(K8sValuesLocation.Service.name()).getGitFileConfig();
      ecsRunTaskStateExecutionData.setGitFileConfig(gitFileConfigFromGitFetchTask);
      ecsRunTaskStateExecutionData.setTaskType(GIT_FETCH_FILES_TASK);
    } else {
      ecsRunTaskStateExecutionData.setTaskType(ECS_COMMAND_TASK);
    }

    return ecsRunTaskStateExecutionData;
  }

  public DelegateTask createGitFetchFileAsyncTask(ExecutionContext context, String activityId) {
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_RUN_TASK_COMMAND, logService);

    String logMessage = "Creating a task to fetch files from git.";
    executionLogCallback.saveExecutionLog(logMessage, CommandExecutionStatus.RUNNING);
    Application app = context.getApp();
    Environment env = ((ExecutionContextImpl) context).getEnv();
    notNullCheck("Environment is null", env, USER);
    InfrastructureMapping infraMapping = infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    notNullCheck("InfraStructureMapping is null", infraMapping, USER);
    GitFetchFilesTaskParams fetchFilesTaskParams = createGitFetchFileTaskParams(context);
    fetchFilesTaskParams.setActivityId(activityId);
    fetchFilesTaskParams.setFinalState(true);
    fetchFilesTaskParams.setAppManifestKind(AppManifestKind.K8S_MANIFEST);
    fetchFilesTaskParams.setExecutionLogName(GIT_FETCH_FILES_TASK_NAME);

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
                  .timeout(TimeUnit.MINUTES.toMillis(
                      Math.max(serviceSteadyStateTimeout, GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT)))
                  .build())
        .build();
  }

  private EcsRunTaskDeployRequest createEcsRunTaskRequest(
      ExecutionContext context, String activityId, EcsRunTaskDataBag ecsRunTaskDataBag) {
    ecsRunTaskDataBag.setListTaskDefinitionJson(ecsRunTaskDataBag.getListTaskDefinitionJson()
                                                    .stream()
                                                    .map(t -> context.renderExpression(t))
                                                    .collect(Collectors.toList()));
    Application application = ecsStateHelper.getApplicationFromExecutionContext(context);
    EcsInfrastructureMapping ecsInfrastructureMapping = ecsStateHelper.getInfrastructureMappingFromInfraMappingService(
        infrastructureMappingService, application.getUuid(), context.fetchInfraMappingId());

    return EcsRunTaskDeployRequest.builder()
        .accountId(ecsRunTaskDataBag.getApplicationAccountId())
        .appId(ecsRunTaskDataBag.getApplicationAppId())
        .activityId(activityId)
        .cluster(ecsInfrastructureMapping.getClusterName())
        .awsConfig(ecsRunTaskDataBag.getAwsConfig())
        .region(ecsInfrastructureMapping.getRegion())
        .launchType(ecsInfrastructureMapping.getLaunchType())
        .isAssignPublicIps(ecsInfrastructureMapping.isAssignPublicIp())
        .subnetIds(ecsInfrastructureMapping.getSubnetIds())
        .securityGroupIds(ecsInfrastructureMapping.getSecurityGroupIds())
        .commandName(ECS_RUN_TASK_COMMAND)
        .listTaskDefinitionJson(ecsRunTaskDataBag.getListTaskDefinitionJson())
        .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
        .runTaskFamilyName(context.renderExpression(runTaskFamilyName))
        .skipSteadyStateCheck(skipSteadyStateCheck)
        .ecsRegisterTaskDefinitionTagsEnabled(
            featureFlagService.isEnabled(ECS_REGISTER_TASK_DEFINITION_TAGS, application.getAccountId()))
        .timeoutErrorSupported(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, application.getAccountId()))
        .build();
  }

  private Map<String, GitFetchFilesConfig> createGitFetchFileConfigMap(ExecutionContext context, Application app) {
    Map<String, GitFetchFilesConfig> gitFetchFileConfigMap = new HashMap<>();

    GitFileConfig gitFileConfig = gitFileConfigHelperService.renderGitFileConfig(context, getGitFileConfig());

    GitConfig gitConfig = settingsService.fetchGitConfigFromConnectorId(gitFileConfig.getConnectorId());
    notNullCheck("Git config not found", gitConfig);

    gitConfigHelperService.convertToRepoGitConfig(gitConfig, gitFileConfig.getRepoName());
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(gitConfig, app.getUuid(), context.getWorkflowExecutionId());

    GitFetchFilesConfig gitFetchFileConfig = GitFetchFilesConfig.builder()
                                                 .gitConfig(gitConfig)
                                                 .gitFileConfig(gitFileConfig)
                                                 .encryptedDataDetails(encryptionDetails)
                                                 .build();

    gitFetchFileConfigMap.put(K8sValuesLocation.Service.name(), gitFetchFileConfig);

    return gitFetchFileConfigMap;
  }

  private GitFetchFilesTaskParams createGitFetchFileTaskParams(ExecutionContext context) {
    Application app = context.getApp();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = createGitFetchFileConfigMap(context, app);

    ContainerServiceParams containerServiceParams = null;
    String infrastructureMappingId = context == null ? null : context.fetchInfraMappingId();

    if (infrastructureMappingId != null) {
      InfrastructureMapping infraMapping =
          infrastructureMappingService.get(context.getAppId(), infrastructureMappingId);
      if (infraMapping instanceof ContainerInfrastructureMapping) {
        containerServiceParams = containerDeploymentManagerHelper.getContainerServiceParams(
            (ContainerInfrastructureMapping) infraMapping, "", context);
      }
    }

    boolean isBindTaskFeatureSet =
        featureFlagService.isEnabled(FeatureName.BIND_FETCH_FILES_TASK_TO_DELEGATE, app.getAccountId());

    return GitFetchFilesTaskParams.builder()
        .accountId(app.getAccountId())
        .appId(app.getUuid())
        .isFinalState(false)
        .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
        .containerServiceParams(containerServiceParams)
        .isBindTaskFeatureSet(isBindTaskFeatureSet)
        .executionLogName(GIT_FETCH_FILES_TASK_NAME)
        .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, context.getAccountId()))
        .build();
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

    if ((stateExecutionData instanceof EcsRunTaskStateExecutionData)
        && ((EcsRunTaskStateExecutionData) stateExecutionData).getTaskType().equals(GIT_FETCH_FILES_TASK)) {
      return handleAsyncInternalGitTask(context, response);
    } else if ((stateExecutionData instanceof EcsRunTaskStateExecutionData)
        && ((EcsRunTaskStateExecutionData) stateExecutionData).getTaskType().equals(ECS_COMMAND_TASK)) {
      return handleAsyncInternalEcsRunTask(context, response);
    } else {
      throw new InvalidRequestException("Unhandled task type for state " + stateExecutionData.getStateName()
          + " and class " + stateExecutionData.getClass());
    }
  }

  private ExecutionResponse handleAsyncInternalGitTask(ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    EcsRunTaskStateExecutionData ecsRunTaskStateExecutionData =
        (EcsRunTaskStateExecutionData) context.getStateExecutionData();

    String activityId = ecsRunTaskStateExecutionData.getActivityId();
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_RUN_TASK_COMMAND, logService);

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

    restoreStateDataAfterGitFetch((EcsRunTaskStateExecutionData) context.getStateExecutionData());
    ecsRunTaskStateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    return executeEcsRunTask(context, activityId, ecsRunTaskStateExecutionData.getEcsRunTaskDataBag());
  }

  private ExecutionResponse handleAsyncInternalEcsRunTask(
      ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = ((EcsRunTaskStateExecutionData) context.getStateExecutionData()).getActivityId();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    EcsRunTaskStateExecutionData executionData = (EcsRunTaskStateExecutionData) context.getStateExecutionData();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    executionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

    ExecutionResponseBuilder builder =
        ExecutionResponse.builder().stateExecutionData(executionData).executionStatus(executionStatus);

    if (null != executionResponse.getEcsCommandResponse()
        && executionResponse.getEcsCommandResponse().isTimeoutFailure()) {
      builder.failureTypes(TIMEOUT);
    }

    return builder.build();
  }

  public void restoreStateDataAfterGitFetch(EcsRunTaskStateExecutionData ecsRunTaskStateExecutionData) {
    this.addTaskDefinition = ecsRunTaskStateExecutionData.getAddTaskDefinition();
    this.taskDefinitionJson = ecsRunTaskStateExecutionData.getTaskDefinitionJson();
    this.skipSteadyStateCheck = ecsRunTaskStateExecutionData.isSkipSteadyStateCheck();
    this.runTaskFamilyName = ecsRunTaskStateExecutionData.getEcsServiceName();
    this.serviceSteadyStateTimeout = ecsRunTaskStateExecutionData.getServiceSteadyStateTimeout();
    this.gitFileConfig = ecsRunTaskStateExecutionData.getGitFileConfig();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
