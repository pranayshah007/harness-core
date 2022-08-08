/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.sm.StateType.ECS_STEADY_STATE_CHECK;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.BreakDependencyOn;
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
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.ScriptStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.container.EcsSteadyStateCheckParams;
import software.wings.beans.container.EcsSteadyStateCheckResponse;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EcsSteadyStateCheck extends State {
  public static final String ECS_STEADY_STATE_CHECK_COMMAND_NAME = "ECS Steady State Check";

  @Inject private transient AppService appService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient ActivityService activityService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient FeatureFlagService featureFlagService;
  @Inject private transient ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Attributes(title = "Ecs Service") @Getter @Setter private String ecsServiceName;

  public EcsSteadyStateCheck(String name) {
    super(name, ECS_STEADY_STATE_CHECK.name());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      Application app = appService.get(context.getAppId());
      Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
      InfrastructureMapping infrastructureMapping =
          infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
      if (!(infrastructureMapping instanceof EcsInfrastructureMapping)) {
        throw new InvalidRequestException(
            String.format("Infrmapping with id: %s is not Ecs inframapping", context.fetchInfraMappingId()));
      }
      EcsInfrastructureMapping ecsInfrastructureMapping = (EcsInfrastructureMapping) infrastructureMapping;
      Activity activity = createActivity(context);
      AwsConfig awsConfig = getAwsConfig(ecsInfrastructureMapping.getComputeProviderSettingId());
      AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context, awsConfig);
      EcsSteadyStateCheckParams params =
          EcsSteadyStateCheckParams.builder()
              .appId(app.getUuid())
              .region(ecsInfrastructureMapping.getRegion())
              .accountId(app.getAccountId())
              .timeoutInMs(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
              .activityId(activity.getUuid())
              .commandName(ECS_STEADY_STATE_CHECK_COMMAND_NAME)
              .clusterName(ecsInfrastructureMapping.getClusterName())
              .serviceName(context.renderExpression(ecsServiceName))
              .awsConfig(awsConfig)
              .encryptionDetails(
                  secretManager.getEncryptionDetails(awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId()))
              .timeoutErrorSupported(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, app.getAccountId()))
              .build();
      DelegateTask delegateTask =
          DelegateTask.builder()
              .accountId(app.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
              .waitId(activity.getUuid())
              .tags(isNotEmpty(params.getAwsConfig().getTag()) ? singletonList(params.getAwsConfig().getTag()) : null)
              .data(TaskData.builder()
                        .async(true)
                        .taskType(TaskType.ECS_STEADY_STATE_CHECK_TASK.name())
                        .parameters(new Object[] {params})
                        .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                        .build())
              .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
              .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
              .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
              .description("ECS Steady state check task execution")
              .build();
      String delegateTaskId = delegateService.queueTask(delegateTask);

      appendDelegateTaskDetails(context, delegateTask);
      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(singletonList(activity.getUuid()))
          .stateExecutionData(ScriptStateExecutionData.builder().activityId(activity.getUuid()).build())
          .delegateTaskId(delegateTaskId)
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      String appId = workflowStandardParams.getAppId();
      String activityId = response.keySet().iterator().next();
      EcsSteadyStateCheckResponse ecsSteadyStateCheckResponse =
          (EcsSteadyStateCheckResponse) response.values().iterator().next();
      activityService.updateStatus(activityId, appId, ecsSteadyStateCheckResponse.getExecutionStatus());
      ScriptStateExecutionData stateExecutionData = (ScriptStateExecutionData) context.getStateExecutionData();
      stateExecutionData.setStatus(ecsSteadyStateCheckResponse.getExecutionStatus());
      stateExecutionData.setDelegateMetaInfo(ecsSteadyStateCheckResponse.getDelegateMetaInfo());

      List<InstanceStatusSummary> instanceStatusSummaries = containerDeploymentManagerHelper.getInstanceStatusSummaries(
          context, ecsSteadyStateCheckResponse.getContainerInfoList());
      List<InstanceElement> instanceElements =
          instanceStatusSummaries.stream().map(InstanceStatusSummary::getInstanceElement).collect(toList());
      InstanceElementListParam instanceElementListParam =
          InstanceElementListParam.builder().instanceElements(instanceElements).build();

      ExecutionResponseBuilder builder = ExecutionResponse.builder()
                                             .executionStatus(ecsSteadyStateCheckResponse.getExecutionStatus())
                                             .stateExecutionData(context.getStateExecutionData())
                                             .contextElement(instanceElementListParam)
                                             .notifyElement(instanceElementListParam);

      if (ecsSteadyStateCheckResponse.isTimeoutFailure()) {
        builder.failureTypes(TIMEOUT);
      }

      return builder.build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + DEFAULT_ASYNC_CALL_TIMEOUT)
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  private AwsConfig getAwsConfig(String awsConfigId) {
    SettingAttribute awsSettingAttribute = settingsService.get(awsConfigId);
    notNullCheck("awsSettingAttribute", awsSettingAttribute);
    if (!(awsSettingAttribute.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("Invalid Aws Config Id");
    }
    return (AwsConfig) awsSettingAttribute.getValue();
  }

  private Activity createActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).getApp();
    notNullCheck("Application not be null", app);
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();
    InstanceElement instanceElement = executionContext.getContextElement(ContextElementType.INSTANCE);
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .appId(app.getUuid())
                                          .commandName(ECS_STEADY_STATE_CHECK_COMMAND_NAME)
                                          .type(Type.Command)
                                          .workflowType(executionContext.getWorkflowType())
                                          .workflowExecutionName(executionContext.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                                          .commandType(getStateType())
                                          .workflowExecutionId(executionContext.getWorkflowExecutionId())
                                          .workflowId(executionContext.getWorkflowId())
                                          .commandUnits(Collections.emptyList())
                                          .status(ExecutionStatus.RUNNING)
                                          .commandUnitType(CommandUnitType.ECS_STEADY_STATE_CHECK)
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
    if (instanceElement != null) {
      activityBuilder.serviceTemplateId(instanceElement.getServiceTemplateElement().getUuid())
          .serviceTemplateName(instanceElement.getServiceTemplateElement().getName())
          .serviceId(instanceElement.getServiceTemplateElement().getServiceElement().getUuid())
          .serviceName(instanceElement.getServiceTemplateElement().getServiceElement().getName())
          .serviceInstanceId(instanceElement.getUuid())
          .hostName(instanceElement.getHost().getHostName());
    }
    Activity activity = activityBuilder.build();
    return activityService.save(activity);
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
