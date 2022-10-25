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
import static io.harness.exception.utils.ExceptionUtils.getMessage;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.AWS_ECS_SERVICE_DEPLOY;
import static software.wings.beans.command.EcsResizeParams.EcsResizeParamsBuilder.anEcsResizeParams;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateTask;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.tasks.ResponseData;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.command.EcsResizeParams;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EcsServiceDeploy extends State {
  public static final String ECS_SERVICE_DEPLOY = "ECS Service Deploy";
  @Getter @Setter private String instanceCount;
  @Getter @Setter private String downsizeInstanceCount;
  @Getter @Setter private InstanceUnitType instanceUnitType = InstanceUnitType.PERCENTAGE;
  @Getter @Setter private InstanceUnitType downsizeInstanceUnitType = InstanceUnitType.PERCENTAGE;

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

  public EcsServiceDeploy(String name) {
    super(name, StateType.ECS_SERVICE_DEPLOY.name());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

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
    return ecsStateHelper.getEcsStateTimeoutFromContext(context, false);
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    EcsDeployDataBag deployDataBag = ecsStateHelper.prepareBagForEcsDeploy(
        context, serviceResourceService, infrastructureMappingService, settingsService, secretManager, false);
    if (deployDataBag.getContainerElement() == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .stateExecutionData(aStateExecutionData().withErrorMsg("No container setup element found. Skipping.").build())
          .build();
    }

    Activity activity = ecsStateHelper.createActivity(
        context, ECS_SERVICE_DEPLOY, getStateType(), AWS_ECS_SERVICE_DEPLOY, activityService);

    boolean blueGreen = false;
    ContainerServiceElement serviceSetupData = ecsStateHelper.getSetupElementFromSweepingOutput(context, false);
    if (null != serviceSetupData && null != serviceSetupData.getEcsBGSetupData()) {
      blueGreen = true;
    }

    CommandStateExecutionData executionData = aCommandStateExecutionData()
                                                  .withServiceId(deployDataBag.getService().getUuid())
                                                  .withServiceName(deployDataBag.getService().getName())
                                                  .withAppId(deployDataBag.getApp().getUuid())
                                                  .withCommandName(ECS_SERVICE_DEPLOY)
                                                  .withClusterName(deployDataBag.getContainerElement().getClusterName())
                                                  .withActivityId(activity.getUuid())
                                                  .build();

    EcsResizeParams resizeParams =
        anEcsResizeParams()
            .withRollback(false)
            .withRegion(deployDataBag.getRegion())
            .withInstanceUnitType(getInstanceUnitType())
            .withImage(deployDataBag.getContainerElement().getImage())
            .withClusterName(deployDataBag.getContainerElement().getClusterName())
            .withContainerServiceName(deployDataBag.getContainerElement().getName())
            .withMaxInstances(deployDataBag.getContainerElement().getMaxInstances())
            .withFixedInstances(deployDataBag.getContainerElement().getFixedInstances())
            .withResizeStrategy(deployDataBag.getContainerElement().getResizeStrategy())
            .withInstanceCount(Integer.valueOf(context.renderExpression(getInstanceCount())))
            .withUseFixedInstances(deployDataBag.getContainerElement().isUseFixedInstances())
            .withContainerServiceName(deployDataBag.getContainerElement().getNewEcsServiceName())
            .withOriginalServiceCounts(deployDataBag.getContainerElement().getActiveServiceCounts())
            .withServiceSteadyStateTimeout(deployDataBag.getContainerElement().getServiceSteadyStateTimeout())
            .withPreviousAwsAutoScalarConfigs(deployDataBag.getContainerElement().getPreviousAwsAutoScalarConfigs())
            .withAwsAutoScalarConfigForNewService(deployDataBag.getContainerElement().getNewServiceAutoScalarConfig())
            .withPreviousEcsAutoScalarsAlreadyRemoved(
                deployDataBag.getContainerElement().isPrevAutoscalarsAlreadyRemoved())
            .withEcsAutoscalarRedesignEnabled(
                featureFlagService.isEnabled(ECS_AUTOSCALAR_REDESIGN, deployDataBag.getApp().getAccountId()))
            .withIsLastDeployPhase(context.isLastPhase(false))
            .withDownsizeInstanceCount(getDownsizeCount(context))
            .withDownsizeInstanceUnitType(getDownsizeInstanceUnitType())
            .build();

    EcsServiceDeployRequest request = EcsServiceDeployRequest.builder()
                                          .accountId(deployDataBag.getApp().getAccountId())
                                          .appId(deployDataBag.getApp().getUuid())
                                          .commandName(ECS_SERVICE_DEPLOY)
                                          .activityId(activity.getUuid())
                                          .region(deployDataBag.getRegion())
                                          .cluster(deployDataBag.getEcsInfrastructureMapping().getClusterName())
                                          .awsConfig(deployDataBag.getAwsConfig())
                                          .ecsResizeParams(resizeParams)
                                          .timeoutErrorSupported(featureFlagService.isEnabled(
                                              TIMEOUT_FAILURE_SUPPORT, deployDataBag.getApp().getAccountId()))
                                          .blueGreen(blueGreen)
                                          .build();

    ecsStateHelper.createSweepingOutputForRollback(
        deployDataBag, activity, delegateService, resizeParams, context, blueGreen);

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

  private Integer getDownsizeCount(ExecutionContext context) {
    return isNotBlank(getDownsizeInstanceCount())
        ? Integer.valueOf(context.renderExpression(getDownsizeInstanceCount()))
        : null;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return ecsStateHelper.handleDelegateResponseForEcsDeploy(
          context, response, false, activityService, false, containerDeploymentHelper);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (!isRollback() && isBlank(getInstanceCount())) {
      invalidFields.put("instanceCount", "Instance count must not be blank");
    }
    return invalidFields;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
