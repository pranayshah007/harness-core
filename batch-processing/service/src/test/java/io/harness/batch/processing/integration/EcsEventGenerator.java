/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.integration;

import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.Ec2InstanceInfo;
import io.harness.event.payloads.Ec2Lifecycle;
import io.harness.event.payloads.EcsContainerInstanceDescription;
import io.harness.event.payloads.EcsContainerInstanceInfo;
import io.harness.event.payloads.EcsContainerInstanceLifecycle;
import io.harness.event.payloads.EcsSyncEvent;
import io.harness.event.payloads.EcsTaskDescription;
import io.harness.event.payloads.EcsTaskInfo;
import io.harness.event.payloads.EcsTaskLifecycle;
import io.harness.event.payloads.EcsUtilization;
import io.harness.event.payloads.EcsUtilization.MetricValue;
import io.harness.event.payloads.InstanceState;
import io.harness.event.payloads.Lifecycle;
import io.harness.event.payloads.Lifecycle.EventType;
import io.harness.event.payloads.ReservedResource;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface EcsEventGenerator {
  double INSTANCE_CPU = 512;
  int INSTANCE_STATE_CODE = 16;
  double INSTANCE_MEMORY = 1024;
  String INSTANCE_TYPE = "t2.small";
  String OPERATING_SYSTEM = "linux";
  String INSTANCE_STATE_NAME = "running";
  String DEFAULT_AWS_REGION = "us-east-1";

  default PublishedMessage getEc2InstanceInfoMessage(
      String instanceId, String accountId, String clusterArn, String clusterId) {
    InstanceState instanceState =
        InstanceState.newBuilder().setCode(INSTANCE_STATE_CODE).setName(INSTANCE_STATE_NAME).build();

    Ec2InstanceInfo ec2InstanceInfo = Ec2InstanceInfo.newBuilder()
                                          .setInstanceId(instanceId)
                                          .setClusterArn(clusterArn)
                                          .setClusterId(clusterId)
                                          .setInstanceType(INSTANCE_TYPE)
                                          .setRegion(DEFAULT_AWS_REGION)
                                          .setInstanceState(instanceState)
                                          .build();
    return getPublishedMessage(accountId, ec2InstanceInfo);
  }

  default PublishedMessage getEc2InstanceLifecycleMessage(
      Timestamp timestamp, EventType eventType, String instanceId, String accountId, String clusterId) {
    Ec2Lifecycle ec2Lifecycle = Ec2Lifecycle.newBuilder()
                                    .setLifecycle(Lifecycle.newBuilder()
                                                      .setInstanceId(instanceId)
                                                      .setClusterId(clusterId)
                                                      .setType(eventType)
                                                      .setTimestamp(timestamp))
                                    .build();
    return getPublishedMessage(accountId, ec2Lifecycle);
  }

  default PublishedMessage getContainerInstanceLifecycleMessage(
      Timestamp timestamp, EventType eventType, String instanceId, String accountId, String clusterId) {
    EcsContainerInstanceLifecycle containerInstanceLifecycle = EcsContainerInstanceLifecycle.newBuilder()
                                                                   .setLifecycle(Lifecycle.newBuilder()
                                                                                     .setInstanceId(instanceId)
                                                                                     .setClusterId(clusterId)
                                                                                     .setType(eventType)
                                                                                     .setTimestamp(timestamp))
                                                                   .build();
    return getPublishedMessage(accountId, containerInstanceLifecycle);
  }

  default PublishedMessage getTaskLifecycleMessage(
      Timestamp timestamp, EventType eventType, String instanceId, String accountId, String clusterId) {
    EcsTaskLifecycle taskLifecycle = EcsTaskLifecycle.newBuilder()
                                         .setLifecycle(Lifecycle.newBuilder()
                                                           .setInstanceId(instanceId)
                                                           .setClusterId(clusterId)
                                                           .setType(eventType)
                                                           .setTimestamp(timestamp))
                                         .build();
    return getPublishedMessage(accountId, taskLifecycle);
  }

  default ReservedResource getReservedResource() {
    return ReservedResource.newBuilder().setMemory(INSTANCE_MEMORY).setCpu(INSTANCE_CPU).build();
  }

  default PublishedMessage getContainerInstanceInfoMessage(String containerInstanceArn, String instanceId,
      String settingId, String clusterArn, String accountId, String clusterId) {
    EcsContainerInstanceDescription containerInstanceDescription = EcsContainerInstanceDescription.newBuilder()
                                                                       .setRegion(DEFAULT_AWS_REGION)
                                                                       .setOperatingSystem(OPERATING_SYSTEM)
                                                                       .setClusterArn(clusterArn)
                                                                       .setClusterId(clusterArn)
                                                                       .setEc2InstanceId(instanceId)
                                                                       .setSettingId(settingId)
                                                                       .setClusterId(clusterId)
                                                                       .setContainerInstanceArn(containerInstanceArn)
                                                                       .build();
    EcsContainerInstanceInfo ecsContainerInstanceInfo =
        EcsContainerInstanceInfo.newBuilder()
            .setEcsContainerInstanceDescription(containerInstanceDescription)
            .setEcsContainerInstanceResource(getReservedResource())
            .build();
    return getPublishedMessage(accountId, ecsContainerInstanceInfo);
  }

  default PublishedMessage getTaskInfoMessage(String taskId, String serviceName, String launchType,
      String containerInstanceArn, String clusterArn, String accountId, String clusterId) {
    EcsTaskDescription ecsTaskDescription = EcsTaskDescription.newBuilder()
                                                .setRegion(DEFAULT_AWS_REGION)
                                                .setClusterArn(clusterArn)
                                                .setClusterId(clusterId)
                                                .setContainerInstanceArn(containerInstanceArn)
                                                .setTaskArn(taskId)
                                                .setServiceName(serviceName)
                                                .setLaunchType(launchType)
                                                .build();

    EcsTaskInfo ecsTaskInfo = EcsTaskInfo.newBuilder()
                                  .setEcsTaskDescription(ecsTaskDescription)
                                  .setEcsTaskResource(getReservedResource())
                                  .build();
    return getPublishedMessage(accountId, ecsTaskInfo);
  }

  default PublishedMessage getEcsSyncEventMessage(String accountId, String settingId, String clusterId,
      String clusterArn, List<String> activeTaskArns, List<String> activeEc2InstanceArns,
      List<String> activeContainerInstanceArns, Timestamp lastProcessedTimestamp) {
    EcsSyncEvent ecsSyncEvent = EcsSyncEvent.newBuilder()
                                    .setClusterArn(clusterArn)
                                    .setClusterId(clusterId)
                                    .setSettingId(settingId)
                                    .addAllActiveTaskArns(activeTaskArns)
                                    .addAllActiveEc2InstanceArns(activeEc2InstanceArns)
                                    .addAllActiveContainerInstanceArns(activeContainerInstanceArns)
                                    .setLastProcessedTimestamp(lastProcessedTimestamp)
                                    .build();
    return getPublishedMessage(accountId, ecsSyncEvent);
  }

  default PublishedMessage getEcsUtilizationMetricsMessage(String accountId, String clusterName, String clusterArn,
      String serviceName, String serviceArn, String settingId, String clusterId) {
    EcsUtilization ecsUtilization =
        EcsUtilization.newBuilder()
            .setClusterArn(clusterArn)
            .setClusterName(clusterName)
            .setServiceArn(serviceArn)
            .setServiceName(serviceName)
            .setSettingId(settingId)
            .setClusterId(clusterId)
            .addMetricValues(MetricValue.newBuilder()
                                 .setStatistic("Maximum")
                                 .setMetricName("MemoryUtilization")
                                 .addValues(1024.0)
                                 .addValues(2048.0)
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(12000000).build())
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(14000000).build())
                                 .build())
            .addMetricValues(MetricValue.newBuilder()
                                 .setStatistic("Average")
                                 .setMetricName("MemoryUtilization")
                                 .addValues(1024.0)
                                 .addValues(2048.0)
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(12000000).build())
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(14000000).build())
                                 .build())
            .addMetricValues(MetricValue.newBuilder()
                                 .setStatistic("Maximum")
                                 .setMetricName("CPUUtilization")
                                 .addValues(50.0)
                                 .addValues(60.0)
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(12000000).build())
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(14000000).build())
                                 .build())
            .addMetricValues(MetricValue.newBuilder()
                                 .setStatistic("Average")
                                 .setMetricName("CPUUtilization")
                                 .addValues(50.0)
                                 .addValues(60.0)
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(12000000).build())
                                 .addTimestamps(Timestamp.newBuilder().setSeconds(14000000).build())
                                 .build())
            .build();
    return getPublishedMessage(accountId, ecsUtilization);
  }

  default InstanceData createEc2InstanceData(
      String instanceId, String accountId, io.harness.ccm.commons.beans.InstanceState instanceState) {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, INSTANCE_TYPE);
    metaData.put(InstanceMetaDataConstants.REGION, DEFAULT_AWS_REGION);
    return InstanceData.builder()
        .instanceId(instanceId)
        .accountId(accountId)
        .instanceType(InstanceType.EC2_INSTANCE)
        .metaData(metaData)
        .totalResource(Resource.builder().cpuUnits(1024.0).memoryMb(1024.0).build())
        .build();
  }

  default InstanceData createContainerInstanceData(
      String instanceId, String accountId, io.harness.ccm.commons.beans.InstanceState instanceState) {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, INSTANCE_TYPE);
    metaData.put(InstanceMetaDataConstants.REGION, DEFAULT_AWS_REGION);
    Resource resource = Resource.builder().cpuUnits(INSTANCE_CPU).memoryMb(INSTANCE_MEMORY).build();
    return InstanceData.builder()
        .instanceId(instanceId)
        .accountId(accountId)
        .totalResource(resource)
        .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
        .metaData(metaData)
        .build();
  }

  default InstanceData createTaskInstanceData(
      String instanceId, String accountId, io.harness.ccm.commons.beans.InstanceState instanceState) {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.REGION, DEFAULT_AWS_REGION);
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    Resource resource = Resource.builder().cpuUnits(INSTANCE_CPU).memoryMb(INSTANCE_MEMORY).build();
    return InstanceData.builder()
        .instanceId(instanceId)
        .totalResource(resource)
        .accountId(accountId)
        .instanceState(instanceState)
        .metaData(metaData)
        .instanceType(InstanceType.ECS_TASK_FARGATE)
        .build();
  }

  default PublishedMessage getPublishedMessage(String accountId, Message message) {
    Any payload = Any.pack(message);
    return PublishedMessage.builder()
        .accountId(accountId)
        .data(payload.toByteArray())
        .type(message.getClass().getName())
        .build();
  }

  default List<PublishedMessage> getMessageList(PublishedMessage publishedMessage) {
    return Collections.singletonList(publishedMessage);
  }

  default List<io.harness.ccm.commons.beans.InstanceState> getActiveInstanceState() {
    return new ArrayList<>(Arrays.asList(
        io.harness.ccm.commons.beans.InstanceState.INITIALIZING, io.harness.ccm.commons.beans.InstanceState.RUNNING));
  }

  default List<io.harness.ccm.commons.beans.InstanceState> getStoppedInstanceState() {
    return Collections.singletonList(io.harness.ccm.commons.beans.InstanceState.STOPPED);
  }
}
