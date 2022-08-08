/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;
import static io.harness.validation.Validator.nullCheckForInvalidRequest;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.Log.Builder.aLog;
import static software.wings.beans.TaskType.AWS_LAMBDA_TASK;
import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.sm.StateType.AWS_LAMBDA_STATE;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.AwsLambdaContextElement;
import software.wings.api.AwsLambdaContextElement.FunctionMeta;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.PhaseElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsLambdaExecutionSummary;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.LambdaSpecification.FunctionSpecification;
import software.wings.beans.Log.Builder;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Tag;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest.AwsLambdaExecuteWfRequestBuilder;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse;
import software.wings.service.impl.aws.model.AwsLambdaFunctionParams;
import software.wings.service.impl.aws.model.AwsLambdaFunctionParams.AwsLambdaFunctionParamsBuilder;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResult;
import software.wings.service.impl.aws.model.AwsLambdaVpcConfig;
import software.wings.service.impl.aws.model.AwsLambdaVpcConfig.AwsLambdaVpcConfigBuilder;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;
import software.wings.utils.LambdaConvention;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AwsLambdaState extends State {
  @Inject protected transient SettingsService settingsService;
  @Inject protected transient ServiceResourceService serviceResourceService;
  @Inject protected transient ServiceTemplateService serviceTemplateService;
  @Inject protected transient ActivityService activityService;
  @Inject protected transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient AwsHelperService awsHelperService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient LogService logService;
  @Inject private transient ArtifactStreamService artifactStreamService;
  @Inject private transient EncryptionService encryptionService;
  @Inject private transient ServiceTemplateHelper serviceTemplateHelper;
  @Inject private transient FeatureFlagService featureFlagService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public static final String AWS_LAMBDA_COMMAND_NAME = "Deploy AWS Lambda Function";

  public static final String URL = "url";
  private static final String JENKINS = "JENKINS";
  private static final String BAMBOO = "BAMBOO";
  private static final String ARTIFACT_STRING = "artifact/";
  private static final String ARTIFACTORY = "ARTIFACTORY";
  private static final String NEXUS = "NEXUS";
  private static final String S3 = "AMAZON_S3";

  @Attributes(title = "Command")
  @DefaultValue(AWS_LAMBDA_COMMAND_NAME)
  private String commandName = AWS_LAMBDA_COMMAND_NAME;

  @Attributes(title = "Lambda Function Alias", required = true)
  @DefaultValue("${env.name}")
  @SchemaIgnore
  private List<String> aliases = new ArrayList<>();

  @Getter @Setter private List<Tag> tags;

  private static Pattern wildCharPattern = Pattern.compile("[_+*/\\\\ &$|\"']");

  public AwsLambdaState(String name) {
    super(name, AWS_LAMBDA_STATE.name());
  }

  protected AwsLambdaState(String name, String type) {
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
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncResponseInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private ExecutionResponse handleAsyncResponseInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();

    AwsLambdaExecuteWfResponse wfResponse = (AwsLambdaExecuteWfResponse) response.values().iterator().next();
    activityService.updateStatus(activityId, context.getAppId(), wfResponse.getExecutionStatus());

    CommandStateExecutionData stateExecutionData = (CommandStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setDelegateMetaInfo(wfResponse.getDelegateMetaInfo());

    updateAwsLambdaExecutionSummaries(context, wfResponse);
    List<FunctionMeta> functionMetas = emptyList();
    List<AwsLambdaFunctionResult> functionResults = wfResponse.getFunctionResults();
    if (isNotEmpty(functionResults)) {
      functionMetas = functionResults.stream()
                          .filter(AwsLambdaFunctionResult::isSuccess)
                          .map(AwsLambdaFunctionResult::getFunctionMeta)
                          .collect(toList());
    }

    AwsConfig awsConfig = wfResponse.getAwsConfig();
    String region = wfResponse.getRegion();
    stateExecutionData.setAliases(aliases);
    stateExecutionData.setTags(tags);
    stateExecutionData.setLambdaFunctionMetaList(functionMetas);
    AwsLambdaContextElement awsLambdaContextElement = AwsLambdaContextElement.builder()
                                                          .awsConfig(awsConfig)
                                                          .aliases(aliases)
                                                          .tags(tags)
                                                          .region(region)
                                                          .functionArns(functionMetas)
                                                          .build();

    return ExecutionResponse.builder()
        .stateExecutionData(stateExecutionData)
        .executionStatus(wfResponse.getExecutionStatus())
        .contextElement(awsLambdaContextElement)
        .notifyElement(awsLambdaContextElement)
        .build();
  }

  private void updateAwsLambdaExecutionSummaries(ExecutionContext context, AwsLambdaExecuteWfResponse wfResponse) {
    try {
      if (!AWS_LAMBDA_STATE.name().equals(getStateType())) {
        return;
      }

      List<AwsLambdaFunctionResult> functionResults = wfResponse.getFunctionResults();
      if (isEmpty(functionResults)) {
        return;
      }

      List<AwsLambdaExecutionSummary> awsLambdaExecutionSummaries = new ArrayList<>();

      for (AwsLambdaFunctionResult functionResult : functionResults) {
        AwsLambdaExecutionSummary summary = AwsLambdaExecutionSummary.builder()
                                                .success(functionResult.isSuccess())
                                                .functionMeta(functionResult.getFunctionMeta())
                                                .build();
        awsLambdaExecutionSummaries.add(summary);
      }
      workflowExecutionService.refreshAwsLambdaExecutionSummary(
          context.getWorkflowExecutionId(), awsLambdaExecutionSummaries);
    } catch (Exception ex) {
      log.info("Exception while updating awsLambda execution summaries", ex);
    }
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    notNullCheck("env", env, USER);

    String envId = env.getUuid();
    Service service = serviceResourceService.getWithDetails(app.getUuid(), serviceId);
    Command command =
        serviceResourceService.getCommandByName(app.getUuid(), serviceId, envId, getCommandName()).getCommand();

    AwsLambdaInfraStructureMapping infrastructureMapping =
        (AwsLambdaInfraStructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    if (infrastructureMapping == null) {
      throw new InvalidRequestException(format("No infra-mapping found for id: [%s]", context.fetchInfraMappingId()));
    }

    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    String region = infrastructureMapping.getRegion();

    List<CommandUnit> commandUnitList =
        serviceResourceService.getFlattenCommandUnitList(app.getUuid(), serviceId, envId, command.getName());
    ActivityBuilder activityBuilder =
        Activity.builder()
            .applicationName(app.getName())
            .environmentId(envId)
            .infrastructureDefinitionId(infrastructureMapping.getInfrastructureDefinitionId())
            .environmentName(env.getName())
            .environmentType(env.getEnvironmentType())
            .serviceId(service.getUuid())
            .serviceName(service.getName())
            .commandName(command.getName())
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
            .triggeredBy(TriggeredBy.builder()
                             .email(workflowStandardParams.getCurrentUser().getEmail())
                             .name(workflowStandardParams.getCurrentUser().getName())
                             .build());

    Artifact artifact = getArtifact(app.getUuid(), serviceId, context.getWorkflowExecutionId(), envId,
        (DeploymentExecutionContext) context, infrastructureMapping.getInfrastructureDefinitionId());
    if (artifact == null) {
      throw new WingsException(format("Unable to find artifact for service %s", service.getName()));
    }

    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());

    activityBuilder.artifactStreamId(artifactStream.getUuid())
        .artifactStreamName(artifactStream.getSourceName())
        .artifactName(artifact.getDisplayName())
        .artifactId(artifact.getUuid());
    activityBuilder.artifactId(artifact.getUuid()).artifactName(artifact.getDisplayName());

    Activity build = activityBuilder.build();
    build.setAppId(app.getUuid());
    Activity activity = activityService.save(build);

    Builder logBuilder = aLog()
                             .appId(activity.getAppId())
                             .activityId(activity.getUuid())
                             .logLevel(LogLevel.INFO)
                             .commandUnitName(commandUnitList.get(0).getName())
                             .executionResult(CommandExecutionStatus.RUNNING);

    logService.batchedSave(singletonList(logBuilder.but().logLine("Begin command execution.").build()));

    CommandStateExecutionData.Builder executionDataBuilder = aCommandStateExecutionData()
                                                                 .withServiceId(service.getUuid())
                                                                 .withServiceName(service.getName())
                                                                 .withAppId(app.getUuid())
                                                                 .withCommandName(getCommandName())
                                                                 .withArtifactId(artifact.getUuid())
                                                                 .withActivityId(activity.getUuid());

    LambdaSpecification specification = serviceResourceService.getLambdaSpecification(app.getUuid(), service.getUuid());

    nullCheckForInvalidRequest(specification, "Missing lambda function specification in service", USER);

    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    if (!ArtifactStreamType.CUSTOM.name().equalsIgnoreCase(artifactStreamAttributes.getArtifactStreamType())) {
      artifactStreamAttributes.setServerSetting(settingsService.get(artifactStream.getSettingId()));
      artifactStreamAttributes.setArtifactServerEncryptedDataDetails(secretManager.getEncryptionDetails(
          (EncryptableSetting) artifactStreamAttributes.getServerSetting().getValue(), context.getAppId(),
          context.getWorkflowExecutionId()));
    }

    artifactStreamAttributes.setMetadata(artifact.getMetadata());
    artifactStreamAttributes.setArtifactStreamId(artifactStream.getUuid());
    artifactStreamAttributes.setArtifactName(artifact.getDisplayName());
    artifactStreamAttributes.setMetadataOnly(onlyMetaForArtifactType(artifactStream));
    artifactStreamAttributes.getMetadata().put(
        ArtifactMetadataKeys.artifactFileName, artifactFileNameForSource(artifact, artifactStreamAttributes));
    artifactStreamAttributes.getMetadata().put(
        ArtifactMetadataKeys.artifactPath, artifactPathForSource(artifact, artifactStreamAttributes));

    if (isEmpty(specification.getFunctions())) {
      logService.batchedSave(singletonList(logBuilder.but().logLine("No Lambda function to deploy.").build()));
      activityService.updateStatus(activity.getUuid(), activity.getAppId(), SUCCESS);
      List<FunctionMeta> functionArns = new ArrayList<>();
      AwsLambdaContextElement awsLambdaContextElement = AwsLambdaContextElement.builder()
                                                            .awsConfig((AwsConfig) cloudProviderSetting.getValue())
                                                            .region(region)
                                                            .functionArns(functionArns)
                                                            .build();
      return ExecutionResponse.builder()
          .stateExecutionData(executionDataBuilder.build())
          .contextElement(awsLambdaContextElement)
          .notifyElement(awsLambdaContextElement)
          .executionStatus(SUCCESS)
          .build();
    } else {
      AwsLambdaExecuteWfRequest wfRequest = constructLambdaWfRequestParams(specification, context, context.getAppId(),
          env.getUuid(), infrastructureMapping, (AwsConfig) cloudProviderSetting.getValue(), region, artifact,
          app.getAccountId(), artifactStreamAttributes, activity.getUuid());
      DelegateTask delegateTask =
          DelegateTask.builder()
              .accountId(app.getAccountId())
              .waitId(activity.getUuid())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
              .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
              .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
              .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMapping.getUuid())
              .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, infrastructureMapping.getServiceId())
              .tags(isNotEmpty(wfRequest.getAwsConfig().getTag()) ? singletonList(wfRequest.getAwsConfig().getTag())
                                                                  : null)
              .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
              .description("Aws Lambda task execution")
              .data(TaskData.builder()
                        .async(true)
                        .taskType(AWS_LAMBDA_TASK.name())
                        .parameters(new Object[] {wfRequest})
                        .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                        .build())
              .build();
      String delegateTaskId = delegateService.queueTask(delegateTask);
      appendDelegateTaskDetails(context, delegateTask);
      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(singletonList(activity.getUuid()))
          .delegateTaskId(delegateTaskId)
          .stateExecutionData(executionDataBuilder.build())
          .build();
    }
  }

  /*
   * returns Artifactpath for source
   * */
  public String artifactPathForSource(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    switch (artifactStreamAttributes.getArtifactStreamType()) {
      case JENKINS:
        if (artifactStreamAttributes.getArtifactPaths().isEmpty()) {
          throw new InvalidRequestException("ArtifactPath missing, reqired for only-meta feature!");
        }
        return ARTIFACT_STRING + artifactStreamAttributes.getArtifactPaths().get(0);
      case BAMBOO:
        if (artifact.getArtifactFileMetadata().isEmpty()) {
          throw new InvalidRequestException("artifact url is required");
        }
        return artifact.getArtifactFileMetadata().get(0).getUrl();
      case ARTIFACTORY:
        return artifactStreamAttributes.getMetadata().get("artifactPath");
      default:
        return artifactStreamAttributes.getMetadata().get(URL);
    }
  }

  /*
   * returns ArtifactFileName for source
   * */
  public String artifactFileNameForSource(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    switch (artifactStreamAttributes.getArtifactStreamType()) {
      case JENKINS:
        return artifact.getDisplayName();
      case BAMBOO:
        if (artifactStreamAttributes.getArtifactPaths().isEmpty()) {
          throw new InvalidRequestException("ArtifactPath is missing!");
        }
        return artifactStreamAttributes.getArtifactPaths().get(0);
      case ARTIFACTORY:
        return artifactStreamAttributes.getMetadata().get("buildNo");
      case NEXUS:
        return artifact.getDisplayName().substring(artifact.getArtifactSourceName().length());
      default:
        return artifact.getDisplayName();
    }
  }

  private boolean onlyMetaForArtifactType(ArtifactStream artifactStream) {
    switch (artifactStream.getArtifactStreamType()) {
      case JENKINS:
      case BAMBOO:
      case ARTIFACTORY:
      case NEXUS:
      case S3:
        return metadataOnlyBehindFlag(
            featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly());
      default:
        return false;
    }
  }

  protected List<String> getEvaluatedAliases(ExecutionContext context) {
    if (isNotEmpty(aliases)) {
      return aliases.stream().map(context::renderExpression).collect(toList());
    }
    return emptyList();
  }

  private AwsLambdaExecuteWfRequest constructLambdaWfRequestParams(LambdaSpecification specification,
      ExecutionContext context, String appId, String envId, AwsLambdaInfraStructureMapping infrastructureMapping,
      AwsConfig awsConfig, String region, Artifact artifact, String accountId,
      ArtifactStreamAttributes artifactStreamAttributes, String activityId) {
    AwsLambdaExecuteWfRequestBuilder wfRequestBuilder = AwsLambdaExecuteWfRequest.builder();
    wfRequestBuilder.awsConfig(awsConfig);
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, appId, context.getWorkflowExecutionId());
    wfRequestBuilder.encryptionDetails(encryptionDetails);
    wfRequestBuilder.region(region);
    wfRequestBuilder.accountId(accountId);
    wfRequestBuilder.appId(appId);
    wfRequestBuilder.activityId(activityId);
    wfRequestBuilder.artifactFiles(artifact.getArtifactFiles());
    wfRequestBuilder.commandName(getCommandName());
    wfRequestBuilder.artifactStreamAttributes(artifactStreamAttributes);
    wfRequestBuilder.roleArn(infrastructureMapping.getRole());
    wfRequestBuilder.evaluatedAliases(getEvaluatedAliases(context));

    String serviceTemplateId = serviceTemplateHelper.fetchServiceTemplateId(infrastructureMapping);
    Map<String, String> serviceVariables =
        serviceTemplateService
            .computeServiceVariables(appId, envId, serviceTemplateId, context.getWorkflowExecutionId(), OBTAIN_VALUE)
            .stream()
            .filter(serviceVariable -> ServiceVariableType.ARTIFACT != serviceVariable.getType())
            .collect(
                Collectors.toMap(ServiceVariable::getName, sv -> context.renderExpression(new String(sv.getValue()))));
    wfRequestBuilder.serviceVariables(serviceVariables);
    wfRequestBuilder.lambdaVpcConfig(getLambdaVpcConfig(infrastructureMapping));

    List<AwsLambdaFunctionParams> functionParams = new ArrayList<>();
    List<FunctionSpecification> functions = specification.getFunctions();
    if (isNotEmpty(functions)) {
      functions.forEach(functionSpecification -> {
        AwsLambdaFunctionParamsBuilder functionParamsBuilder = AwsLambdaFunctionParams.builder();
        functionParamsBuilder.key(context.renderExpression(artifact.getMetadata().get("key")));
        functionParamsBuilder.bucket(context.renderExpression(artifact.getMetadata().get("bucketName")));
        String functionName = context.renderExpression(functionSpecification.getFunctionName());
        functionName = LambdaConvention.normalizeFunctionName(functionName);
        functionParamsBuilder.functionName(functionName);
        functionParamsBuilder.handler(context.renderExpression(functionSpecification.getHandler()));
        functionParamsBuilder.runtime(context.renderExpression(functionSpecification.getRuntime()));
        functionParamsBuilder.memory(functionSpecification.getMemorySize());
        functionParamsBuilder.timeout(functionSpecification.getTimeout());
        functionParamsBuilder.functionTags(getFunctionTags(context));
        functionParams.add(functionParamsBuilder.build());
      });
    }
    wfRequestBuilder.functionParams(functionParams);
    return wfRequestBuilder.build();
  }

  protected Map<String, String> getFunctionTags(ExecutionContext context) {
    Map<String, String> functionTags = new HashMap<>();
    if (isNotEmpty(tags)) {
      tags.forEach(tag -> { functionTags.put(tag.getKey(), context.renderExpression(tag.getValue())); });
    }
    return functionTags;
  }

  private AwsLambdaVpcConfig getLambdaVpcConfig(AwsLambdaInfraStructureMapping infrastructureMapping) {
    String vpcId = infrastructureMapping.getVpcId();
    AwsLambdaVpcConfigBuilder builder = AwsLambdaVpcConfig.builder();
    if (vpcId != null) {
      builder.vpcId(vpcId);
      List<String> subnetIds = infrastructureMapping.getSubnetIds();
      List<String> securityGroupIds = infrastructureMapping.getSecurityGroupIds();
      if (!securityGroupIds.isEmpty() && !subnetIds.isEmpty()) {
        builder.subnetIds(subnetIds);
        builder.securityGroupIds(securityGroupIds);
      } else {
        throw new InvalidRequestException("At least one security group and one subnet must be provided");
      }
    }
    return builder.build();
  }

  /**
   * Gets artifact.
   *
   * @param appId                  the app id
   * @param serviceId              the service id
   * @param workflowExecutionId    the workflow execution id
   * @param envId                  the env id
   * @param deploymentExecutionContext the deploymentExecutionContext
   * @param infrastructureDefinitionId the infrastructure definition id
   * @return the artifact
   */
  protected Artifact getArtifact(String appId, String serviceId, String workflowExecutionId, String envId,
      DeploymentExecutionContext deploymentExecutionContext, String infrastructureDefinitionId) {
    return deploymentExecutionContext.getDefaultArtifactForService(serviceId);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  public String getCommandName() {
    return commandName;
  }

  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  @SchemaIgnore
  public List<String> getAliases() {
    return aliases;
  }

  public void setAliases(List<String> aliases) {
    this.aliases = aliases;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
