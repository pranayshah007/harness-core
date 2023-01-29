/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.usagemetrics;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.groupingBy;

import io.harness.beans.WorkflowType;
import io.harness.event.timeseries.processor.EventProcessor;
import io.harness.event.timeseries.processor.StepEventProcessor;
import io.harness.queue.QueuePublisher;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.DeploymentStepTimeSeriesEvent;
import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.api.ExecutionInterruptTimeSeriesEvent;
import software.wings.api.InstanceEvent;
import software.wings.beans.EnvSummary;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionServiceHelper;
import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo;
import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo.DataPoint;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class UsageMetricsEventPublisher {
  @Inject private ExecutorService executorService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private QueuePublisher<DeploymentTimeSeriesEvent> deploymentTimeSeriesEventQueue;
  @Inject private QueuePublisher<InstanceEvent> instanceTimeSeriesEventQueue;
  @Inject private QueuePublisher<DeploymentStepTimeSeriesEvent> deploymentStepTimeSeriesEventQueue;
  @Inject private QueuePublisher<ExecutionInterruptTimeSeriesEvent> executionInterruptTimeSeriesEventQueue;
  SimpleDateFormat sdf;

  private String APPROVAL = "APPROVAL";

  public UsageMetricsEventPublisher() {
    sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    sdf.setTimeZone(TimeZone.getTimeZone(TimeZone.getTimeZone("Etc/UTC").toZoneId()));
  }

  public void publishDeploymentTimeSeriesEvent(
      String accountId, WorkflowExecution workflowExecution, Map<String, Object> metadata) {
    DeploymentTimeSeriesEvent event = constructDeploymentTimeSeriesEvent(accountId, workflowExecution, metadata);
    executorService.submit(() -> {
      try {
        deploymentTimeSeriesEventQueue.send(event);
      } catch (Exception e) {
        log.error("Failed to publish deployment time series event:[{}]", event.getId(), e);
      }
    });
  }

  public void publishDeploymentStepTimeSeriesEvent(String accountId, StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance == null) {
      return;
    }
    for (String stateType : StepEventProcessor.STATE_TYPES) {
      if (stateType.equals(stateExecutionInstance.getStateType())) {
        return;
      }
    }
    DeploymentStepTimeSeriesEvent event = constructDeploymentStepTimeSeriesEvent(accountId, stateExecutionInstance);
    if (event.getTimeSeriesEventInfo().getLongData().get(StepEventProcessor.START_TIME) == null) {
      return;
    }
    executorService.submit(() -> {
      try {
        deploymentStepTimeSeriesEventQueue.send(event);
      } catch (Exception e) {
        log.error("Failed to publish deployment step time series event:[{}]", event.getId(), e);
      }
    });
  }

  public DeploymentStepTimeSeriesEvent constructDeploymentStepTimeSeriesEvent(
      String accountId, StateExecutionInstance stateExecutionInstance) {
    log.info("Reporting Step execution");
    Map<String, String> stringData = new HashMap<>();
    Map<String, Long> longData = new HashMap<>();
    Map<String, Boolean> booleanData = new HashMap<>();

    stringData.put(StepEventProcessor.ID, stateExecutionInstance.getUuid());
    stringData.put(StepEventProcessor.APP_ID, stateExecutionInstance.getAppId());
    stringData.put(StepEventProcessor.STEP_NAME, stateExecutionInstance.getStateName());
    stringData.put(StepEventProcessor.STEP_TYPE, stateExecutionInstance.getStateType());
    if (stateExecutionInstance.getStatus() != null) {
      stringData.put(StepEventProcessor.STATUS, stateExecutionInstance.getStatus().toString());
    }
    stringData.put(StepEventProcessor.STAGE_NAME, stateExecutionInstance.getStageName());
    stringData.put(StepEventProcessor.EXECUTION_ID, stateExecutionInstance.getExecutionUuid());

    if (stateExecutionInstance.getStateExecutionMap() != null) {
      if (stateExecutionInstance.getStateExecutionMap().get(stateExecutionInstance.getStateName()) != null) {
        stringData.put(StepEventProcessor.FAILURE_DETAILS,
            stateExecutionInstance.getStateExecutionMap().get(stateExecutionInstance.getStateName()).getErrorMsg());
      } else if (stateExecutionInstance.getStateExecutionMap().get(stateExecutionInstance.getDisplayName()) != null) {
        stringData.put(StepEventProcessor.FAILURE_DETAILS,
            stateExecutionInstance.getStateExecutionMap().get(stateExecutionInstance.getDisplayName()).getErrorMsg());
      }
    }

    // when we execute shell script step with manual intervention and select retry, then both start time and end time
    // change to track from first execution to last, we traverse history to calculate the oldest execution start time

    long startTime = stateExecutionInstance.getStartTs() != null ? stateExecutionInstance.getStartTs() : -1L;
    List<StateExecutionData> stateExecutionDataList = stateExecutionInstance.getStateExecutionDataHistory();
    if (stateExecutionDataList != null) {
      for (StateExecutionData stateExecutionData : stateExecutionDataList) {
        if (stateExecutionData.getStartTs() != null
            && (startTime == -1L || startTime > stateExecutionData.getStartTs())) {
          startTime = stateExecutionData.getStartTs();
        }
      }
    }
    if (startTime != -1L) {
      longData.put(StepEventProcessor.START_TIME, startTime);
    }

    longData.put(StepEventProcessor.END_TIME, stateExecutionInstance.getEndTs());

    if (startTime != -1l && stateExecutionInstance.getEndTs() != null) {
      longData.put(StepEventProcessor.DURATION, stateExecutionInstance.getEndTs() - startTime);
    }

    if (APPROVAL.equals(stateExecutionInstance.getStateType()) && stateExecutionInstance.getStateExecutionMap() != null
        && stateExecutionInstance.getStateExecutionMap().get(stateExecutionInstance.getStateName()) != null) {
      StateExecutionData stateExecutionData =
          stateExecutionInstance.getStateExecutionMap().get(stateExecutionInstance.getStateName());
      if (stateExecutionData instanceof ApprovalStateExecutionData) {
        ApprovalStateExecutionData approvalStateExecutionData = (ApprovalStateExecutionData) stateExecutionData;
        longData.put(StepEventProcessor.APPROVED_AT, approvalStateExecutionData.getApprovedOn());

        if (approvalStateExecutionData.getStartTs() != null && approvalStateExecutionData.getTimeoutMillis() != null) {
          longData.put(StepEventProcessor.APPROVAL_EXPIRY,
              approvalStateExecutionData.getStartTs() + approvalStateExecutionData.getTimeoutMillis());
        }

        stringData.put(StepEventProcessor.APPROVAL_COMMENT, approvalStateExecutionData.getComments());
        if (approvalStateExecutionData.getApprovalStateType() != null) {
          stringData.put(
              StepEventProcessor.APPROVAL_TYPE, approvalStateExecutionData.getApprovalStateType().toString());
        }
        if (approvalStateExecutionData.getApprovedBy() != null) {
          stringData.put(StepEventProcessor.APPROVED_BY, approvalStateExecutionData.getApprovedBy().getUuid());
        }
      }
    }

    booleanData.put(StepEventProcessor.MANUAL_INTERVENTION, stateExecutionInstance.isWaitingForManualIntervention());

    return DeploymentStepTimeSeriesEvent.builder()
        .timeSeriesEventInfo(TimeSeriesEventInfo.builder()
                                 .accountId(accountId)
                                 .booleanData(booleanData)
                                 .longData(longData)
                                 .stringData(stringData)
                                 .build())
        .build();
  }

  public void publishExecutionInterruptTimeSeriesEvent(String accountId, ExecutionInterrupt executionInterrupt) {
    if (executionInterrupt == null) {
      return;
    }
    ExecutionInterruptTimeSeriesEvent event = constructExecutionInterruptTimeSeriesEvent(accountId, executionInterrupt);
    if (event.getTimeSeriesEventInfo().getLongData().get(StepEventProcessor.EXECUTION_INTERRUPT_CREATED_AT) == null) {
      return;
    }
    executorService.submit(() -> {
      try {
        executionInterruptTimeSeriesEventQueue.send(event);
      } catch (Exception e) {
        log.error("Failed to publish execution interrupt time series event:[{}]", event.getId(), e);
      }
    });
  }

  public ExecutionInterruptTimeSeriesEvent constructExecutionInterruptTimeSeriesEvent(
      String accountId, ExecutionInterrupt executionInterrupt) {
    log.info("Reporting execution interrupt");
    Map<String, String> stringData = new HashMap<>();
    Map<String, Long> longData = new HashMap<>();

    stringData.put(StepEventProcessor.EXECUTION_INTERRUPT_ID, executionInterrupt.getUuid());
    if (executionInterrupt.getExecutionInterruptType() != null) {
      stringData.put(
          StepEventProcessor.EXECUTION_INTERRUPT_TYPE, executionInterrupt.getExecutionInterruptType().toString());
    }

    stringData.put(StepEventProcessor.EXECUTION_ID, executionInterrupt.getExecutionUuid());
    stringData.put(StepEventProcessor.APP_ID, executionInterrupt.getAppId());
    stringData.put(StepEventProcessor.ID, executionInterrupt.getStateExecutionInstanceId());

    if (executionInterrupt.getCreatedBy() != null && executionInterrupt.getCreatedBy().getUuid() != null) {
      stringData.put(StepEventProcessor.EXECUTION_INTERRUPT_CREATED_BY, executionInterrupt.getCreatedBy().getUuid());
    }

    if (executionInterrupt.getLastUpdatedBy() != null && executionInterrupt.getLastUpdatedBy().getUuid() != null) {
      stringData.put(
          StepEventProcessor.EXECUTION_INTERRUPT_UPDATED_BY, executionInterrupt.getLastUpdatedBy().getUuid());
    }

    longData.put(StepEventProcessor.EXECUTION_INTERRUPT_CREATED_AT, executionInterrupt.getCreatedAt());
    longData.put(StepEventProcessor.EXECUTION_INTERRUPT_UPDATED_AT, executionInterrupt.getLastUpdatedAt());

    return ExecutionInterruptTimeSeriesEvent.builder()
        .timeSeriesEventInfo(
            TimeSeriesEventInfo.builder().stringData(stringData).longData(longData).accountId(accountId).build())
        .build();
  }

  public DeploymentTimeSeriesEvent constructDeploymentTimeSeriesEvent(
      String accountId, WorkflowExecution workflowExecution, Map<String, Object> metadata) {
    log.info("Reporting execution");
    Map<String, String> stringData = new HashMap<>();
    Map<String, List<String>> listData = new HashMap<>();
    Map<String, Long> longData = new HashMap<>();
    Map<String, Integer> integerData = new HashMap<>();
    Map<String, Object> objectData = new HashMap<>();
    Map<String, Boolean> booleanData = new HashMap<>();
    stringData.put(EventProcessor.EXECUTIONID, workflowExecution.getUuid());
    stringData.put(EventProcessor.STATUS, workflowExecution.getStatus().name());
    if (workflowExecution.getPipelineExecution() != null) {
      stringData.put(EventProcessor.PIPELINE, workflowExecution.getPipelineExecution().getPipelineId());
    } else {
      listData.put(EventProcessor.WORKFLOW_LIST, Lists.newArrayList(workflowExecution.getWorkflowId()));
    }

    stringData.put(EventProcessor.APPID, workflowExecution.getAppId());
    longData.put(EventProcessor.STARTTIME, workflowExecution.getStartTs());
    longData.put(EventProcessor.ENDTIME, workflowExecution.getEndTs());

    List<String> cloudProviderIds = workflowExecution.getDeployedCloudProviders();

    if (!Lists.isNullOrEmpty(cloudProviderIds)) {
      listData.put(EventProcessor.CLOUD_PROVIDER_LIST, cloudProviderIds);
    }

    if (workflowExecution.getStageName() != null) {
      stringData.put(EventProcessor.STAGENAME, workflowExecution.getStageName());
    }

    if (workflowExecution.getPipelineExecutionId() != null) {
      stringData.put(EventProcessor.PARENT_EXECUTION, workflowExecution.getPipelineExecutionId());
    }

    if (workflowExecution.getFailureDetails() != null) {
      stringData.put(EventProcessor.FAILURE_DETAILS, workflowExecution.getFailureDetails());
    }

    if (workflowExecution.getFailedStepNames() != null) {
      stringData.put(EventProcessor.FAILED_STEP_NAMES, workflowExecution.getFailedStepNames());
    }

    if (workflowExecution.getFailedStepTypes() != null) {
      stringData.put(EventProcessor.FAILED_STEP_TYPES, workflowExecution.getFailedStepTypes());
    }

    if (!Lists.isNullOrEmpty(workflowExecution.getArtifacts())) {
      listData.put(EventProcessor.ARTIFACT_LIST,
          workflowExecution.getArtifacts()
              .stream()
              .filter(Objects::nonNull)
              .map(Artifact::getBuildNo)
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
    }

    List<String> serviceIds = workflowExecution.getDeployedServices();
    if (!Lists.isNullOrEmpty(serviceIds)) {
      listData.put(EventProcessor.SERVICE_LIST, serviceIds);
    }

    List<EnvSummary> environments = workflowExecution.getDeployedEnvironments();

    if (!Lists.isNullOrEmpty(environments)) {
      listData.put(EventProcessor.ENVTYPES,
          new ArrayList<>(
              environments.stream().map(env -> env.getEnvironmentType().name()).collect(Collectors.toSet())));

      listData.put(
          EventProcessor.ENV_LIST, environments.stream().map(EnvSummary::getUuid).collect(Collectors.toList()));
    }
    if (workflowExecution.getDeploymentTriggerId() != null) {
      stringData.put(EventProcessor.TRIGGER_ID, workflowExecution.getDeploymentTriggerId());
    }
    if (workflowExecution.getTriggeredBy() != null) {
      stringData.put(EventProcessor.TRIGGERED_BY, workflowExecution.getTriggeredBy().getUuid());
    }
    if (workflowExecution.getPipelineSummary() != null && workflowExecution.getPipelineSummary().getPipelineId() != null
        && WorkflowType.ORCHESTRATION.equals(workflowExecution.getWorkflowType())) {
      stringData.put(EventProcessor.PARENT_PIPELINE_ID, workflowExecution.getPipelineSummary().getPipelineId());
    }

    stringData.put(EventProcessor.CREATED_BY_TYPE, WorkflowExecutionServiceHelper.getCause(workflowExecution));
    booleanData.put(EventProcessor.ON_DEMAND_ROLLBACK, workflowExecution.isOnDemandRollback());

    if (workflowExecution.isOnDemandRollback() && workflowExecution.getOriginalExecution() != null
        && workflowExecution.getOriginalExecution().getExecutionId() != null) {
      stringData.put(EventProcessor.ORIGINAL_EXECUTION_ID, workflowExecution.getOriginalExecution().getExecutionId());
    }

    if (metadata.isEmpty()) {
      booleanData.put(EventProcessor.MANUALLY_ROLLED_BACK, false);
    } else {
      booleanData.put(EventProcessor.MANUALLY_ROLLED_BACK, (boolean) metadata.get("manuallyRolledBack"));
    }

    if (!Lists.isNullOrEmpty(workflowExecution.getWorkflowIds())) {
      listData.put(EventProcessor.WORKFLOWS, workflowExecution.getWorkflowIds());
    }
    stringData.put(EventProcessor.ACCOUNTID, accountId);
    longData.put(EventProcessor.DURATION, workflowExecution.getDuration());
    longData.put(EventProcessor.ROLLBACK_DURATION, workflowExecution.getRollbackDuration());
    int instancesDeployed = workflowExecutionService.getInstancesDeployedFromExecution(workflowExecution);
    integerData.put(EventProcessor.INSTANCES_DEPLOYED, instancesDeployed);

    objectData.put(
        EventProcessor.TAGS, workflowExecutionService.getDeploymentTags(accountId, workflowExecution.getTags()));

    List<String> infraDefinitionIds = workflowExecution.getInfraDefinitionIds();
    if (!Lists.isNullOrEmpty(infraDefinitionIds)) {
      listData.put(EventProcessor.INFRA_DEFINITIONS, infraDefinitionIds);
    }
    List<String> infraMappingIds = workflowExecution.getInfraMappingIds();
    if (!Lists.isNullOrEmpty(infraMappingIds)) {
      listData.put(EventProcessor.INFRA_MAPPINGS, infraMappingIds);
    }

    TimeSeriesEventInfo eventInfo = TimeSeriesEventInfo.builder()
                                        .accountId(accountId)
                                        .stringData(stringData)
                                        .listData(listData)
                                        .longData(longData)
                                        .integerData(integerData)
                                        .booleanData(booleanData)
                                        .data(objectData)
                                        .build();
    if (isEmpty(eventInfo.getListData())) {
      log.info("TimeSeriesEventInfo has listData empty eventInfo=[{}]", eventInfo);
    }
    return DeploymentTimeSeriesEvent.builder().timeSeriesEventInfo(eventInfo).build();
  }

  public void publishInstanceTimeSeries(String accountId, long timestamp, List<Instance> instances) {
    if (isEmpty(instances)) {
      return;
    }

    List<TimeSeriesBatchEventInfo.DataPoint> dataPointList = new ArrayList<>();

    // key - infraMappingId, value - Set<Instance>
    Map<String, List<Instance>> infraMappingInstancesMap =
        instances.stream().collect(groupingBy(Instance::getInfraMappingId));

    infraMappingInstancesMap.values().forEach(instanceList -> {
      if (isEmpty(instanceList)) {
        return;
      }

      int size = instanceList.size();
      Instance instance = instanceList.get(0);
      Map<String, Object> data = new HashMap<>();
      data.put(EventProcessor.ACCOUNTID, instance.getAccountId());
      data.put(EventProcessor.APPID, instance.getAppId());
      data.put(EventProcessor.SERVICEID, instance.getServiceId());
      data.put(EventProcessor.ENVID, instance.getEnvId());
      data.put(EventProcessor.INFRAMAPPINGID, instance.getInfraMappingId());
      data.put(EventProcessor.CLOUDPROVIDERID, instance.getComputeProviderId());
      data.put(EventProcessor.INSTANCETYPE, instance.getInstanceType().name());
      data.put(EventProcessor.ARTIFACTID, instance.getLastArtifactId());
      data.put(EventProcessor.INSTANCECOUNT, size);

      DataPoint dataPoint = DataPoint.builder().data(data).build();
      dataPointList.add(dataPoint);
    });

    TimeSeriesBatchEventInfo eventInfo = TimeSeriesBatchEventInfo.builder()
                                             .accountId(accountId)
                                             .timestamp(timestamp)
                                             .dataPointList(dataPointList)
                                             .build();
    InstanceEvent instanceEvent =
        InstanceEvent.builder().accountId(accountId).timeSeriesBatchEventInfo(eventInfo).build();
    instanceTimeSeriesEventQueue.send(instanceEvent);
  }
}
