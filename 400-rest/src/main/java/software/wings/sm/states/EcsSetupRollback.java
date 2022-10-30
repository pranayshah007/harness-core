/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static java.util.Collections.singletonList;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerRollbackRequestElement;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContainerServiceElement.ContainerServiceElementBuilder;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.beans.Activity;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.EcsSetupParams;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceSetupResponse;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EcsSetupRollback extends State {
  public static final String ECS_DAEMON_SERVICE_ROLLBACK_COMMAND = "ECS Daemon Service Rollback";

  @Getter @Setter private int serviceSteadyStateTimeout;

  @Inject private SecretManager secretManager;
  @Inject private EcsStateHelper ecsStateHelper;
  @Inject private ActivityService activityService;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private FeatureFlagService featureFlagService;

  public EcsSetupRollback(String name) {
    super(name, StateType.ECS_SERVICE_SETUP_ROLLBACK.name());
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

  private ExecutionResponse executeInternal(ExecutionContext context) {
    EcsSetUpDataBag dataBag = ecsStateHelper.prepareBagForEcsSetUp(context, serviceSteadyStateTimeout,
        artifactCollectionUtils, serviceResourceService, infrastructureMappingService, settingsService, secretManager);

    Activity activity = ecsStateHelper.createActivity(context, ECS_DAEMON_SERVICE_ROLLBACK_COMMAND, getStateType(),
        CommandUnitType.AWS_ECS_SERVICE_ROLLBACK_DAEMON, activityService);

    ContainerRollbackRequestElement rollbackElement = ecsStateHelper.getDeployElementFromSweepingOutput(context);

    EcsSetupParams ecsSetupParams = (EcsSetupParams) ecsStateHelper.buildContainerSetupParams(context,
        EcsSetupStateConfig.builder()
            .app(dataBag.getApplication())
            .env(dataBag.getEnvironment())
            .service(dataBag.getService())
            .infrastructureMapping(dataBag.getEcsInfrastructureMapping())
            .clusterName(dataBag.getEcsInfrastructureMapping().getClusterName())
            .serviceSteadyStateTimeout(dataBag.getServiceSteadyStateTimeout())
            .rollback(true)
            .containerTask(dataBag.getContainerTask())
            .ecsServiceSpecification(dataBag.getServiceSpecification())
            .previousEcsServiceSnapshotJson(rollbackElement.getPreviousEcsServiceSnapshotJson())
            .ecsServiceArn(rollbackElement.getEcsServiceArn())
            .isDaemonSchedulingStrategy(true)
            .serviceName(dataBag.getService().getName())
            .build());

    CommandStateExecutionData stateExecutionData =
        ecsStateHelper.getStateExecutionData(dataBag, ECS_DAEMON_SERVICE_ROLLBACK_COMMAND, ecsSetupParams, activity);

    EcsSetupContextVariableHolder variables = ecsStateHelper.renderEcsSetupContextVariables(context);

    EcsServiceSetupRequest request = EcsServiceSetupRequest.builder()
                                         .ecsSetupParams(ecsSetupParams)
                                         .awsConfig(dataBag.getAwsConfig())
                                         .clusterName(ecsSetupParams.getClusterName())
                                         .region(ecsSetupParams.getRegion())
                                         .safeDisplayServiceVariables(variables.getSafeDisplayServiceVariables())
                                         .serviceVariables(variables.getServiceVariables())
                                         .accountId(dataBag.getApplication().getAccountId())
                                         .appId(dataBag.getApplication().getUuid())
                                         .commandName(ECS_DAEMON_SERVICE_ROLLBACK_COMMAND)
                                         .activityId(activity.getUuid())
                                         .timeoutErrorSupported(featureFlagService.isEnabled(
                                             TIMEOUT_FAILURE_SUPPORT, dataBag.getApplication().getAccountId()))
                                         .build();

    DelegateTask delegateTask = ecsStateHelper.createAndQueueDelegateTaskForEcsServiceSetUp(
        request, dataBag, activity, delegateService, isSelectionLogsTrackingForTasksEnabled());
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activity.getUuid()))
        .stateExecutionData(stateExecutionData)
        .delegateTaskId(delegateTask.getUuid())
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    EcsServiceSetupResponse ecsServiceSetupResponse =
        (EcsServiceSetupResponse) executionResponse.getEcsCommandResponse();
    ContainerSetupCommandUnitExecutionData setupExecutionData = ecsServiceSetupResponse.getSetupData();
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    if (artifact == null) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT).addParam("args", "Artifact is null");
    }

    ImageDetails imageDetails =
        artifactCollectionUtils.fetchContainerImageDetails(artifact, context.getWorkflowExecutionId());

    ContainerServiceElement containerServiceElement =
        buildContainerServiceElement(context, setupExecutionData, imageDetails);

    ecsStateHelper.populateFromDelegateResponse(setupExecutionData, executionData, containerServiceElement);
    executionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

    ExecutionResponseBuilder builder = ExecutionResponse.builder()
                                           .stateExecutionData(executionData)
                                           .executionStatus(executionStatus)
                                           .contextElement(containerServiceElement)
                                           .notifyElement(containerServiceElement);
    if (ecsServiceSetupResponse.isTimeoutFailure()) {
      builder.failureTypes(TIMEOUT);
    }
    return builder.build();
  }

  private ContainerServiceElement buildContainerServiceElement(
      ExecutionContext context, ContainerSetupCommandUnitExecutionData setupExecutionData, ImageDetails imageDetails) {
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    int serviceSteadyStateTimeout =
        getServiceSteadyStateTimeout() > 0 ? getServiceSteadyStateTimeout() : DEFAULT_STEADY_STATE_TIMEOUT;
    EcsSetupParams setupParams = (EcsSetupParams) executionData.getContainerSetupParams();
    ContainerServiceElementBuilder serviceElementBuilder =
        ContainerServiceElement.builder()
            .uuid(executionData.getServiceId())
            .image(imageDetails.getName() + ":" + imageDetails.getTag())
            .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
            .clusterName(executionData.getClusterName())
            .deploymentType(DeploymentType.ECS)
            .infraMappingId(setupParams.getInfraMappingId());
    if (setupExecutionData != null) {
      serviceElementBuilder.name(setupExecutionData.getContainerServiceName());
    }
    return serviceElementBuilder.build();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
