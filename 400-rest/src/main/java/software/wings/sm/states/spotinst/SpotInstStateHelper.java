/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEPLOYMENT_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.GROUP_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.PHASE_PARAM;
import static io.harness.spotinst.model.SpotInstConstants.SETUP_COMMAND_UNIT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.sm.states.spotinst.SpotInstServiceSetup.SPOTINST_SERVICE_SETUP_COMMAND;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.SpotInstException;
import io.harness.exception.WingsException;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.beans.container.UserDataSpecification;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.impl.spotinst.SpotInstCommandRequest.SpotInstCommandRequestBuilder;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.AwsStateHelper;
import software.wings.utils.ServiceVersionConvention;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class SpotInstStateHelper {
  @Inject private AppService appService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private ActivityService activityService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AwsCommandHelper commandHelper;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private AwsStateHelper awsStateHelper;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public SpotInstSetupStateExecutionData prepareStateExecutionData(
      ExecutionContext context, SpotInstServiceSetup serviceSetup) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    Application app = appService.get(context.getAppId());
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);
    ServiceElement serviceElement = phaseElement.getServiceElement();

    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceElement.getUuid());
    if (artifact == null) {
      throw new WingsException(format("Unable to find artifact for service id: %s", serviceElement.getUuid()));
    }

    if (serviceSetup.getOlderActiveVersionCountToKeep() == null) {
      serviceSetup.setOlderActiveVersionCountToKeep(Integer.valueOf(3));
    }

    if (serviceSetup.getOlderActiveVersionCountToKeep() <= 0) {
      throw new WingsException("Value for Older Active Versions To Keep Must be > 0");
    }

    AwsAmiInfrastructureMapping awsAmiInfrastructureMapping =
        (AwsAmiInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    Activity activity = createActivity(context, artifact, serviceSetup.getStateType(), SPOTINST_SERVICE_SETUP_COMMAND,
        CommandUnitType.SPOTINST_SETUP,
        ImmutableList.of(
            new SpotinstDummyCommandUnit(SETUP_COMMAND_UNIT), new SpotinstDummyCommandUnit(DEPLOYMENT_ERROR)));

    SettingAttribute settingAttribute = settingsService.get(awsAmiInfrastructureMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> awsEncryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());

    settingAttribute = settingsService.get(awsAmiInfrastructureMapping.getSpotinstCloudProvider());
    if (settingAttribute == null) {
      throw new SpotInstException("Spotinst Cloud Provider is not present");
    }
    SpotInstConfig spotInstConfig = (SpotInstConfig) settingAttribute.getValue();

    List<EncryptedDataDetail> spotinstEncryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());

    // change ${app.name}__${service.name}__${env.name} to actual name.
    String elastiGroupNamePrefix = serviceSetup.getElastiGroupNamePrefix();

    elastiGroupNamePrefix = isBlank(elastiGroupNamePrefix)
        ? Misc.normalizeExpression(
            ServiceVersionConvention.getPrefix(app.getName(), serviceElement.getName(), env.getName()))
        : Misc.normalizeExpression(context.renderExpression(elastiGroupNamePrefix));

    String elastiGroupOriginalJson = context.renderExpression(awsAmiInfrastructureMapping.getSpotinstElastiGroupJson());
    ElastiGroup elastiGroupOriginalConfig =
        generateOriginalConfigFromJson(elastiGroupOriginalJson, serviceSetup, context);
    boolean blueGreen = serviceSetup.isBlueGreen();
    SpotInstTaskParameters spotInstTaskParameters =
        SpotInstSetupTaskParameters.builder()
            .accountId(app.getAccountId())
            .timeoutIntervalInMin(fetchTimeoutIntervalInMin(serviceSetup.getTimeoutIntervalInMin()))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .appId(app.getAppId())
            .activityId(activity.getUuid())
            .commandName(SPOTINST_SERVICE_SETUP_COMMAND)
            .elastiGroupJson(elastiGroupOriginalJson)
            .blueGreen(blueGreen)
            .elastiGroupNamePrefix(elastiGroupNamePrefix)
            .awsLoadBalancerConfigs(
                addLoadBalancerConfigAfterExpressionEvaluation(serviceSetup.getAwsLoadBalancerConfigs(), context))
            .image(artifact.getRevision())
            .userData(getBase64EncodedUserData(app.getUuid(), serviceElement.getUuid(), context))
            .awsRegion(awsAmiInfrastructureMapping.getRegion())
            .build();

    SpotInstCommandRequest commandRequest = SpotInstCommandRequest.builder()
                                                .spotInstTaskParameters(spotInstTaskParameters)
                                                .awsConfig(awsConfig)
                                                .spotInstConfig(spotInstConfig)
                                                .awsEncryptionDetails(awsEncryptedDataDetails)
                                                .spotinstEncryptionDetails(spotinstEncryptedDataDetails)
                                                .build();

    return SpotInstSetupStateExecutionData.builder()
        .envId(env.getUuid())
        .environmentType(env.getEnvironmentType())
        .appId(app.getUuid())
        .infraMappingId(awsAmiInfrastructureMapping.getUuid())
        .commandName(SPOTINST_SERVICE_SETUP_COMMAND)
        .maxInstanceCount(elastiGroupOriginalConfig.getCapacity().getMaximum())
        .useCurrentRunningInstanceCount(serviceSetup.isUseCurrentRunningCount())
        .currentRunningInstanceCount(fetchCurrentRunningCountForSetupRequest(serviceSetup))
        .serviceId(serviceElement.getUuid())
        .spotinstCommandRequest(commandRequest)
        .elastiGroupOriginalConfig(elastiGroupOriginalConfig)
        .build();
  }

  @VisibleForTesting
  List<LoadBalancerDetailsForBGDeployment> addLoadBalancerConfigAfterExpressionEvaluation(
      List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs, ExecutionContext context) {
    List<LoadBalancerDetailsForBGDeployment> loadBalancerConfigs = new ArrayList<>();

    Map<String, LoadBalancerDetailsForBGDeployment> lbMap = new HashMap<>();
    // Use a map with key as <lbName + prodPort + stagePort>, and value as actual LbConfig.
    // This will get rid of any duplicate config.
    if (isNotEmpty(awsLoadBalancerConfigs)) {
      awsLoadBalancerConfigs.forEach(awsLoadBalancerConfig -> {
        lbMap.put(getLBKey(awsLoadBalancerConfig),
            LoadBalancerDetailsForBGDeployment.builder()
                .loadBalancerName(context.renderExpression(awsLoadBalancerConfig.getLoadBalancerName()))
                .loadBalancerArn(context.renderExpression(awsLoadBalancerConfig.getLoadBalancerArn()))
                .prodListenerPort(context.renderExpression(awsLoadBalancerConfig.getProdListenerPort()))
                .stageListenerPort(context.renderExpression(awsLoadBalancerConfig.getStageListenerPort()))
                .useSpecificRules(awsLoadBalancerConfig.isUseSpecificRules())
                .prodRuleArn(context.renderExpression(awsLoadBalancerConfig.getProdRuleArn()))
                .stageRuleArn(context.renderExpression(awsLoadBalancerConfig.getStageRuleArn()))
                .build());
      });

      loadBalancerConfigs.addAll(lbMap.values());
    }

    return loadBalancerConfigs;
  }

  @NotNull
  private String getLBKey(LoadBalancerDetailsForBGDeployment awsLoadBalancerConfig) {
    return new StringBuilder(128)
        .append(awsLoadBalancerConfig.getLoadBalancerName())
        .append('_')
        .append(awsLoadBalancerConfig.getProdListenerPort())
        .append('_')
        .append(awsLoadBalancerConfig.getStageListenerPort())
        .toString();
  }

  String getBase64EncodedUserData(String appId, String serviceId, ExecutionContext context) {
    UserDataSpecification userDataSpecification = serviceResourceService.getUserDataSpecification(appId, serviceId);
    if (userDataSpecification != null && isNotEmpty(userDataSpecification.getData())) {
      String userData = userDataSpecification.getData();
      String userDataAfterEvaluation = context.renderExpression(userData);
      return BaseEncoding.base64().encode(userDataAfterEvaluation.getBytes(Charsets.UTF_8));
    }
    return null;
  }

  private ElastiGroup generateOriginalConfigFromJson(
      String elastiGroupOriginalJson, SpotInstServiceSetup serviceSetup, ExecutionContext context) {
    ElastiGroup elastiGroup = generateConfigFromJson(elastiGroupOriginalJson);
    ElastiGroupCapacity groupCapacity = elastiGroup.getCapacity();
    if (serviceSetup.isUseCurrentRunningCount()) {
      groupCapacity.setMinimum(DEFAULT_ELASTIGROUP_MIN_INSTANCES);
      groupCapacity.setMaximum(DEFAULT_ELASTIGROUP_MAX_INSTANCES);
      groupCapacity.setTarget(DEFAULT_ELASTIGROUP_TARGET_INSTANCES);
    } else {
      groupCapacity.setMinimum(renderCount(serviceSetup.getMinInstances(), context, DEFAULT_ELASTIGROUP_MIN_INSTANCES));
      groupCapacity.setMaximum(renderCount(serviceSetup.getMaxInstances(), context, DEFAULT_ELASTIGROUP_MAX_INSTANCES));
      groupCapacity.setTarget(
          renderCount(serviceSetup.getTargetInstances(), context, DEFAULT_ELASTIGROUP_TARGET_INSTANCES));
    }
    return elastiGroup;
  }

  public ElastiGroup generateConfigFromJson(String elastiGroupJson) {
    java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();

    // Map<"group": {...entire config...}>, this is elastiGroupConfig json that spotinst exposes
    Map<String, Object> jsonConfigMap = gson.fromJson(elastiGroupJson, mapType);
    Map<String, Object> elastiGroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);
    String groupConfigJson = gson.toJson(elastiGroupConfigMap);
    return gson.fromJson(groupConfigJson, ElastiGroup.class);
  }

  public ActivityBuilder generateActivityBuilder(String appName, String appId, String commandName, Type type,
      ExecutionContext executionContext, String commandType, CommandUnitType commandUnitType, Environment environment,
      List<CommandUnit> commandUnits) {
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    return Activity.builder()
        .applicationName(appName)
        .appId(appId)
        .commandName(commandName)
        .type(type)
        .commandType(commandType)
        .commandUnits(commandUnits)
        .commandUnitType(commandUnitType)
        .status(ExecutionStatus.RUNNING)
        .environmentId(environment.getUuid())
        .environmentType(environment.getEnvironmentType())
        .environmentName(environment.getName())
        .workflowType(executionContext.getWorkflowType())
        .workflowExecutionName(executionContext.getWorkflowExecutionName())
        .workflowExecutionId(executionContext.getWorkflowExecutionId())
        .workflowId(executionContext.getWorkflowId())
        .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
        .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
        .triggeredBy(TriggeredBy.builder()
                         .name(workflowStandardParams.getCurrentUser().getName())
                         .email(workflowStandardParams.getCurrentUser().getEmail())
                         .build());
  }

  public DelegateTask getDelegateTask(String accountId, String appId, TaskType taskType, String waitId, String envId,
      String infrastructureMappingId, SpotInstCommandRequest spotInstCommandRequest, EnvironmentType environmentType,
      String serviceId, boolean selectionLogsEnabled) {
    return DelegateTask.builder()
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
        .waitId(waitId)
        .tags(commandHelper.nonEmptyTag(spotInstCommandRequest.getAwsConfig()))
        .data(TaskData.builder()
                  .async(true)
                  .taskType(taskType.name())
                  .parameters(new Object[] {spotInstCommandRequest})
                  .timeout(TimeUnit.MINUTES.toMillis(generateTimeOutForDelegateTask(
                      spotInstCommandRequest.getSpotInstTaskParameters().getTimeoutIntervalInMin())))
                  .build())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, environmentType.name())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, serviceId)
        .selectionLogsTrackingEnabled(selectionLogsEnabled)
        .description("SpotInst command execution")
        .build();
  }

  public Activity createActivity(ExecutionContext executionContext, Artifact artifact, String stateType, String command,
      CommandUnitType commandUnitType, List<CommandUnit> commandUnits) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    Environment env = ((ExecutionContextImpl) executionContext).fetchRequiredEnvironment();
    ActivityBuilder activityBuilder = generateActivityBuilder(app.getName(), app.getUuid(), command, Type.Command,
        executionContext, stateType, commandUnitType, env, commandUnits);

    if (artifact != null) {
      activityBuilder.artifactName(artifact.getDisplayName()).artifactId(artifact.getUuid());
    }

    return activityService.save(activityBuilder.build());
  }

  private int fetchTimeoutIntervalInMin(Integer timeoutIntervalInMin) {
    if (timeoutIntervalInMin == null || timeoutIntervalInMin.intValue() <= 0) {
      return 5;
    }

    return timeoutIntervalInMin.intValue();
  }

  private Integer fetchCurrentRunningCountForSetupRequest(SpotInstServiceSetup serviceSetup) {
    if (!serviceSetup.isUseCurrentRunningCount()) {
      return null;
    }

    if (serviceSetup.getCurrentRunningCount() == null || serviceSetup.getCurrentRunningCount().intValue() == 0) {
      return Integer.valueOf(2);
    }

    return serviceSetup.getCurrentRunningCount();
  }

  public int generateTimeOutForDelegateTask(Integer timeoutIntervalInMin) {
    return 5 + fetchTimeoutIntervalInMin(timeoutIntervalInMin);
  }

  public SpotInstCommandRequestBuilder generateSpotInstCommandRequest(
      AwsAmiInfrastructureMapping awsAmiInfrastructureMapping, ExecutionContext context) {
    // Details for SpotInstConfig
    SettingAttribute settingAttribute = settingsService.get(awsAmiInfrastructureMapping.getSpotinstCloudProvider());
    SpotInstConfig spotInstConfig = (SpotInstConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> spotinstEncryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) spotInstConfig, context.getAppId(), context.getWorkflowExecutionId());

    // Details for AwsConfig
    settingAttribute = settingsService.get(awsAmiInfrastructureMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> awsEncryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) awsConfig, context.getAppId(), context.getWorkflowExecutionId());

    return SpotInstCommandRequest.builder()
        .awsConfig(awsConfig)
        .awsEncryptionDetails(awsEncryptedDataDetails)
        .spotInstConfig(spotInstConfig)
        .spotinstEncryptionDetails(spotinstEncryptedDataDetails);
  }

  public ElastiGroup prepareNewElastiGroupConfigForRollback(SpotInstSetupContextElement setupContextElement) {
    ElastiGroup elastiGroup = setupContextElement.getNewElastiGroupOriginalConfig() != null
        ? setupContextElement.getNewElastiGroupOriginalConfig().clone()
        : null;
    if (elastiGroup != null) {
      elastiGroup.setCapacity(ElastiGroupCapacity.builder().maximum(0).minimum(0).target(0).build());
    }
    return elastiGroup;
  }

  public ElastiGroup prepareOldElastiGroupConfigForRollback(SpotInstSetupContextElement setupContextElement) {
    return setupContextElement.getOldElastiGroupOriginalConfig() != null
        ? setupContextElement.getOldElastiGroupOriginalConfig()
        : null;
  }

  public boolean isBlueGreenWorkflow(ExecutionContext context) {
    return OrchestrationWorkflowType.BLUE_GREEN == context.getOrchestrationWorkflowType();
  }

  public Integer getTimeoutFromCommandRequest(SpotInstCommandRequest commandRequest) {
    if (commandRequest == null || commandRequest.getSpotInstTaskParameters() == null
        || commandRequest.getSpotInstTaskParameters().getTimeoutIntervalInMin() == null) {
      return 5;
    }

    return commandRequest.getSpotInstTaskParameters().getTimeoutIntervalInMin();
  }

  public int renderCount(String expr, ExecutionContext context, int defaultValue) {
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

  SpotinstTrafficShiftDataBag getDataBag(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    notNullCheck("Workflow Standard Params are null", workflowStandardParams);
    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);
    AwsAmiInfrastructureMapping infrastructureMapping =
        (AwsAmiInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    notNullCheck("Inframapping is null", infrastructureMapping);
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Aws Cloud Provider is null", settingAttribute);
    AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> awsEncryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());
    settingAttribute = settingsService.get(infrastructureMapping.getSpotinstCloudProvider());
    notNullCheck("Spotinst Cloud Provider is null", settingAttribute);
    SpotInstConfig spotinstConfig = (SpotInstConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> spotinstEncryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());
    return SpotinstTrafficShiftDataBag.builder()
        .app(app)
        .env(env)
        .infrastructureMapping(infrastructureMapping)
        .awsConfig(awsConfig)
        .awsEncryptedDataDetails(awsEncryptedDataDetails)
        .spotinstConfig(spotinstConfig)
        .spotinstEncryptedDataDetails(spotinstEncryptedDataDetails)
        .build();
  }

  void saveInstanceInfoToSweepingOutput(ExecutionContext context, List<InstanceElement> instanceElements) {
    if (isNotEmpty(instanceElements)) {
      // This sweeping element will be used by verification or other consumers.
      List<InstanceDetails> instanceDetails = awsStateHelper.generateAmInstanceDetails(instanceElements);
      boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                     .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                     .value(InstanceInfoVariables.builder()
                                                .instanceElements(instanceElements)
                                                .instanceDetails(instanceDetails)
                                                .skipVerification(skipVerification)
                                                .build())
                                     .build());
    }
  }

  public void saveInstanceInfoToSweepingOutput(ExecutionContext context, int trafficShift) {
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
            .value(InstanceInfoVariables.builder().newInstanceTrafficPercent(trafficShift).build())
            .build());
  }

  public List<LbDetailsForAlbTrafficShift> getRenderedLbDetails(
      ExecutionContext context, List<LbDetailsForAlbTrafficShift> lbDetails) {
    List<LbDetailsForAlbTrafficShift> rendered = new ArrayList<>();
    for (LbDetailsForAlbTrafficShift originalLbDetails : lbDetails) {
      rendered.add(LbDetailsForAlbTrafficShift.builder()
                       .loadBalancerName(context.renderExpression(originalLbDetails.getLoadBalancerName()))
                       .loadBalancerArn(context.renderExpression(originalLbDetails.getLoadBalancerArn()))
                       .listenerPort(context.renderExpression(originalLbDetails.getListenerPort()))
                       .listenerArn(context.renderExpression(originalLbDetails.getListenerArn()))
                       .useSpecificRule(originalLbDetails.isUseSpecificRule())
                       .ruleArn(context.renderExpression(originalLbDetails.getRuleArn()))
                       .build());
    }
    return rendered;
  }

  SweepingOutput getSetupElementFromSweepingOutput(ExecutionContext context, String prefix) {
    String sweepingOutputName = getSweepingOutputName(context, prefix);
    SweepingOutputInquiry inquiry = context.prepareSweepingOutputInquiryBuilder().name(sweepingOutputName).build();
    return sweepingOutputService.findSweepingOutput(inquiry);
  }

  @NotNull
  String getSweepingOutputName(ExecutionContext context, String prefix) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String suffix = phaseElement.getServiceElement().getUuid().trim();
    return prefix + suffix;
  }
}
