/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CLOUDFORMATION_CHANGE_SET;
import static io.harness.beans.FeatureName.CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.SKIP_BASED_ON_STACK_STATUSES;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_GIT;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.TaskType.CLOUD_FORMATION_TASK;
import static software.wings.beans.TaskType.FETCH_S3_FILE_TASK;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
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
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.api.cloudformation.CloudFormationOutputInfoElement;
import software.wings.api.cloudformation.CloudFormationRollbackInfoElement;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.s3.FetchS3FilesCommandParams;
import software.wings.beans.s3.FetchS3FilesExecutionResponse;
import software.wings.beans.s3.S3File;
import software.wings.beans.s3.S3FileRequest;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.helpers.ext.cloudformation.CloudFormationCompletionFlag;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest.CloudFormationCreateStackRequestBuilder;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.GitUtilsManager;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class CloudFormationCreateStackState extends CloudFormationState {
  private static final String CREATE_STACK_COMMAND_UNIT = "Create Stack";
  private static final String FETCH_FILES_COMMAND_UNIT = "Fetch Files";
  private static final String CF_PARAMETERS = "Cloud Formation parameters";
  private static final String CURRENT_DIR_PREFIX = "./";
  @Inject private transient AppService appService;
  @Inject private transient GitUtilsManager gitUtilsManager;
  @Inject private S3UriParser s3UriParser;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  @Attributes(title = "Parameters file path") @Getter @Setter protected List<String> parametersFilePaths;
  @Attributes(title = "Use parameters file") @Getter @Setter protected boolean useParametersFile;
  @Attributes(title = "Git Template Body") @Getter @Setter protected String gitTemplateBody;
  @Attributes(title = "Git Template File Path") @Getter @Setter protected String gitTemplateFilePath;
  @Attributes(title = "Should skip on reaching given stack statuses")
  @Getter
  @Setter
  protected boolean skipBasedOnStackStatus;
  @Attributes(title = "Stack status to ignore") @Getter @Setter protected List<String> stackStatusesToMarkAsSuccess;

  @Setter @JsonIgnore @SchemaIgnore private boolean fileFetched;
  @Getter @Setter private boolean specifyCapabilities;
  @Getter @Setter private List<String> capabilities;
  @Getter @Setter private boolean addTags;
  // tag list as JSON text
  @Getter @Setter private String tags;

  @JsonIgnore
  @SchemaIgnore
  public boolean isFileFetched() {
    return fileFetched;
  }

  public CloudFormationCreateStackState(String name) {
    super(name, StateType.CLOUD_FORMATION_CREATE_STACK.name());
  }

  @Override
  protected List<String> commandUnits(CloudFormationInfrastructureProvisioner provisioner) {
    List<String> commandUnits = new ArrayList<>();
    if (provisioner != null && (provisioner.provisionByGit() || useParametersFile)) {
      commandUnits.add(FETCH_FILES_COMMAND_UNIT);
    }
    commandUnits.add(mainCommandUnit());
    return commandUnits;
  }

  @Override
  protected String mainCommandUnit() {
    return CREATE_STACK_COMMAND_UNIT;
  }

  private void ensureNonEmptyStringField(String field, String fieldName) {
    if (isEmpty(field)) {
      throw new InvalidRequestException(format("Field: [%s] in provisioner is required", fieldName));
    }
  }

  @Override
  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    CloudFormationInfrastructureProvisioner provisioner = getProvisioner(context);

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;

    if (featureFlagService.isEnabled(SKIP_BASED_ON_STACK_STATUSES, context.getAccountId())) {
      if (skipBasedOnStackStatus == false || isEmpty(stackStatusesToMarkAsSuccess)) {
        stackStatusesToMarkAsSuccess = new ArrayList<>();
      }
    } else {
      stackStatusesToMarkAsSuccess = new ArrayList<>();
    }

    if (provisioner.provisionByGit() && !isFileFetched()) {
      return buildAndQueueGitCommandTask(executionContext, provisioner, activityId);
    }
    return super.executeInternal(context, activityId);
  }

  @Override
  protected ExecutionResponse buildAndQueueDelegateTask(ExecutionContextImpl executionContext,
      CloudFormationInfrastructureProvisioner provisioner, AwsConfig awsConfig, String activityId) {
    CloudFormationCreateStackRequestBuilder builder = CloudFormationCreateStackRequest.builder();

    String roleArnRendered = executionContext.renderExpression(getCloudFormationRoleArn());
    if (isEmpty(getStackStatusesToMarkAsSuccess())) {
      setStackStatusesToMarkAsSuccess(new ArrayList<>());
    }

    List<String> stackStatusesToMarkAsSuccessRendered = getStackStatusesToMarkAsSuccess()
                                                            .stream()
                                                            .map(status -> executionContext.renderExpression(status))
                                                            .collect(Collectors.toList());
    String regionRendered = executionContext.renderExpression(getRegion());
    builder.cloudFormationRoleArn(roleArnRendered).region(regionRendered);
    builder.stackStatusesToMarkAsSuccess(stackStatusesToMarkAsSuccessRendered.stream()
                                             .map(status -> StackStatus.fromValue(status))
                                             .collect(Collectors.toList()));

    if (isSpecifyCapabilities()) {
      builder.capabilities(capabilities);
    }
    if (isAddTags()) {
      builder.tags(executionContext.renderExpression(tags));
    }

    if (provisioner.provisionByUrl()) {
      if (useParametersFile && !isFileFetched()) {
        return buildAndQueueFetchS3FilesTask(executionContext, awsConfig, activityId);
      }
      ensureNonEmptyStringField(provisioner.getTemplateFilePath(), "Template Url");
      builder.createType(CLOUDFORMATION_STACK_CREATE_URL)
          .data(executionContext.renderExpression(provisioner.getTemplateFilePath()));
    } else if (provisioner.provisionByBody()) {
      String templateBody = provisioner.getTemplateBody();
      ensureNonEmptyStringField(templateBody, "Template Body");
      String renderedTemplate = executionContext.renderExpression(templateBody);
      builder.createType(CLOUDFORMATION_STACK_CREATE_BODY).data(renderedTemplate);
    } else if (provisioner.provisionByGit()) {
      String renderedGitTemplate = executionContext.renderExpression(gitTemplateBody);
      builder.createType(CLOUDFORMATION_STACK_CREATE_GIT).data(renderedGitTemplate);
    } else {
      throw new InvalidRequestException("Create type is not set on cloud provisioner");
    }
    return buildAndQueueCreateStackTask(executionContext, provisioner, awsConfig, activityId, builder);
  }

  private ExecutionResponse buildAndQueueCreateStackTask(ExecutionContextImpl executionContext,
      CloudFormationInfrastructureProvisioner provisioner, AwsConfig awsConfig, String activityId,
      CloudFormationCreateStackRequestBuilder builder) {
    try {
      Map<String, String> infrastructureVariables =
          infrastructureProvisionerService.extractTextVariables(getVariables(), executionContext);
      Map<String, String> renderedInfrastructureVariables = infrastructureVariables;

      if (EmptyPredicate.isNotEmpty(infrastructureVariables.entrySet())) {
        renderedInfrastructureVariables = infrastructureVariables.entrySet().stream().collect(Collectors.toMap(
            e
            -> {
              if (EmptyPredicate.isNotEmpty(e.getKey())) {
                return executionContext.renderExpression(e.getKey());
              }
              return e.getKey();
            },
            e -> {
              if (EmptyPredicate.isNotEmpty(e.getValue())) {
                return executionContext.renderExpression(e.getValue());
              }
              return e.getValue();
            }));
      }

      Map<String, EncryptedDataDetail> encryptedInfrastructureVariables =
          infrastructureProvisionerService.extractEncryptedTextVariables(
              getVariables(), executionContext.getAppId(), executionContext.getWorkflowExecutionId());

      Map<String, EncryptedDataDetail> renderedEncryptedInfrastructureVariables = encryptedInfrastructureVariables;
      if (EmptyPredicate.isNotEmpty(encryptedInfrastructureVariables.entrySet())) {
        renderedEncryptedInfrastructureVariables =
            encryptedInfrastructureVariables.entrySet().stream().collect(Collectors.toMap(e -> {
              if (EmptyPredicate.isNotEmpty(e.getKey())) {
                return executionContext.renderExpression(e.getKey());
              }
              return e.getKey();
            }, e -> e.getValue()));
      }

      boolean isTimeoutFailureSupported =
          featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, executionContext.getAccountId());
      builder.stackNameSuffix(getStackNameSuffix(executionContext, provisioner.getUuid()))
          .customStackName(useCustomStackName ? executionContext.renderExpression(customStackName) : StringUtils.EMPTY)
          .commandType(CloudFormationCommandType.CREATE_STACK)
          .timeoutSupported(isTimeoutFailureSupported)
          .accountId(executionContext.getApp().getAccountId())
          .appId(executionContext.getApp().getUuid())
          .activityId(activityId)
          .commandName(mainCommandUnit())
          .variables(renderedInfrastructureVariables)
          .encryptedVariables(renderedEncryptedInfrastructureVariables)
          .awsConfig(awsConfig)
          .skipWaitForResources(
              featureFlagService.isEnabled(CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES, executionContext.getAccountId()))
          .deploy(featureFlagService.isEnabled(CLOUDFORMATION_CHANGE_SET, executionContext.getAccountId()));

      CloudFormationCreateStackRequest request = builder.build();
      setTimeOutOnRequest(request);
      DelegateTask delegateTask = getCreateStackDelegateTask(executionContext, awsConfig, activityId, request);

      String delegateTaskId = delegateService.queueTaskV2(delegateTask);
      appendDelegateTaskDetails(executionContext, delegateTask);

      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(Collections.singletonList(activityId))
          .delegateTaskId(delegateTaskId)
          .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
          .build();
    } catch (Exception e) {
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(e.getMessage()).build();
    }
  }

  private DelegateTask getCreateStackDelegateTask(ExecutionContextImpl executionContext, AwsConfig awsConfig,
      String activityId, CloudFormationCreateStackRequest request) {
    return DelegateTask.builder()
        .accountId(executionContext.getApp().getAccountId())
        .waitId(activityId)
        .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getApp().getUuid())
        .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
        .description("CloudFormation create stack task execution")
        .data(TaskData.builder()
                  .async(true)
                  .taskType(CLOUD_FORMATION_TASK.name())
                  .parameters(new Object[] {request,
                      secretManager.getEncryptionDetails(
                          awsConfig, GLOBAL_APP_ID, executionContext.getWorkflowExecutionId())})
                  .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                  .build())
        .build();
  }

  private ExecutionResponse buildAndQueueGitCommandTask(
      ExecutionContextImpl executionContext, CloudFormationInfrastructureProvisioner provisioner, String activityId) {
    GitFileConfig renderedGitFileConfig =
        gitFileConfigHelperService.renderGitFileConfig(executionContext, provisioner.getGitFileConfig());
    if (renderedGitFileConfig.getFilePathList() == null) {
      renderedGitFileConfig.setFilePathList(new ArrayList<>());
    }
    if (useParametersFile) {
      getParametersFilePaths().forEach(parametersFilePath
          -> renderedGitFileConfig.getFilePathList().add(executionContext.renderExpression(parametersFilePath)));
    }

    gitTemplateFilePath = executionContext.renderExpression(renderedGitFileConfig.getFilePath());

    // To support paths like: "hello/file.json", "/hello/file.json", "./hello/file.json" and normalize
    // Do this only if `gitTemplateFilePath` is non-null and non-empty
    if (gitTemplateFilePath != null && !gitTemplateFilePath.equals("")) {
      gitTemplateFilePath = Paths.get(CURRENT_DIR_PREFIX, gitTemplateFilePath).normalize().toString();
      renderedGitFileConfig.getFilePathList().add(gitTemplateFilePath);
    }

    String sourceRepoSettingId = renderedGitFileConfig.getConnectorId();
    GitConfig gitConfig = gitUtilsManager.getGitConfig(sourceRepoSettingId);
    String branch = renderedGitFileConfig.getBranch();
    ensureNonEmptyStringField(sourceRepoSettingId, "sourceRepoSettingId");
    if (isNotEmpty(branch)) {
      gitConfig.setBranch(branch);
    }
    gitConfig.setReference(renderedGitFileConfig.getCommitId());
    gitConfigHelperService.convertToRepoGitConfig(gitConfig, renderedGitFileConfig.getRepoName());

    DelegateTask gitFetchFileTask =
        createGitFetchFileAsyncTask(executionContext, activityId, gitConfig, renderedGitFileConfig);
    return queueFetchFileTask(executionContext, activityId, gitFetchFileTask);
  }

  private ExecutionResponse buildAndQueueFetchS3FilesTask(
      ExecutionContextImpl executionContext, AwsConfig awsConfig, String activityId) {
    FetchS3FilesCommandParams fetchS3FilesCommandParams =
        createFetchS3FilesCommandParams(executionContext, awsConfig, activityId);

    DelegateTask s3FetchFileTask =
        DelegateTask.builder()
            .accountId(executionContext.getApp().getAccountId())
            .tags(isNotEmpty(awsConfig.getTag()) ? singletonList(awsConfig.getTag()) : null)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getApp().getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD,
                executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : null)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD,
                executionContext.getEnv() != null ? executionContext.getEnv().getEnvironmentType().name() : null)
            .uuid(generateUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(FETCH_S3_FILE_TASK.name())
                      .parameters(new Object[] {fetchS3FilesCommandParams})
                      .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                      .build())
            .build();

    return queueFetchFileTask(executionContext, activityId, s3FetchFileTask);
  }

  private ExecutionResponse queueFetchFileTask(
      ExecutionContextImpl executionContext, String activityId, DelegateTask fetchFileTask) {
    delegateService.queueTaskV2(fetchFileTask);
    appendDelegateTaskDetails(executionContext, fetchFileTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(fetchFileTask.getUuid()))
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  private FetchS3FilesCommandParams createFetchS3FilesCommandParams(
      ExecutionContextImpl executionContext, AwsConfig awsConfig, String activityId) {
    setParametersFilePaths(
        getParametersFilePaths().stream().map(executionContext::renderExpression).collect(Collectors.toList()));
    Map<String, List<String>> buckets = s3UriParser.getBucketsFilesMap(getParametersFilePaths());
    List<S3FileRequest> s3FileRequests = new ArrayList<>();
    buckets.forEach(
        (bucketName,
            fileKeys) -> s3FileRequests.add(S3FileRequest.builder().bucketName(bucketName).fileKeys(fileKeys).build()));

    return FetchS3FilesCommandParams.builder()
        .activityId(activityId)
        .encryptionDetails(
            secretManager.getEncryptionDetails(awsConfig, GLOBAL_APP_ID, executionContext.getWorkflowExecutionId()))
        .awsConfig(awsConfig)
        .appId(executionContext.getApp().getAppId())
        .accountId(executionContext.getApp().getAccountId())
        .executionLogName(FETCH_FILES_COMMAND_UNIT)
        .s3FileRequests(s3FileRequests)
        .build();
  }

  public DelegateTask createGitFetchFileAsyncTask(
      ExecutionContext context, String activityId, GitConfig gitConfig, GitFileConfig gitFileConfig) {
    Application app = context.getApp();
    Environment env = ((ExecutionContextImpl) context).getEnv();

    GitFetchFilesTaskParams fetchFilesTaskParams =
        createGitFetchFilesTaskParams(context, activityId, gitConfig, gitFileConfig, app);

    return DelegateTask.builder()
        .accountId(app.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env != null ? env.getUuid() : null)
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env != null ? env.getEnvironmentType().name() : null)
        .uuid(generateUuid())
        .data(TaskData.builder()
                  .async(true)
                  .taskType(GIT_FETCH_FILES_TASK.name())
                  .parameters(new Object[] {fetchFilesTaskParams})
                  .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                  .build())
        .build();
  }

  @NotNull
  private GitFetchFilesTaskParams createGitFetchFilesTaskParams(
      ExecutionContext context, String activityId, GitConfig gitConfig, GitFileConfig gitFileConfig, Application app) {
    gitConfigHelperService.renderGitConfig(context, gitConfig);
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .gitConfig(gitConfig)
                                                  .gitFileConfig(gitFileConfig)
                                                  .encryptedDataDetails(secretManager.getEncryptionDetails(
                                                      gitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId()))
                                                  .build();

    HashMap<String, GitFetchFilesConfig> gitFetchFileConfigMap = new HashMap();
    gitFetchFileConfigMap.put(CF_PARAMETERS, gitFetchFilesConfig);

    return GitFetchFilesTaskParams.builder()
        .appId(app.getAppId())
        .accountId(app.getAccountId())
        .activityId(activityId)
        .gitFetchFilesConfigMap(gitFetchFileConfigMap)
        .isFinalState(false)
        .executionLogName(FETCH_FILES_COMMAND_UNIT)
        .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, context.getAccountId()))
        .build();
  }

  @Override
  protected List<CloudFormationElement> handleResponse(
      CloudFormationCommandResponse commandResponse, ExecutionContext context) {
    CloudFormationCreateStackResponse createStackResponse = (CloudFormationCreateStackResponse) commandResponse;
    if (CommandExecutionStatus.SUCCESS == commandResponse.getCommandExecutionStatus()) {
      updateInfraMappings(commandResponse, context, provisionerId);
      CloudFormationRollbackInfo rollbackInfo = createStackResponse.getRollbackInfo();
      saveCloudFormationRollbackConfig(rollbackInfo, (ExecutionContextImpl) context, fetchResolvedAwsConfigId(context));
      Map<String, Object> outputs = ((CloudFormationCreateStackResponse) commandResponse).getCloudFormationOutputMap();
      CloudFormationOutputInfoElement outputElement =
          context.getContextElement(ContextElementType.CLOUD_FORMATION_PROVISION);
      if (outputElement == null) {
        outputElement = CloudFormationOutputInfoElement.builder().build();
      }
      if (isNotEmpty(outputs)) {
        outputElement.mergeOutputs(outputs);
      }
      ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
      Map<String, String> renderedOldStackParams = existingStackInfo.getOldStackParameters();
      if (EmptyPredicate.isNotEmpty(existingStackInfo.getOldStackParameters())) {
        renderedOldStackParams = existingStackInfo.getOldStackParameters().entrySet().stream().collect(Collectors.toMap(
            e
            -> {
              if (EmptyPredicate.isNotEmpty(e.getKey())) {
                return context.renderExpression(e.getKey());
              }
              return e.getKey();
            },
            e -> {
              if (EmptyPredicate.isNotEmpty(e.getValue())) {
                return context.renderExpression(e.getValue());
              }
              return e.getValue();
            }));
      }

      CloudFormationRollbackInfoElement rollbackElement =
          CloudFormationRollbackInfoElement.builder()
              .stackExisted(existingStackInfo.isStackExisted())
              .provisionerId(provisionerId)
              .awsConfigId(fetchResolvedAwsConfigId(context))
              .region(context.renderExpression(region))
              .stackNameSuffix(getStackNameSuffix((ExecutionContextImpl) context, provisionerId))
              .customStackName(useCustomStackName ? context.renderExpression(customStackName) : StringUtils.EMPTY)
              .skipBasedOnStackStatus(
                  ((CloudFormationCreateStackResponse) commandResponse).getRollbackInfo().isSkipBasedOnStackStatus())
              .stackStatusesToMarkAsSuccess(((CloudFormationCreateStackResponse) commandResponse)
                                                .getRollbackInfo()
                                                .getStackStatusesToMarkAsSuccess())
              .oldStackBody(context.renderExpression(existingStackInfo.getOldStackBody()))
              .oldStackParameters(renderedOldStackParams)
              .tags(rollbackInfo.getTags())
              .capabilities(rollbackInfo.getCapabilities())
              .build();
      return Arrays.asList(rollbackElement, outputElement);
    }
    return emptyList();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ResponseData responseData = response.values().iterator().next();
    if (responseData instanceof GitCommandExecutionResponse) {
      return handleResponseFromGitCommand(response, context);
    } else if (responseData instanceof FetchS3FilesExecutionResponse) {
      return handleFetchS3FilesExecutionResponse(response, context);
    } else {
      saveCreateStackCompletionFlag(context);
      return super.handleAsyncResponse(context, response);
    }
  }

  private ExecutionResponse handleResponseFromGitCommand(Map<String, ResponseData> response, ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = ((ScriptStateExecutionData) context.getStateExecutionData()).getActivityId();
    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        executionResponse.getGitCommandStatus() == GitCommandExecutionResponse.GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(executionResponse.getErrorMessage())
          .build();
    }

    GitFetchFilesFromMultipleRepoResult gitCommandResult =
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult();

    List<GitFile> files = gitCommandResult.getFilesFromMultipleRepo().get(CF_PARAMETERS).getFiles();

    CloudFormationInfrastructureProvisioner provisioner = getProvisioner(context);
    GitFileConfig renderedGitFileConfig =
        gitFileConfigHelperService.renderGitFileConfig(context, provisioner.getGitFileConfig());
    gitTemplateFilePath = context.renderExpression(renderedGitFileConfig.getFilePath());

    for (GitFile file : files) {
      if (gitTemplateFilePath != null && !gitTemplateFilePath.equals("")
          && Paths.get(CURRENT_DIR_PREFIX, gitTemplateFilePath)
                 .normalize()
                 .equals(Paths.get(CURRENT_DIR_PREFIX, file.getFilePath()).normalize())) {
        gitTemplateBody = file.getFileContent();
        break;
      }
    }
    if (EmptyPredicate.isEmpty(gitTemplateBody)) {
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage("No Template file found in Git")
          .build();
    }

    if (useParametersFile) {
      setParametersFilePaths(
          getParametersFilePaths().stream().map(context::renderExpression).collect(Collectors.toList()));
      gitCommandResult.getFilesFromMultipleRepo()
          .get(CF_PARAMETERS)
          .getFiles()
          .stream()
          .filter(gitFile -> getParametersFilePaths().contains(gitFile.getFilePath()))
          .map(GitFile::getFileContent)
          .map(this::getParametersFromJson)
          .forEach(this::addNewVariables);
    }
    setFileFetched(true);
    return executeInternal(context, ((ScriptStateExecutionData) context.getStateExecutionData()).getActivityId());
  }

  private ExecutionResponse handleFetchS3FilesExecutionResponse(
      Map<String, ResponseData> response, ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = ((ScriptStateExecutionData) context.getStateExecutionData()).getActivityId();
    FetchS3FilesExecutionResponse executionResponse =
        (FetchS3FilesExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        executionResponse.getCommandStatus() == FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      if (executionResponse.isTimeoutError()) {
        return ExecutionResponse.builder()
            .executionStatus(executionStatus)
            .failureTypes(FailureType.TIMEOUT)
            .errorMessage("Timed out while waiting for task to complete")
            .build();
      }
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(executionResponse.getErrorMessage())
          .build();
    } else {
      setParametersFilePaths(
          getParametersFilePaths().stream().map(context::renderExpression).collect(Collectors.toList()));
      Map<String, List<String>> buckets = s3UriParser.getBucketsFilesMap(getParametersFilePaths());
      executionResponse.getS3FetchFileResult().getS3Buckets().forEach(s3Bucket -> {
        if (buckets.get(s3Bucket.getName()) != null) {
          s3Bucket.getS3Files()
              .stream()
              .filter(s3File -> buckets.get(s3Bucket.getName()).contains(s3File.getFileKey()))
              .map(S3File::getFileContent)
              .map(this::getParametersFromJson)
              .forEach(this::addNewVariables);
        }
      });
    }
    setFileFetched(true);
    return executeInternal(context, ((ScriptStateExecutionData) context.getStateExecutionData()).getActivityId());
  }

  private void saveCreateStackCompletionFlag(ExecutionContext context) {
    SweepingOutputInstance cloudFormationCompletionFlag =
        getCloudFormationCompletionFlag(context, CLOUDFORMATION_COMPLETION_FLAG);
    if (cloudFormationCompletionFlag == null || cloudFormationCompletionFlag.getValue() == null) {
      sweepingOutputService.save(
          context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
              .name(getCompletionStatusFlagSweepingOutputName(CLOUDFORMATION_COMPLETION_FLAG, context))
              .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
              .build());
    }
  }

  private List<Parameter> getParametersFromJson(String parametersJson) {
    ObjectMapper mapper = new ObjectMapper();
    List<Parameter> parameters;
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    try {
      parameters = mapper.readValue(parametersJson, new TypeReference<List<Parameter>>() {});
    } catch (IOException e) {
      throw new InvalidArgumentsException("Failed to Deserialize json" + e);
    }
    return parameters;
  }

  private void addNewVariables(List<Parameter> parameters) {
    if (isNotEmpty(parameters)) {
      if (isEmpty(getVariables())) {
        setVariables(new ArrayList<>());
      }
      Set<String> variablesKeySet = getVariables().stream().map(NameValuePair::getName).collect(Collectors.toSet());
      parameters.forEach(parameter -> {
        if (!variablesKeySet.contains(parameter.getParameterKey())) {
          getVariables().add(NameValuePair.builder()
                                 .name(parameter.getParameterKey())
                                 .value(parameter.getParameterValue())
                                 .valueType("TEXT")
                                 .build());
        }
      });
    }
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
