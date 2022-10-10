/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dummydata;

import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

import com.amazonaws.services.ecs.model.LaunchType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Qualifier("EcsDummyInstanceDataGenerator")
public class EcsDummyInstanceDataGenerator {
  @Autowired protected InstanceDataDao instanceDataDao;

  private static String accountId = "account_id";
  private static String instanceId = "instance_id";
  private static String clusterId = "cluster_id";
  private static String clusterName = "cluster_name";
  private static String settingId = "setting_id";
  private static String containerId = "container_id";
  private static String containerName = "container_name";
  private static String taskId = "task_id";
  private static String taskName = "task_name";
  private static List<String> instanceFamilyList =
      new ArrayList<>(Arrays.asList("t3.nano", "t3.medium", "t2.nano", "t2.medium"));

  private static double cpuBaseline = 1000;
  private static double memoryBaseline = 102400000;
  private static long oneDayMillis = 86400000;
  private static SecureRandom secureRandom = new SecureRandom();

  private List<InstanceData> createEcsDummyData(int numberOfInstances,
      List<Integer> listOfNumberOfContainersInEachInstance, List<List<Integer>> listOfNumberOfTasksInEachContainer,
      long startTime, long endTime, List<HarnessServiceInfo> harnessServiceInfoList) {
    String operatingSystem = "linux";
    String region = "us-east-2";
    String instanceFamily = instanceFamilyList.get(secureRandom.nextInt(instanceFamilyList.size()));

    List<InstanceData> listOfInstanceData = new ArrayList<>();
    IntStream.range(0, numberOfInstances).forEach(instanceNumber -> {
      long containerStartTimeLocal = startTime + (secureRandom.nextInt(10) * oneDayMillis);
      Map<String, String> instanceMetadataMap = new HashMap<>();
      instanceMetadataMap.put(InstanceMetaDataConstants.INSTANCE_FAMILY, instanceFamily);
      instanceMetadataMap.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
      instanceMetadataMap.put(InstanceMetaDataConstants.REGION, region);

      InstanceData InstanceInfo = InstanceData.builder()
                                      .accountId(accountId)
                                      .instanceId(instanceId + instanceNumber)
                                      .clusterName(clusterName + instanceNumber)
                                      .instanceType(InstanceType.EC2_INSTANCE)
                                      .metaData(instanceMetadataMap)
                                      .instanceState(InstanceState.RUNNING)
                                      .usageStartTime(Instant.ofEpochMilli(containerStartTimeLocal))
                                      .createdAt(containerStartTimeLocal)
                                      .lastUpdatedAt(containerStartTimeLocal)
                                      .build();

      listOfInstanceData.add(InstanceInfo);

      IntStream.range(0, listOfNumberOfContainersInEachInstance.get(instanceNumber)).forEach(containerNumber -> {
        Map<String, String> containerMetadataMap = new HashMap<>();
        containerMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, instanceId + instanceNumber);
        containerMetadataMap.put(InstanceMetaDataConstants.EC2_INSTANCE_ID, instanceId + instanceNumber);
        containerMetadataMap.put(InstanceMetaDataConstants.INSTANCE_FAMILY, instanceFamily);
        containerMetadataMap.put(InstanceMetaDataConstants.OPERATING_SYSTEM, operatingSystem);
        containerMetadataMap.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
        containerMetadataMap.put(InstanceMetaDataConstants.REGION, region);
        containerMetadataMap.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());

        double containerCpu = cpuBaseline * (secureRandom.nextInt(3) + 1);
        double containerMemory = memoryBaseline * (secureRandom.nextInt(5) + 1);
        long containerEndTimeLocal = containerStartTimeLocal + (secureRandom.nextInt(10) * oneDayMillis);

        InstanceData containerInstanceInfo =
            InstanceData.builder()
                .accountId(accountId)
                .settingId(settingId)
                .instanceId(containerId + containerNumber)
                .instanceName(containerName + containerNumber)
                .clusterId(clusterId + instanceNumber)
                .clusterName(clusterName + instanceNumber)
                .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
                .totalResource(Resource.builder().cpuUnits(containerCpu).memoryMb(containerMemory).build())
                .metaData(containerMetadataMap)
                .instanceState(InstanceState.RUNNING)
                .usageStartTime(Instant.ofEpochMilli(containerStartTimeLocal))
                .createdAt(containerStartTimeLocal)
                .lastUpdatedAt(containerStartTimeLocal)
                .build();
        if (containerEndTimeLocal <= endTime) {
          containerInstanceInfo.setInstanceState(InstanceState.STOPPED);
          containerInstanceInfo.setUsageStopTime(Instant.ofEpochMilli(containerEndTimeLocal));
        }
        listOfInstanceData.add(containerInstanceInfo);
        int numberOfTasks = listOfNumberOfTasksInEachContainer.get(instanceNumber).get(containerNumber);

        IntStream.range(0, numberOfTasks).forEach(taskNumber -> {
          Map<String, String> taskMetadataMap = new HashMap<>();
          taskMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, containerId + containerNumber);
          taskMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_CPU, String.valueOf(containerCpu));
          taskMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY, String.valueOf(containerMemory));
          taskMetadataMap.put(InstanceMetaDataConstants.OPERATING_SYSTEM, operatingSystem);
          taskMetadataMap.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
          taskMetadataMap.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
          taskMetadataMap.put(InstanceMetaDataConstants.ECS_SERVICE_ARN, "");
          taskMetadataMap.put(InstanceMetaDataConstants.ECS_SERVICE_NAME, "");
          taskMetadataMap.put(InstanceMetaDataConstants.CONTAINER_INSTANCE_ARN, "");
          taskMetadataMap.put(InstanceMetaDataConstants.INSTANCE_FAMILY, instanceFamily);
          taskMetadataMap.put(InstanceMetaDataConstants.REGION, region);
          taskMetadataMap.put(InstanceMetaDataConstants.LAUNCH_TYPE, LaunchType.EC2.toString());

          long taskStartTimeLocal = containerStartTimeLocal + (secureRandom.nextInt(10) * oneDayMillis);
          long taskEndTimeLocal = taskStartTimeLocal + (secureRandom.nextInt(10) * oneDayMillis);

          InstanceData taskInstanceInfo =
              InstanceData.builder()
                  .accountId(accountId)
                  .settingId(settingId)
                  .instanceId(taskId + taskNumber)
                  .instanceName(taskName + taskNumber)
                  .clusterId(clusterId + instanceNumber)
                  .clusterName(clusterName + instanceNumber)
                  .instanceType(InstanceType.ECS_TASK_EC2)
                  .totalResource(Resource.builder()
                                     .cpuUnits(containerCpu / numberOfTasks)
                                     .memoryMb(containerMemory / numberOfTasks)
                                     .build())
                  .metaData(taskMetadataMap)
                  .usageStartTime(Instant.ofEpochMilli(taskStartTimeLocal))
                  .createdAt(taskStartTimeLocal)
                  .lastUpdatedAt(taskStartTimeLocal)
                  .harnessServiceInfo(harnessServiceInfoList.get(secureRandom.nextInt(harnessServiceInfoList.size())))
                  .build();
          if (taskEndTimeLocal <= containerEndTimeLocal) {
            taskInstanceInfo.setInstanceState(InstanceState.STOPPED);
            taskInstanceInfo.setUsageStopTime(Instant.ofEpochMilli(taskEndTimeLocal));
          }
          listOfInstanceData.add(taskInstanceInfo);
        });
      });
    });
    return listOfInstanceData;
  }

  public boolean createAndInsertDummyData(int numberOfInstances, List<Integer> listOfNumberOfContainersInEachInstance,
      List<List<Integer>> listOfNumberOfTasksInEachContainer, long startTime, long endTime,
      List<HarnessServiceInfo> harnessServiceInfoList) {
    List<InstanceData> listOfDummyInstanceData =
        createEcsDummyData(numberOfInstances, listOfNumberOfContainersInEachInstance,
            listOfNumberOfTasksInEachContainer, startTime, endTime, harnessServiceInfoList);
    writeData(listOfDummyInstanceData);
    return true;
  }

  private void writeData(List<InstanceData> instanceDataList) {
    instanceDataList.forEach(instanceData -> instanceDataDao.create(instanceData));
  }
}
