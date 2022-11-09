/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.TaskType.AWS_AMI_ASYNC_TASK;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AwsAmiSwitchRoutesStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesRequest;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AwsAmiSwitchRoutesState extends State {
  public static final String SWAP_AUTO_SCALING_ROUTES = "Swap AutoScaling Routes";
  @Attributes(title = "Downsize Old Auto Scaling Group")
  @Getter
  @Setter
  @DefaultValue("true")
  private boolean downsizeOldAsg;

  @Inject protected transient SettingsService settingsService;
  @Inject protected transient InfrastructureMappingService infrastructureMappingService;
  @Inject protected transient ActivityService activityService;
  @Inject protected transient SecretManager secretManager;
  @Inject protected transient DelegateService delegateService;
  @Inject protected transient AwsStateHelper awsStateHelper;
  @Inject protected transient AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Inject private FeatureFlagService featureFlagService;

  public AwsAmiSwitchRoutesState(String name) {
    super(name, StateType.AWS_AMI_SWITCH_ROUTES.name());
  }
  public AwsAmiSwitchRoutesState(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context, false);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    AmiServiceSetupElement serviceSetupElement =
        (AmiServiceSetupElement) awsAmiServiceHelper.getSetupElementFromSweepingOutput(
            context, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    return awsStateHelper.getAmiStateTimeout(serviceSetupElement);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  protected ExecutionResponse executeInternal(ExecutionContext context, boolean rollback) throws InterruptedException {
    AmiServiceSetupElement serviceSetupElement =
        (AmiServiceSetupElement) awsAmiServiceHelper.getSetupElementFromSweepingOutput(
            context, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    if (serviceSetupElement == null) {
      return ExecutionResponse.builder()
          .errorMessage("Did not find Setup context element. Skipping rollback")
          .executionStatus(ExecutionStatus.SUCCESS)
          .build();
    }
    Activity activity = createActivity(context);
    AwsAmiInfrastructureMapping infrastructureMapping = (AwsAmiInfrastructureMapping) infrastructureMappingService.get(
        activity.getAppId(), context.fetchInfraMappingId());
    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    String region = infrastructureMapping.getRegion();
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context, awsConfig);
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId());
    AwsAmiSwitchRoutesRequest routesRequest =
        AwsAmiSwitchRoutesRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .accountId(infrastructureMapping.getAccountId())
            .appId(infrastructureMapping.getAppId())
            .activityId(activity.getUuid())
            .commandName(SWAP_AUTO_SCALING_ROUTES)
            .oldAsgName(serviceSetupElement.getOldAutoScalingGroupName())
            .newAsgName(serviceSetupElement.getNewAutoScalingGroupName())
            .primaryClassicLBs(infrastructureMapping.getClassicLoadBalancers())
            .primaryTargetGroupARNs(infrastructureMapping.getTargetGroupArns())
            .stageClassicLBs(infrastructureMapping.getStageClassicLoadBalancers())
            .stageTargetGroupARNs(infrastructureMapping.getStageTargetGroupArns())
            .registrationTimeout(serviceSetupElement.getAutoScalingSteadyStateTimeout())
            .preDeploymentData(serviceSetupElement.getPreDeploymentData())
            .downscaleOldAsg(downsizeOldAsg)
            .rollback(rollback)
            .baseScalingPolicyJSONs(serviceSetupElement.getBaseScalingPolicyJSONs())
            .amiInServiceHealthyStateFFEnabled(false)
            .scheduledActions(featureFlagService.isEnabled(FeatureName.AMI_ASG_CONFIG_COPY, context.getAccountId())
                    ? serviceSetupElement.getBaseAsgScheduledActionJSONs()
                    : null)
            .amiAsgConfigCopyEnabled(
                featureFlagService.isEnabled(FeatureName.AMI_ASG_CONFIG_COPY, context.getAccountId()))
            .build();

    AwsAmiSwitchRoutesStateExecutionData executionData =
        AwsAmiSwitchRoutesStateExecutionData.builder()
            .activityId(activity.getUuid())
            .oldAutoScalingGroupName(serviceSetupElement.getOldAutoScalingGroupName())
            .newAutoScalingGroupName(serviceSetupElement.getNewAutoScalingGroupName())
            .targetArns(infrastructureMapping.getTargetGroupArns())
            .classicLbs(infrastructureMapping.getClassicLoadBalancers())
            .stageTargetArns(infrastructureMapping.getStageTargetGroupArns())
            .stageClassicLbs(infrastructureMapping.getStageClassicLoadBalancers())
            .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(infrastructureMapping.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, infrastructureMapping.getAppId())
            .waitId(activity.getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(AWS_AMI_ASYNC_TASK.name())
                      .parameters(new Object[] {routesRequest})
                      .timeout(TimeUnit.MINUTES.toMillis(serviceSetupElement.getAutoScalingSteadyStateTimeout()))
                      .build())
            .tags(isNotEmpty(routesRequest.getAwsConfig().getTag())
                    ? singletonList(routesRequest.getAwsConfig().getTag())
                    : null)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, infrastructureMapping.getEnvId())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, context.fetchRequiredEnvironment().getEnvironmentType().name())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Aws Ami switch routes task execution")
            .build();
    delegateService.queueTask(delegateTask);

    appendDelegateTaskDetails(context, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(executionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .correlationId(activity.getUuid())
        .build();
  }

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    AwsAmiSwitchRoutesResponse routesResponse = (AwsAmiSwitchRoutesResponse) response.values().iterator().next();
    activityService.updateStatus(activityId, context.getAppId(), routesResponse.getExecutionStatus());
    return ExecutionResponse.builder().executionStatus(routesResponse.getExecutionStatus()).build();
  }

  protected Activity createActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    Environment env = ((ExecutionContextImpl) executionContext).fetchRequiredEnvironment();

    ActivityBuilder activityBuilder =
        Activity.builder()
            .applicationName(app.getName())
            .commandName(SWAP_AUTO_SCALING_ROUTES)
            .type(Type.Command)
            .commandUnitType(CommandUnitType.AWS_AMI_SWITCH_ROUTES)
            .workflowType(executionContext.getWorkflowType())
            .workflowExecutionName(executionContext.getWorkflowExecutionName())
            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
            .commandType(getStateType())
            .workflowExecutionId(executionContext.getWorkflowExecutionId())
            .workflowId(executionContext.getWorkflowId())
            .commandUnits(ImmutableList.of(new SpotinstDummyCommandUnit(SWAP_AUTO_SCALING_ROUTES)))
            .status(ExecutionStatus.RUNNING);

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
    return activityService.save(activity);
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
