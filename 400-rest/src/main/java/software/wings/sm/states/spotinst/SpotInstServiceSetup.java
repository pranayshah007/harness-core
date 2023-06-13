/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;

import static software.wings.sm.StateType.SPOTINST_SETUP;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstSetupTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
@OwnedBy(CDP)
public class SpotInstServiceSetup extends State {
  public static final String SPOTINST_SERVICE_SETUP_COMMAND = "Spotinst Service Setup";
  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Getter @Setter private String minInstances;
  @Getter @Setter private String maxInstances;
  @Getter @Setter private String targetInstances;

  @Getter @Setter private Integer currentRunningCount;
  @Getter @Setter private String elastiGroupNamePrefix;
  @Getter @Setter private Integer timeoutIntervalInMin;
  @Getter @Setter private Integer olderActiveVersionCountToKeep;
  @Getter @Setter private boolean blueGreen;
  @Getter @Setter private boolean useCurrentRunningCount;
  @Getter @Setter private ResizeStrategy resizeStrategy;

  // LoadBalancer details
  @Getter @Setter private boolean useLoadBalancer;
  private List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs;
  @Inject private transient ActivityService activityService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient SpotInstStateHelper spotinstStateHelper;
  @Inject private SweepingOutputService sweepingOutputService;

  public SpotInstServiceSetup(String name) {
    super(name, SPOTINST_SETUP.name());
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
    blueGreen = spotinstStateHelper.isBlueGreenWorkflow(context);

    // Override if not so.
    if (blueGreen) {
      resizeStrategy = ResizeStrategy.RESIZE_NEW_FIRST;
    }

    // Contains SpotInstCommandRequest + env/infra/activity/workflowExecution ids
    SpotInstSetupStateExecutionData spotinstSetupStateExecutionData =
        spotinstStateHelper.prepareStateExecutionData(context, this);

    SpotInstSetupTaskParameters spotInstTaskParameters =
        (SpotInstSetupTaskParameters) spotinstSetupStateExecutionData.getSpotinstCommandRequest()
            .getSpotInstTaskParameters();

    DelegateTask delegateTask = spotinstStateHelper.getDelegateTask(spotInstTaskParameters.getAccountId(),
        spotInstTaskParameters.getAppId(), TaskType.SPOTINST_COMMAND_TASK, spotInstTaskParameters.getActivityId(),
        spotinstSetupStateExecutionData.getEnvId(), spotinstSetupStateExecutionData.getInfraMappingId(),
        spotinstSetupStateExecutionData.getSpotinstCommandRequest(),
        spotinstSetupStateExecutionData.getEnvironmentType(), spotinstSetupStateExecutionData.getServiceId(),
        isSelectionLogsTrackingForTasksEnabled());

    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .correlationIds(Arrays.asList(spotInstTaskParameters.getActivityId()))
        .stateExecutionData(spotinstSetupStateExecutionData)
        .async(true)
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
    SpotInstTaskExecutionResponse executionResponse =
        (SpotInstTaskExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : FAILED;

    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    SpotInstSetupTaskResponse spotInstSetupTaskResponse =
        (SpotInstSetupTaskResponse) executionResponse.getSpotInstTaskResponse();

    SpotInstSetupStateExecutionData stateExecutionData =
        (SpotInstSetupStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());

    if (ExecutionStatus.SUCCESS == executionStatus && spotInstSetupTaskResponse != null) {
      if (spotInstSetupTaskResponse.getNewElastiGroup() != null) {
        stateExecutionData.getElastiGroupOriginalConfig().setId(spotInstSetupTaskResponse.getNewElastiGroup().getId());
        stateExecutionData.getElastiGroupOriginalConfig().setName(
            spotInstSetupTaskResponse.getNewElastiGroup().getName());
        stateExecutionData.setElastiGroupId(spotInstSetupTaskResponse.getNewElastiGroup().getId());
        stateExecutionData.setElastiGroupName(spotInstSetupTaskResponse.getNewElastiGroup().getName());
      }
      if (useCurrentRunningCount) {
        int min = DEFAULT_ELASTIGROUP_MIN_INSTANCES;
        int max = DEFAULT_ELASTIGROUP_MAX_INSTANCES;
        int target = DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
        List<ElastiGroup> groupToBeDownsized = spotInstSetupTaskResponse.getGroupToBeDownsized();
        if (isNotEmpty(groupToBeDownsized)) {
          ElastiGroup elastiGroupToBeDownsized = groupToBeDownsized.get(0);
          if (elastiGroupToBeDownsized != null) {
            ElastiGroupCapacity capacity = elastiGroupToBeDownsized.getCapacity();
            if (capacity != null) {
              min = capacity.getMinimum();
              max = capacity.getMaximum();
              target = capacity.getTarget();
            }
          }
        }
        stateExecutionData.getElastiGroupOriginalConfig().getCapacity().setMinimum(min);
        stateExecutionData.getElastiGroupOriginalConfig().getCapacity().setMaximum(max);
        stateExecutionData.getElastiGroupOriginalConfig().getCapacity().setTarget(target);
      }
    }

    if (FAILED == executionStatus && executionResponse.isTimeoutError()) {
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .failureTypes(FailureType.TIMEOUT)
          .errorMessage(executionResponse.getErrorMessage())
          .stateExecutionData(stateExecutionData)
          .build();
    }

    stateExecutionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());

    SpotInstCommandRequest spotInstCommandRequest = stateExecutionData.getSpotinstCommandRequest();
    elastiGroupNamePrefix =
        ((SpotInstSetupTaskParameters) spotInstCommandRequest.getSpotInstTaskParameters()).getElastiGroupNamePrefix();
    SpotInstSetupContextElement spotInstSetupContextElement =
        SpotInstSetupContextElement.builder()
            .commandRequest(spotInstCommandRequest)
            .commandName(SPOTINST_SERVICE_SETUP_COMMAND)
            .maxInstanceCount(stateExecutionData.getMaxInstanceCount())
            .useCurrentRunningInstanceCount(stateExecutionData.isUseCurrentRunningInstanceCount())
            .resizeStrategy(resizeStrategy)
            .spotInstSetupTaskResponse(spotInstSetupTaskResponse)
            .isBlueGreen(spotinstStateHelper.isBlueGreenWorkflow(context))
            .serviceId(stateExecutionData.getServiceId())
            .infraMappingId(stateExecutionData.getInfraMappingId())
            .appId(stateExecutionData.getAppId())
            .envId(stateExecutionData.getEnvId())
            .newElastiGroupOriginalConfig(stateExecutionData.getElastiGroupOriginalConfig())
            .elstiGroupNamePrefix(Misc.normalizeExpression(context.renderExpression(elastiGroupNamePrefix)))
            .lbDetailsForBGDeployment(
                spotInstSetupTaskResponse != null ? spotInstSetupTaskResponse.getLbDetailsForBGDeployments() : null)
            .build();

    // Add these details only if spotInstSetupTaskResponse is not NULL
    addDetailsForSuccessfulExecution(spotInstSetupContextElement, spotInstSetupTaskResponse);

    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(spotinstStateHelper.getSweepingOutputName(context, SPOTINST_SERVICE_SETUP_SWEEPING_OUTPUT_NAME))
            .value(spotInstSetupContextElement)
            .build());

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(executionResponse.getErrorMessage())
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private void addDetailsForSuccessfulExecution(
      SpotInstSetupContextElement spotInstSetupContextElement, SpotInstSetupTaskResponse spotInstSetupTaskResponse) {
    if (spotInstSetupTaskResponse == null) {
      return;
    }

    ElastiGroup oldElastiGroup = fetchOldElasticGroup(spotInstSetupTaskResponse);
    spotInstSetupContextElement.setOldElastiGroupOriginalConfig(oldElastiGroup);
    spotInstSetupContextElement.setLbDetailsForBGDeployment(spotInstSetupTaskResponse.getLbDetailsForBGDeployments());

    if (oldElastiGroup != null && oldElastiGroup.getCapacity() != null) {
      spotInstSetupContextElement.setCurrentRunningInstanceCount(oldElastiGroup.getCapacity().getTarget());
    } else {
      spotInstSetupContextElement.setCurrentRunningInstanceCount(DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT);
    }
  }

  private ElastiGroup fetchOldElasticGroup(SpotInstSetupTaskResponse spotInstSetupTaskResponse) {
    if (isEmpty(spotInstSetupTaskResponse.getGroupToBeDownsized())) {
      return null;
    }

    return spotInstSetupTaskResponse.getGroupToBeDownsized().get(0);
  }

  public List<LoadBalancerDetailsForBGDeployment> getAwsLoadBalancerConfigs() {
    return awsLoadBalancerConfigs;
  }

  public void setAwsLoadBalancerConfigs(List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs) {
    this.awsLoadBalancerConfigs = awsLoadBalancerConfigs;
  }

  @Override
  public Map<String, String> validateFields() {
    if (useCurrentRunningCount) {
      return emptyMap();
    }
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(minInstances)) {
      invalidFields.put("minInstances", "Min Instances is needed");
    }
    if (isEmpty(maxInstances)) {
      invalidFields.put("maxInstances", "Max Instances is needed");
    }
    if (isEmpty(targetInstances)) {
      invalidFields.put("targetInstances", "Target Instances is needed");
    }
    return invalidFields;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
