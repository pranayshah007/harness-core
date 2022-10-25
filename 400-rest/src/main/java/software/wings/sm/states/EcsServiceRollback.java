/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.FeatureName.ECS_AUTOSCALAR_REDESIGN;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.AWS_ECS_SERVICE_DEPLOY;
import static software.wings.beans.command.EcsResizeParams.EcsResizeParamsBuilder.anEcsResizeParams;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.states.EcsServiceDeploy.ECS_SERVICE_DEPLOY;

import static java.util.Collections.singletonList;

import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.tasks.ResponseData;

import software.wings.api.CommandStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EcsServiceRollback extends State {
  @Getter @Setter @Attributes(title = "Rollback all phases at once") private boolean rollbackAllPhases;

  @Inject private SecretManager secretManager;
  @Inject private EcsStateHelper ecsStateHelper;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private ActivityService activityService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentHelper;
  @Inject private FeatureFlagService featureFlagService;
  private String accountId;

  public EcsServiceRollback(String name) {
    super(name, StateType.ECS_SERVICE_ROLLBACK.name());
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

  @Override
  public Integer getTimeoutMillis(ExecutionContext context) {
    return ecsStateHelper.getEcsStateTimeoutFromContext(context, true);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private ExecutionResponse executeInternal(ExecutionContext context) {
    if (ecsStateHelper.allPhaseRollbackDone(context)) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .stateExecutionData(aStateExecutionData().withErrorMsg("All phase Rollback already done. Skipping.").build())
          .build();
    }

    ContextElement rollbackElement = ecsStateHelper.getDeployElementFromSweepingOutput(context);
    if (rollbackElement == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .stateExecutionData(aStateExecutionData().withErrorMsg("No context found for rollback. Skipping.").build())
          .build();
    }

    EcsDeployDataBag deployDataBag = ecsStateHelper.prepareBagForEcsDeploy(
        context, serviceResourceService, infrastructureMappingService, settingsService, secretManager, true);
    if (deployDataBag.getContainerElement() == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .stateExecutionData(aStateExecutionData().withErrorMsg("No container setup element found. Skipping.").build())
          .build();
    }

    Activity activity = ecsStateHelper.createActivity(
        context, ECS_SERVICE_DEPLOY, getStateType(), AWS_ECS_SERVICE_DEPLOY, activityService);

    CommandStateExecutionData executionData = aCommandStateExecutionData()
                                                  .withServiceId(deployDataBag.getService().getUuid())
                                                  .withServiceName(deployDataBag.getService().getName())
                                                  .withAppId(deployDataBag.getApp().getUuid())
                                                  .withCommandName(ECS_SERVICE_DEPLOY)
                                                  .withClusterName(deployDataBag.getContainerElement().getClusterName())
                                                  .withActivityId(activity.getUuid())
                                                  .build();

    accountId = deployDataBag.getApp().getAccountId();

    EcsResizeParams resizeParams =
        anEcsResizeParams()
            .withClusterName(deployDataBag.getContainerElement().getClusterName())
            .withRegion(deployDataBag.getRegion())
            .withServiceSteadyStateTimeout(deployDataBag.getContainerElement().getServiceSteadyStateTimeout())
            .withContainerServiceName(deployDataBag.getContainerElement().getName())
            .withResizeStrategy(deployDataBag.getContainerElement().getResizeStrategy())
            .withUseFixedInstances(deployDataBag.getContainerElement().isUseFixedInstances())
            .withMaxInstances(deployDataBag.getContainerElement().getMaxInstances())
            .withFixedInstances(deployDataBag.getContainerElement().getFixedInstances())
            .withNewInstanceData(deployDataBag.getRollbackElement().getNewInstanceData())
            .withOldInstanceData(deployDataBag.getRollbackElement().getOldInstanceData())
            .withOriginalServiceCounts(deployDataBag.getContainerElement().getActiveServiceCounts())
            .withRollback(true)
            .withRollbackAllPhases(getRollbackAtOnce(deployDataBag))
            .withPreviousAwsAutoScalarConfigs(deployDataBag.getContainerElement().getPreviousAwsAutoScalarConfigs())
            .withContainerServiceName(deployDataBag.getContainerElement().getNewEcsServiceName())
            .withEcsAutoscalarRedesignEnabled(featureFlagService.isEnabled(ECS_AUTOSCALAR_REDESIGN, accountId))
            .withIsLastDeployPhase(context.isLastPhase(true))
            .withFFMaxDesiredCountEnabled(
                featureFlagService.isEnabled(FeatureName.ECS_ROLLBACK_MAX_DESIRED_COUNT, accountId))
            .build();

    EcsServiceDeployRequest request =
        EcsServiceDeployRequest.builder()
            .accountId(accountId)
            .appId(deployDataBag.getApp().getUuid())
            .commandName(ECS_SERVICE_DEPLOY)
            .activityId(activity.getUuid())
            .region(deployDataBag.getRegion())
            .cluster(deployDataBag.getEcsInfrastructureMapping().getClusterName())
            .awsConfig(deployDataBag.getAwsConfig())
            .ecsResizeParams(resizeParams)
            .timeoutErrorSupported(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, accountId))
            .build();

    DelegateTask delegateTask = ecsStateHelper.createAndQueueDelegateTaskForEcsServiceDeploy(
        deployDataBag, request, activity, delegateService, isSelectionLogsTrackingForTasksEnabled());
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activity.getUuid()))
        .stateExecutionData(executionData)
        .delegateTaskId(delegateTask.getUuid())
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      EcsDeployDataBag deployDataBag = ecsStateHelper.prepareBagForEcsDeploy(
          context, serviceResourceService, infrastructureMappingService, settingsService, secretManager, true);
      return ecsStateHelper.handleDelegateResponseForEcsDeploy(
          context, response, true, activityService, getRollbackAtOnce(deployDataBag), containerDeploymentHelper);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  private boolean getRollbackAtOnce(EcsDeployDataBag deployDataBag) {
    if (featureFlagService.isEnabled(ECS_AUTOSCALAR_REDESIGN, deployDataBag.getApp().getAccountId())) {
      return rollbackAllPhases;
    } else {
      List<AwsAutoScalarConfig> awsAutoScalarConfigs =
          deployDataBag.getContainerElement().getPreviousAwsAutoScalarConfigs();
      if (isNotEmpty(awsAutoScalarConfigs)) {
        rollbackAllPhases = true;
      }
      return rollbackAllPhases;
    }
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
