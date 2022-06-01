/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.AwsCodeDeployRequestElement.AWS_CODE_DEPLOY_REQUEST_PARAM;
import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.api.ServiceTemplateElement.Builder.aServiceTemplateElement;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.deployment.InstanceDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.AwsCodeDeployRequestElement;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.CommandStateExecutionData.Builder;
import software.wings.api.DeploymentType;
import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.command.CodeDeployCommandExecutionData;
import software.wings.beans.command.CodeDeployParams;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandMapper;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.aws.model.AwsCodeDeployS3LocationData;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsCodeDeployHelperServiceManager;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DataProvider;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;

/**
 * Created by brett on 6/22/17
 */
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AwsCodeDeployState extends State {
  public static final String ARTIFACT_S3_BUCKET_EXPRESSION = "${artifact.bucketName}";
  public static final String ARTIFACT_S3_KEY_EXPRESSION = "${artifact.key}";

  @Attributes(title = "Bucket", required = true) private String bucket;
  @Attributes(title = "Key", required = true) private String key;
  @Attributes(title = "Steady State Timeout") @DefaultValue("10") private int steadyStateTimeout = 10;

  @EnumData(enumDataProvider = CodeDeployBundleTypeProvider.class)
  @Attributes(title = "Bundle Type", required = true)
  private String bundleType;

  @Attributes(title = "Ignore ApplicationStop lifecycle event failure") private boolean ignoreApplicationStopFailures;

  @Attributes(title = "Enable Rollbacks") private boolean enableAutoRollback;

  @EnumData(enumDataProvider = CodeDeployAutoRollbackConfigurationProvider.class)
  @Attributes(title = "Rollback configuration overrides")
  private List<String> autoRollbackConfigurations;

  @EnumData(enumDataProvider = CodeDeployFileExistBehaviorProvider.class)
  @DefaultValue("DISALLOW")
  @Attributes(title = "Content options")
  private String fileExistsBehavior;

  @Attributes(title = "Command") @DefaultValue("Amazon Code Deploy") private String commandName = "Amazon Code Deploy";

  @Inject private transient AwsCodeDeployHelperServiceManager awsCodeDeployHelperServiceManager;
  @Inject private transient AwsHelperService awsHelperService;
  @Inject private transient AwsCommandHelper awsCommandHelper;
  @Inject protected transient SettingsService settingsService;
  @Inject protected transient DelegateService delegateService;
  @Inject protected transient ServiceResourceService serviceResourceService;
  @Inject protected transient ActivityService activityService;
  @Inject protected transient InfrastructureMappingService infrastructureMappingService;
  @Inject protected transient ServiceTemplateService serviceTemplateService;
  @Inject protected transient SecretManager secretManager;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private AwsStateHelper awsStateHelper;

  public AwsCodeDeployState(String name) {
    super(name, StateType.AWS_CODEDEPLOY_STATE.name());
  }

  protected AwsCodeDeployState(String name, String type) {
    super(name, type);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    if (StateType.AWS_CODEDEPLOY_ROLLBACK.name().equals(getStateType())) {
      AwsCodeDeployRequestElement codeDeployRequestElement =
          context.getContextElement(ContextElementType.PARAM, AWS_CODE_DEPLOY_REQUEST_PARAM);
      if (codeDeployRequestElement == null || codeDeployRequestElement.getOldCodeDeployParams() == null) {
        return ExecutionResponse.builder()
            .executionStatus(SKIPPED)
            .stateExecutionData(aStateExecutionData().withErrorMsg("No context found for rollback. Skipping.").build())
            .build();
      }
    }

    CommandStateExecutionData.Builder executionDataBuilder = aCommandStateExecutionData();

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);

    String envId = env.getUuid();
    Service service = serviceResourceService.getWithDetails(app.getUuid(), serviceId);
    Command command =
        serviceResourceService.getCommandByName(app.getUuid(), serviceId, envId, getCommandName()).getCommand();

    CodeDeployInfrastructureMapping infrastructureMapping =
        (CodeDeployInfrastructureMapping) infrastructureMappingService.get(
            app.getUuid(), context.fetchInfraMappingId());

    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) cloudProviderSetting.getValue(), context.getAppId(), context.getWorkflowExecutionId());

    Activity act = Activity.builder()
                       .applicationName(app.getName())
                       .environmentId(envId)
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
                       .commandUnits(serviceResourceService.getFlattenCommandUnitList(
                           app.getUuid(), serviceId, envId, command.getName()))
                       .commandType(command.getCommandUnitType().name())
                       .status(ExecutionStatus.RUNNING)
                       .build();

    act.setAppId(app.getUuid());
    Activity activity = activityService.save(act);

    executionDataBuilder.withServiceId(service.getUuid())
        .withServiceName(service.getName())
        .withAppId(app.getUuid())
        .withCommandName(getCommandName())
        .withActivityId(activity.getUuid());

    CodeDeployParams codeDeployParams = prepareCodeDeployParams(
        context, infrastructureMapping, cloudProviderSetting, encryptedDataDetails, executionDataBuilder);

    DeploymentType deploymentType = serviceResourceService.getDeploymentType(infrastructureMapping, service, null);
    CommandExecutionContext commandExecutionContext = aCommandExecutionContext()
                                                          .accountId(app.getAccountId())
                                                          .appId(app.getUuid())
                                                          .envId(envId)
                                                          .serviceName(service.getName())
                                                          .deploymentType(deploymentType.name())
                                                          .activityId(activity.getUuid())
                                                          .cloudProviderSetting(cloudProviderSetting)
                                                          .cloudProviderCredentials(encryptedDataDetails)
                                                          .codeDeployParams(codeDeployParams)
                                                          .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getAppId())
            .waitId(activity.getUuid())
            .tags(awsCommandHelper.getAwsConfigTagsFromContext(commandExecutionContext))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.COMMAND.name())
                      .parameters(new Object[] {CommandMapper.toCommandDTO(command), commandExecutionContext})
                      .timeout(getTaskTimeout())
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, infrastructureMapping.getServiceId())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Aws code deploy task execution")
            .build();

    String delegateTaskId = delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activity.getUuid()))
        .stateExecutionData(executionDataBuilder.build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  protected CodeDeployParams prepareCodeDeployParams(ExecutionContext context,
      CodeDeployInfrastructureMapping infrastructureMapping, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, Builder executionDataBuilder) {
    CodeDeployParams codeDeployParams = CodeDeployParams.builder()
                                            .applicationName(infrastructureMapping.getApplicationName())
                                            .deploymentGroupName(infrastructureMapping.getDeploymentGroup())
                                            .region(infrastructureMapping.getRegion())
                                            .deploymentConfigurationName(infrastructureMapping.getDeploymentConfig())
                                            .bucket(context.renderExpression(bucket))
                                            .key(context.renderExpression(key))
                                            .bundleType(bundleType)
                                            .enableAutoRollback(enableAutoRollback)
                                            .autoRollbackConfigurations(autoRollbackConfigurations)
                                            .fileExistsBehavior(fileExistsBehavior)
                                            .ignoreApplicationStopFailures(ignoreApplicationStopFailures)
                                            .timeout(getTimeOut())
                                            .build();
    executionDataBuilder.withCodeDeployParams(codeDeployParams);

    AwsCodeDeployS3LocationData s3LocationData =
        awsCodeDeployHelperServiceManager.listAppRevision((AwsConfig) cloudProviderSetting.getValue(),
            encryptedDataDetails, codeDeployParams.getRegion(), codeDeployParams.getApplicationName(),
            codeDeployParams.getDeploymentGroupName(), infrastructureMapping.getAppId());
    if (s3LocationData != null) {
      CodeDeployParams oldCodeDeployParams =
          CodeDeployParams.builder()
              .applicationName(codeDeployParams.getApplicationName())
              .deploymentGroupName(codeDeployParams.getDeploymentGroupName())
              .deploymentConfigurationName(codeDeployParams.getDeploymentConfigurationName())
              .region(codeDeployParams.getRegion())
              .bucket(s3LocationData.getBucket())
              .key(s3LocationData.getKey())
              .bundleType(s3LocationData.getBundleType())
              .enableAutoRollback(enableAutoRollback)
              .autoRollbackConfigurations(autoRollbackConfigurations)
              .fileExistsBehavior(fileExistsBehavior)
              .ignoreApplicationStopFailures(ignoreApplicationStopFailures)
              .timeout(getTimeOut())
              .build();
      executionDataBuilder.withOldCodeDeployParams(oldCodeDeployParams);
    }
    return codeDeployParams;
  }

  @Override
  public Integer getTimeoutMillis() {
    if (steadyStateTimeout == 0) {
      return null;
    }
    return awsStateHelper.getTimeout(steadyStateTimeout);
  }

  private int getTimeOut() {
    return (steadyStateTimeout == 0) ? 10 : steadyStateTimeout;
  }

  private long getTaskTimeout() {
    long l1 = TimeUnit.HOURS.toMillis(1);
    long l2 = TimeUnit.MINUTES.toMillis(getTimeOut());
    return Math.max(l1, l2);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    CommandStateExecutionData commandStateExecutionData = (CommandStateExecutionData) context.getStateExecutionData();
    CommandExecutionResult commandExecutionResult = (CommandExecutionResult) response.values().iterator().next();

    ExecutionStatus status =
        commandExecutionResult != null && CommandExecutionStatus.SUCCESS == commandExecutionResult.getStatus()
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(
        commandStateExecutionData.getActivityId(), commandStateExecutionData.getAppId(), status);

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);
    Key<ServiceTemplate> serviceTemplateKey =
        serviceTemplateService.getTemplateRefKeysByService(app.getUuid(), serviceId, env.getUuid()).get(0);

    CodeDeployInfrastructureMapping infrastructureMapping =
        (CodeDeployInfrastructureMapping) infrastructureMappingService.get(
            app.getUuid(), context.fetchInfraMappingId());

    InstanceElementListParam instanceElementListParam = InstanceElementListParam.builder().build();
    List<InstanceStatusSummary> instanceStatusSummaries = new ArrayList<>();
    if (commandExecutionResult != null && commandExecutionResult.getCommandExecutionData() != null) {
      CodeDeployCommandExecutionData commandExecutionData =
          (CodeDeployCommandExecutionData) commandExecutionResult.getCommandExecutionData();
      List<InstanceElement> instanceElements = new ArrayList<>();
      commandExecutionData.getInstances().forEach(instance -> {
        HostElement hostElement = HostElement.builder()
                                      .publicDns(instance.getPublicDnsName())
                                      .ip(instance.getPrivateIpAddress())
                                      .ec2Instance(instance)
                                      .instanceId(instance.getInstanceId())
                                      .build();

        Map<String, Object> contextMap = context.asMap();
        contextMap.put("host", hostElement);
        String hostName =
            awsHelperService.getHostnameFromConvention(contextMap, infrastructureMapping.getHostNameConvention());

        hostElement.setHostName(hostName);
        InstanceElement instanceElement =
            anInstanceElement()
                .uuid(instance.getInstanceId())
                .hostName(hostName)
                .displayName(instance.getPublicDnsName())
                .host(hostElement)
                .serviceTemplateElement(aServiceTemplateElement()
                                            .withUuid(serviceTemplateKey.getId().toString())
                                            .withServiceElement(phaseElement.getServiceElement())
                                            .build())
                .newInstance(true)
                .build();
        instanceElements.add(instanceElement);

        instanceStatusSummaries.add(anInstanceStatusSummary()
                                        .withInstanceElement((InstanceElement) instanceElement.cloneMin())
                                        .withStatus(ExecutionStatus.SUCCESS)
                                        .build());
      });
      instanceElementListParam = InstanceElementListParam.builder().instanceElements(instanceElements).build();
      commandStateExecutionData.setNewInstanceStatusSummaries(instanceStatusSummaries);
      commandStateExecutionData.setCodeDeployDeploymentId(commandExecutionData.getDeploymentId());
      commandStateExecutionData.setDelegateMetaInfo(commandExecutionResult.getDelegateMetaInfo());

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

    return ExecutionResponse.builder()
        .stateExecutionData(commandStateExecutionData)
        .executionStatus(status)
        .contextElement(instanceElementListParam)
        .notifyElement(instanceElementListParam)
        .build();
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (!isRollback()) {
      if (isBlank(bucket)) {
        invalidFields.put("bucket", "Bucket should not be empty");
      }
      if (isBlank(key)) {
        invalidFields.put("key", "Key should not be empty");
      }
      if (isBlank(bundleType)) {
        invalidFields.put("bundleType", "Bundle Type should not be empty");
      }
    }
    if (getCommandName() == null) {
      invalidFields.put("commandName", "Command Name should not be null");
    }

    if (steadyStateTimeout < 0) {
      invalidFields.put("steadyStateTimeout", "Steady State Timeout cannot be less than 0");
    }
    return invalidFields;
  }

  @SchemaIgnore
  public String getCommandName() {
    return commandName;
  }

  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getBundleType() {
    return bundleType;
  }

  public void setBundleType(String bundleType) {
    this.bundleType = bundleType;
  }

  public boolean isIgnoreApplicationStopFailures() {
    return ignoreApplicationStopFailures;
  }

  public void setIgnoreApplicationStopFailures(boolean ignoreApplicationStopFailures) {
    this.ignoreApplicationStopFailures = ignoreApplicationStopFailures;
  }

  public String getFileExistsBehavior() {
    return fileExistsBehavior;
  }

  public void setFileExistsBehavior(String fileExistsBehavior) {
    this.fileExistsBehavior = fileExistsBehavior;
  }

  public boolean isEnableAutoRollback() {
    return enableAutoRollback;
  }

  public void setEnableAutoRollback(boolean enableAutoRollback) {
    this.enableAutoRollback = enableAutoRollback;
  }

  public List<String> getAutoRollbackConfigurations() {
    return autoRollbackConfigurations;
  }

  public void setAutoRollbackConfigurations(List<String> autoRollbackConfigurations) {
    this.autoRollbackConfigurations = autoRollbackConfigurations;
  }

  public int getSteadyStateTimeout() {
    return steadyStateTimeout;
  }

  public void setSteadyStateTimeout(int steadyStateTimeout) {
    this.steadyStateTimeout = steadyStateTimeout;
  }

  public static class CodeDeployAutoRollbackConfigurationProvider implements DataProvider {
    @Override
    public Map<String, String> getData(String appId, Map<String, String> params) {
      return ImmutableMap.of("DEPLOYMENT_FAILURE", "Roll back when a deployment fails", "DEPLOYMENT_STOP_ON_ALARM",
          "Roll back when alarm thresholds are met", "DEPLOYMENT_STOP_ON_REQUEST", "Roll back on request");
    }
  }

  public static class CodeDeployFileExistBehaviorProvider implements DataProvider {
    @Override
    public Map<String, String> getData(String appId, Map<String, String> params) {
      return ImmutableMap.of("DISALLOW", "Fail the deployment (Default option)", "OVERWRITE", "Overwrite the content",
          "RETAIN", "Retain the content");
    }
  }

  public static class CodeDeployBundleTypeProvider implements DataProvider {
    @Override
    public Map<String, String> getData(String appId, Map<String, String> params) {
      return ImmutableMap.of("tar", "A tar archive file (tar)", "tgz", "A compressed tar archive file (tgz)", "zip",
          "A zip archive file (zip)");
    }
  }

  public static Map<String, String> loadDefaults() {
    Map<String, String> stateDefaults = new HashMap<>();
    stateDefaults.put("bucket", ARTIFACT_S3_BUCKET_EXPRESSION);
    stateDefaults.put("key", ARTIFACT_S3_KEY_EXPRESSION);
    stateDefaults.put("bundleType", "zip");
    return stateDefaults;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
