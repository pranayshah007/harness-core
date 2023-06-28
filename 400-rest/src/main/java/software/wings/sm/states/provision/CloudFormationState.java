/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.tasks.ResponseData;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.command.Command.Builder;
import software.wings.beans.command.CommandType;
import software.wings.beans.dto.Log;
import software.wings.beans.infrastructure.CloudFormationRollbackConfig;
import software.wings.beans.infrastructure.CloudFormationRollbackConfig.CloudFormationRollbackConfigKeys;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@FieldNameConstants(innerTypeName = "CloudFormationStateKeys")
@OwnedBy(CDP)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public abstract class CloudFormationState extends State {
  protected static final String CLOUDFORMATION_COMPLETION_FLAG = "CloudFormationCompletionFlag";

  @Inject protected transient ActivityService activityService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient AppService appService;
  @Inject protected transient DelegateService delegateService;
  @Inject protected transient SecretManager secretManager;
  @Inject protected transient InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject protected transient LogService logService;
  @Inject protected transient TemplateExpressionProcessor templateExpressionProcessor;
  @Inject protected transient WingsPersistence wingsPersistence;
  @Inject protected SweepingOutputService sweepingOutputService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @FieldNameConstants.Include @Attributes(title = "Provisioner") @Getter @Setter protected String provisionerId;
  @Attributes(title = "Region")
  @DefaultValue(AWS_DEFAULT_REGION)
  @Getter
  @Setter
  protected String region = AWS_DEFAULT_REGION;
  @Attributes(title = "AwsConfigId") @Getter @Setter protected String awsConfigId;
  @Attributes(title = "Variables") @Getter @Setter private List<NameValuePair> variables;
  @Attributes(title = "CloudFormationRoleArn") @Getter @Setter private String cloudFormationRoleArn;
  @Attributes(title = "Use Custom Stack Name") @Getter @Setter protected boolean useCustomStackName;
  @Attributes(title = "Custom Stack Name") @Getter @Setter protected String customStackName;
  @Attributes(title = "Is Cloud Provider as Expression") @Getter @Setter private boolean infraCloudProviderAsExpression;
  @Attributes(title = "Cloud Provider Expression") @Getter @Setter private String infraCloudProviderExpression;
  private static final int IDSIZE = 8;
  private static final Set<Character> ALLOWED_CHARS =
      Sets.newHashSet(Lists.charactersOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-"));

  public CloudFormationState(String name, String stateType) {
    super(name, stateType);
  }
  protected abstract List<String> commandUnits(CloudFormationInfrastructureProvisioner provisioner);
  protected abstract String mainCommandUnit();
  protected abstract ExecutionResponse buildAndQueueDelegateTask(ExecutionContextImpl executionContext,
      CloudFormationInfrastructureProvisioner provisioner, AwsConfig awsConfig, String activityId);
  protected abstract List<CloudFormationElement> handleResponse(
      CloudFormationCommandResponse commandResponse, ExecutionContext context);

  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + TaskData.DEFAULT_ASYNC_CALL_TIMEOUT)
  @Override
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    return executeInternal(context, activityId);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to do
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    CloudFormationCommandExecutionResponse executionResponse =
        (CloudFormationCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);
    ExecutionResponseBuilder builder = ExecutionResponse.builder().executionStatus(executionStatus);

    if (ExecutionStatus.FAILED == executionStatus && executionResponse.isTimeoutError()) {
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .failureTypes(FailureType.TIMEOUT)
          .errorMessage("Timed out while waiting for task to complete")
          .build();
    }

    if (ExecutionStatus.SUCCESS == executionStatus) {
      List<CloudFormationElement> elements = handleResponse(executionResponse.getCommandResponse(), context);
      if (isNotEmpty(elements)) {
        elements.forEach(element -> {
          builder.contextElement(element);
          builder.notifyElement(element);
        });
      }
    }
    return builder.build();
  }

  protected CloudFormationInfrastructureProvisioner getProvisioner(ExecutionContext context) {
    InfrastructureProvisioner infrastructureProvisioner =
        infrastructureProvisionerService.get(context.getAppId(), provisionerId);

    if (!(infrastructureProvisioner instanceof CloudFormationInfrastructureProvisioner)) {
      throw new InvalidRequestException("");
    }
    return (CloudFormationInfrastructureProvisioner) infrastructureProvisioner;
  }

  protected AwsConfig getAwsConfig(String awsConfigId) {
    SettingAttribute awsSettingAttribute = settingsService.get(awsConfigId);
    notNullCheck("awsSettingAttribute", awsSettingAttribute);
    if (!(awsSettingAttribute.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("Setting attribute is of AwsConfig");
    }
    return (AwsConfig) awsSettingAttribute.getValue();
  }

  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    CloudFormationInfrastructureProvisioner cloudFormationInfrastructureProvisioner = getProvisioner(context);

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;

    AwsConfig awsConfig;
    final List<TemplateExpression> templateExpressions = getTemplateExpressions();
    if (isNotEmpty(templateExpressions)) {
      TemplateExpression configIdExpression =
          templateExpressionProcessor.getTemplateExpression(templateExpressions, "awsConfigId");
      if (configIdExpression != null) {
        SettingAttribute settingAttribute = templateExpressionProcessor.resolveSettingAttributeByNameOrId(
            context, configIdExpression, SettingVariableTypes.AWS);
        awsConfig = (AwsConfig) settingAttribute.getValue();
      } else {
        awsConfig = getAwsConfig(awsConfigId);
      }
    } else if (isInfraCloudProviderAsExpression()) {
      awsConfig = resolveInfraStructureProviderFromExpression(context);
    } else {
      awsConfig = getAwsConfig(awsConfigId);
    }
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context, awsConfig);

    return buildAndQueueDelegateTask(executionContext, cloudFormationInfrastructureProvisioner, awsConfig, activityId);
  }

  public AwsConfig resolveInfraStructureProviderFromExpression(ExecutionContext context) {
    if (isEmpty(getInfraCloudProviderExpression())) {
      throw new InvalidRequestException("Infrastructure Provider expression is set but value not provided", USER);
    }

    String expression = getInfraCloudProviderExpression();
    String renderedExpression = context.renderExpression(expression);

    if (isEmpty(renderedExpression)) {
      log.error("[EMPTY_EXPRESSION] Rendered expression is: [{}]. Original Expression: [{}], Context: [{}]",
          renderedExpression, expression, context.asMap());
      throw new InvalidRequestException("Infrastructure provider expression is invalid", USER);
    }

    SettingAttribute settingAttribute =
        settingsService.getSettingAttributeByName(context.getAccountId(), renderedExpression);

    if (settingAttribute == null) {
      log.error("[NOT_AWS_EXPRESSION] Rendered expression is: [{}]. Original Expression: [{}], Context: [{}]",
          renderedExpression, expression, context.asMap());
      throw new InvalidRequestException(
          "Infrastructure provider expression doesn't contains valid AWS configuration", USER);
    }

    return (AwsConfig) settingAttribute.getValue();
  }

  protected void setTimeOutOnRequest(CloudFormationCommandRequest request) {
    if (request != null) {
      Integer timeout = getTimeoutMillis();
      if (timeout != null) {
        request.setTimeoutInMs(timeout);
      }
    }
  }

  protected void updateInfraMappings(
      CloudFormationCommandResponse commandResponse, ExecutionContext context, String provisionerId) {
    CloudFormationCreateStackResponse createStackResponse = (CloudFormationCreateStackResponse) commandResponse;
    ScriptStateExecutionData scriptStateExecutionData = (ScriptStateExecutionData) context.getStateExecutionData();
    Log.Builder logBuilder = aLog()
                                 .appId(context.getAppId())
                                 .activityId(scriptStateExecutionData.getActivityId())
                                 .logLevel(LogLevel.INFO)
                                 .commandUnitName(mainCommandUnit())
                                 .executionResult(CommandExecutionStatus.RUNNING);

    ManagerExecutionLogCallback executionLogCallback =
        new ManagerExecutionLogCallback(logService, logBuilder, scriptStateExecutionData.getActivityId());

    if (CommandExecutionStatus.SUCCESS == commandResponse.getCommandExecutionStatus()) {
      Map<String, Object> outputs = createStackResponse.getCloudFormationOutputMap();
      if (isNotEmpty(outputs)) {
        infrastructureProvisionerService.regenerateInfrastructureMappings(
            provisionerId, context, outputs, Optional.of(executionLogCallback), Optional.of(region));
      } else {
        executionLogCallback.saveExecutionLog(
            "There are not outputs in the CF Stack. No infra mappings would be updated.");
      }
    }
    executionLogCallback.saveExecutionLog("Completed CF Create Stack", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  private String createActivity(ExecutionContext executionContext) {
    Application app = executionContext.getApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);
    Preconditions.checkNotNull(app, "No app found from executionContext");
    ActivityBuilder activityBuilder =
        Activity.builder()
            .applicationName(app.getName())
            .commandName(getName())
            .type(Type.Command)
            .workflowType(executionContext.getWorkflowType())
            .workflowExecutionName(executionContext.getWorkflowExecutionName())
            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
            .commandType(getStateType())
            .workflowExecutionId(executionContext.getWorkflowExecutionId())
            .workflowId(executionContext.getWorkflowId())
            .commandUnits(
                commandUnits(infrastructureProvisionerService.get(executionContext.getAppId(), provisionerId) != null
                        ? getProvisioner(executionContext)
                        : null)
                    .stream()
                    .map(s -> Builder.aCommand().withName(s).withCommandType(CommandType.OTHER).build())
                    .collect(Collectors.toList()))
            .status(ExecutionStatus.RUNNING)
            .triggeredBy(TriggeredBy.builder()
                             .email(workflowStandardParams.getCurrentUser().getEmail())
                             .name(workflowStandardParams.getCurrentUser().getName())
                             .build());

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      activityBuilder.environmentId(env.getUuid())
          .environmentName(env.getName())
          .environmentType(env.getEnvironmentType());
    }
    Activity activity = activityBuilder.build();
    activity.setAppId(app.getUuid());
    return activityService.save(activity).getUuid();
  }

  protected String getStackNameSuffix(ExecutionContextImpl executionContext, String provisionerId) {
    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      return getNormalizedId(provisionerId);
    }
    WorkflowStandardParams workflowStandardParams = executionContext.fetchWorkflowStandardParamsFromContext();
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);
    return getNormalizedId(env.getUuid()) + getNormalizedId(provisionerId);
  }

  protected String fetchResolvedAwsConfigId(ExecutionContext context) {
    if (isNotEmpty(getTemplateExpressions())) {
      TemplateExpression configIdExpression =
          templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), "awsConfigId");
      if (configIdExpression != null) {
        SettingAttribute settingAttribute = templateExpressionProcessor.resolveSettingAttributeByNameOrId(
            context, configIdExpression, SettingVariableTypes.AWS);
        return settingAttribute.getUuid();
      } else {
        return awsConfigId;
      }
    }
    return awsConfigId;
  }

  private String getNormalizedId(String id) {
    if (isEmpty(id)) {
      return id;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < IDSIZE && i < id.length(); i++) {
      char ch = id.charAt(i);
      sb.append(ALLOWED_CHARS.contains(ch) ? ch : '-');
    }
    return sb.toString();
  }

  protected void clearRollbackConfig(ExecutionContextImpl context) {
    Query<CloudFormationRollbackConfig> query =
        wingsPersistence.createQuery(CloudFormationRollbackConfig.class)
            .filter(CloudFormationRollbackConfigKeys.entityId, getStackNameSuffix(context, provisionerId));
    if (featureFlagService.isEnabled(FeatureName.CF_ROLLBACK_CONFIG_FILTER, context.getAccountId())) {
      query.filter(CloudFormationRollbackConfigKeys.awsConfigId, fetchResolvedAwsConfigId(context));
    }
    if (featureFlagService.isEnabled(FeatureName.CF_ROLLBACK_CUSTOM_STACK_NAME, context.getAccountId())) {
      String renderedCustomStackName =
          useCustomStackName ? context.renderExpression(customStackName) : StringUtils.EMPTY;
      query.filter(CloudFormationRollbackConfigKeys.customStackName, context.renderExpression(renderedCustomStackName));
      query.filter(CloudFormationRollbackConfigKeys.region, context.renderExpression(region));
    }
    wingsPersistence.delete(query);
  }

  protected void saveCloudFormationRollbackConfig(
      CloudFormationRollbackInfo rollbackInfo, ExecutionContextImpl context, String awsConfigId) {
    String url = null;
    String body = null;
    String createType;
    if (isNotEmpty(rollbackInfo.getUrl())) {
      createType = CLOUDFORMATION_STACK_CREATE_URL;
      url = rollbackInfo.getUrl();
    } else {
      createType = CLOUDFORMATION_STACK_CREATE_BODY;
      body = rollbackInfo.getBody();
    }
    wingsPersistence.save(CloudFormationRollbackConfig.builder()
                              .accountId(context.getAccountId())
                              .appId(context.getAppId())
                              .url(url)
                              .body(body)
                              .region(rollbackInfo.getRegion())
                              .awsConfigId(awsConfigId)
                              .customStackName(rollbackInfo.getCustomStackName())
                              .createType(createType)
                              .cloudFormationRoleArn(rollbackInfo.getCloudFormationRoleArn())
                              .variables(rollbackInfo.getVariables())
                              .workflowExecutionId(context.getWorkflowExecutionId())
                              .skipBasedOnStackStatus(rollbackInfo.isSkipBasedOnStackStatus())
                              .stackStatusesToMarkAsSuccess(rollbackInfo.getStackStatusesToMarkAsSuccess())
                              .entityId(getStackNameSuffix(context, provisionerId))
                              .capabilities(rollbackInfo.getCapabilities())
                              .tags(rollbackInfo.getTags())
                              .build());
  }

  protected SweepingOutputInstance getCloudFormationCompletionFlag(ExecutionContext context, String prefix) {
    SweepingOutputInquiry inquiry = context.prepareSweepingOutputInquiryBuilder()
                                        .name(getCompletionStatusFlagSweepingOutputName(prefix, context))
                                        .build();
    return sweepingOutputService.find(inquiry);
  }

  protected String getCompletionStatusFlagSweepingOutputName(String prefix, ExecutionContext context) {
    if (featureFlagService.isEnabled(FeatureName.CF_ROLLBACK_CUSTOM_STACK_NAME, context.getAccountId())
        && isUseCustomStackName()) {
      if (featureFlagService.isEnabled(FeatureName.CF_ROLLBACK_CONFIG_FILTER, context.getAccountId())) {
        return String.format("%s %s-%s-%s-%s", prefix, fetchResolvedAwsConfigId(context), provisionerId,
            getCustomStackName(), getRegion());
      }
      return String.format("%s %s-%s-%s", prefix, provisionerId, getCustomStackName(), getRegion());
    }
    if (featureFlagService.isEnabled(FeatureName.CF_ROLLBACK_CONFIG_FILTER, context.getAccountId())) {
      return String.format("%s %s-%s", prefix, fetchResolvedAwsConfigId(context), provisionerId);
    }
    return String.format("%s %s", prefix, provisionerId);
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> results = new HashMap<>();
    if (isEmpty(provisionerId)) {
      results.put("Provisioner", "Provisioner must be provided.");
    }
    // if more fields need to validated, please make sure templatized fields are not broken.
    return results;
  }
}
