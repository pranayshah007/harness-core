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
import io.harness.queue.QueuePublisher;

import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.api.InstanceEvent;
import software.wings.beans.EnvSummary;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.service.impl.WorkflowExecutionServiceHelper;
import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo;
import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo.DataPoint;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  SimpleDateFormat sdf;

  public UsageMetricsEventPublisher() {
    sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    sdf.setTimeZone(TimeZone.getTimeZone(TimeZone.getTimeZone("Etc/UTC").toZoneId()));
  }

  public void publishDeploymentTimeSeriesEvent(String accountId, WorkflowExecution workflowExecution) {
    DeploymentTimeSeriesEvent event = constructDeploymentTimeSeriesEvent(accountId, workflowExecution);
    executorService.submit(() -> {
      try {
        deploymentTimeSeriesEventQueue.send(event);
      } catch (Exception e) {
        log.error("Failed to publish deployment time series event:[{}]", event.getId(), e);
      }
    });
  }

  public DeploymentTimeSeriesEvent constructDeploymentTimeSeriesEvent(
      String accountId, WorkflowExecution workflowExecution) {
    log.info("Reporting execution");
    Map<String, String> stringData = new HashMap<>();
    Map<String, List<String>> listData = new HashMap<>();
    Map<String, Long> longData = new HashMap<>();
    Map<String, Integer> integerData = new HashMap<>();
    Map<String, Object> objectData = new HashMap<>();
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
          workflowExecution.getArtifacts().stream().map(Artifact::getBuildNo).collect(Collectors.toList()));
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

    TimeSeriesEventInfo eventInfo = TimeSeriesEventInfo.builder()
                                        .accountId(accountId)
                                        .stringData(stringData)
                                        .listData(listData)
                                        .longData(longData)
                                        .integerData(integerData)
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
