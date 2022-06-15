/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Activity.Type.Command;
import static software.wings.beans.TaskType.ECS_COMMAND_TASK;
import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.AWS_ECS_UPDATE_ROUTE_53_DNS_WEIGHT;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.StateType.ECS_ROUTE53_DNS_WEIGHT_UPDATE;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.ContainerServiceElement;
import software.wings.api.ecs.EcsRoute53WeightUpdateStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.ecs.request.EcsBGRoute53DNSWeightUpdateRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
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
import software.wings.sm.State;

import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EcsBGUpdateRoute53DNSWeightState extends State {
  public static final String UPDATE_ROUTE_53_DNS_WEIGHTS = "Update Route 53 DNS Weights";
  private static final int MIN_WEIGHT = 0;
  private static final int MAX_WEIGHT = 100;
  @Getter @Setter @Attributes(title = "Record TTL") private int recordTTL;
  @Getter @Setter @Attributes(title = "Old Service DNS Weight [0, 100]") private int oldServiceDNSWeight;
  @Getter @Setter @Attributes(title = "New Service DNS Weight [0, 100]") private int newServiceDNSWeight;
  @Getter @Setter @Attributes(title = "Downsize old service") private boolean downsizeOldService;

  @Inject private AppService appService;
  @Inject private SecretManager secretManager;
  @Inject private SettingsService settingsService;
  @Inject private ActivityService activityService;
  @Inject private DelegateService delegateService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject protected EcsStateHelper ecsStateHelper;

  public EcsBGUpdateRoute53DNSWeightState(String name) {
    super(name, ECS_ROUTE53_DNS_WEIGHT_UPDATE.name());
  }

  public EcsBGUpdateRoute53DNSWeightState(String name, String stateType) {
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
  public Integer getTimeoutMillis(ExecutionContext context) {
    return ecsStateHelper.getEcsStateTimeoutFromContext(context, false);
  }

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);
    EcsRoute53WeightUpdateStateExecutionData stateExecutionData =
        (EcsRoute53WeightUpdateStateExecutionData) context.getStateExecutionData();
    if (stateExecutionData != null) {
      stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    }

    ExecutionResponseBuilder builder =
        ExecutionResponse.builder().errorMessage(executionResponse.getErrorMessage()).executionStatus(executionStatus);
    if (null != executionResponse.getEcsCommandResponse()
        && executionResponse.getEcsCommandResponse().isTimeoutFailure()) {
      builder.failureTypes(TIMEOUT);
    }

    return builder.build();
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

  protected ExecutionResponse executeInternal(ExecutionContext context, boolean rollback) {
    ContainerServiceElement containerServiceElement =
        ecsStateHelper.getSetupElementFromSweepingOutput(context, rollback);
    if (containerServiceElement == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .stateExecutionData(aStateExecutionData().withErrorMsg("No container setup element found. Skipping.").build())
          .build();
    }

    Activity activity = createActivity(context);
    Application application = appService.get(context.getAppId());
    EcsInfrastructureMapping infrastructureMapping = (EcsInfrastructureMapping) infrastructureMappingService.get(
        application.getUuid(), context.fetchInfraMappingId());
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context,awsConfig);
    List<EncryptedDataDetail> encryptedDetails =
        secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId());

    EcsBGRoute53DNSWeightUpdateRequest request =
        EcsBGRoute53DNSWeightUpdateRequest.builder()
            .accountId(application.getAccountId())
            .appId(application.getUuid())
            .commandName(UPDATE_ROUTE_53_DNS_WEIGHTS)
            .activityId(activity.getUuid())
            .region(infrastructureMapping.getRegion())
            .cluster(infrastructureMapping.getClusterName())
            .awsConfig(awsConfig)
            .rollback(rollback)
            .serviceName(containerServiceElement.getNewEcsServiceName())
            .serviceNameDownsized(containerServiceElement.getEcsBGSetupData().getDownsizedServiceName())
            .serviceCountDownsized(containerServiceElement.getEcsBGSetupData().getDownsizedServiceCount())
            .downsizeOldService(downsizeOldService)
            .ttl(recordTTL)
            .oldServiceWeight(getDNSWeightWithinLimits(oldServiceDNSWeight))
            .newServiceWeight(getDNSWeightWithinLimits(newServiceDNSWeight))
            .parentRecordName(containerServiceElement.getEcsBGSetupData().getParentRecordName())
            .parentRecordHostedZoneId(containerServiceElement.getEcsBGSetupData().getParentRecordHostedZoneId())
            .oldServiceDiscoveryArn(containerServiceElement.getEcsBGSetupData().getOldServiceDiscoveryArn())
            .newServiceDiscoveryArn(containerServiceElement.getEcsBGSetupData().getNewServiceDiscoveryArn())
            .timeout(containerServiceElement.getServiceSteadyStateTimeout())
            .timeoutErrorSupported(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, application.getAccountId()))
            .previousAwsAutoScalarConfigs(containerServiceElement.getPreviousAwsAutoScalarConfigs())
            .build();

    EcsRoute53WeightUpdateStateExecutionData executionData =
        EcsRoute53WeightUpdateStateExecutionData.builder()
            .activityId(activity.getUuid())
            .newServiceName(containerServiceElement.getNewEcsServiceName())
            .newServiceDiscoveryServiceArn(containerServiceElement.getEcsBGSetupData().getNewServiceDiscoveryArn())
            .newServiceWeight(rollback ? 0 : getDNSWeightWithinLimits(newServiceDNSWeight))
            .oldServiceName(containerServiceElement.getEcsBGSetupData().getDownsizedServiceName())
            .oldServiceDiscoveryServiceArn(containerServiceElement.getEcsBGSetupData().getOldServiceDiscoveryArn())
            .oldServiceWeight(rollback ? 100 : getDNSWeightWithinLimits(oldServiceDNSWeight))
            .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(infrastructureMapping.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, infrastructureMapping.getAppId())
            .waitId(activity.getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(ECS_COMMAND_TASK.name())
                      .parameters(new Object[] {request, encryptedDetails})
                      .timeout(MINUTES.toMillis(containerServiceElement.getServiceSteadyStateTimeout()))
                      .build())
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, infrastructureMapping.getEnvId())
            .setupAbstraction(
                Cd1SetupFields.ENV_TYPE_FIELD, context.fetchRequiredEnvironment().getEnvironmentType().name())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("ECS Blue Green Update Route task execution")
            .build();

    delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(executionData)
        .executionStatus(SUCCESS)
        .correlationId(activity.getUuid())
        .build();
  }

  private int getDNSWeightWithinLimits(int weight) {
    if (weight < MIN_WEIGHT) {
      return MIN_WEIGHT;
    } else if (weight > MAX_WEIGHT) {
      return MAX_WEIGHT;
    } else {
      return weight;
    }
  }

  private Activity createActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).getApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();
    notNullCheck("Application cannot be null", app);

    ActivityBuilder activityBuilder = Activity.builder()
                                          .appId(app.getUuid())
                                          .applicationName(app.getName())
                                          .commandName(getName())
                                          .type(Command)
                                          .commandUnitType(AWS_ECS_UPDATE_ROUTE_53_DNS_WEIGHT)
                                          .workflowType(executionContext.getWorkflowType())
                                          .workflowExecutionName(executionContext.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                                          .commandType(getStateType())
                                          .workflowExecutionId(executionContext.getWorkflowExecutionId())
                                          .workflowId(executionContext.getWorkflowId())
                                          .commandUnits(emptyList())
                                          .status(RUNNING)
                                          .environmentId(env.getUuid())
                                          .environmentName(env.getName())
                                          .environmentType(env.getEnvironmentType());
    return activityService.save(activityBuilder.build());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
