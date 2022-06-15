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
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

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
@Qualifier("K8sDummyInstanceDataGenerator")
public class K8sDummyInstanceDataGenerator {
  @Autowired protected InstanceDataDao instanceDataDao;

  private static String accountId = "account_id";
  private static String settingId = "setting_id";
  private static String nodeId = "node_id";
  private static String nodeName = "node_name";
  private static String podId = "pod_id";
  private static String podName = "pod_name";
  private static String clusterId = "cluster_id";
  private static String clusterName = "cluster_name";
  private static String namespace = "namespace";
  private static List<String> instanceFamilyList = new ArrayList<>(
      Arrays.asList("n1-standard-4", "n1-standard-96", "n1-highmem-2", "n1-highcpu-32", "n2-standard-4"));

  private static double cpuBaseline = 1000;
  private static double memoryBaseline = 102400000;
  private static long oneDayMillis = 86400000;
  private static SecureRandom secureRandom = new SecureRandom();

  private List<InstanceData> createK8sDummyData(int numberOfCluster, List<Integer> listOfNumberOfNodesInEachCluster,
      List<List<Integer>> listOfNumberOfPodsInEachNode, long startTime, long endTime,
      List<HarnessServiceInfo> harnessServiceInfoList) {
    String operatingSystem = "linux";
    String region = "us-central1";
    String instanceFamily = instanceFamilyList.get(secureRandom.nextInt(instanceFamilyList.size()));

    List<InstanceData> listOfInstanceData = new ArrayList<>();
    IntStream.range(0, numberOfCluster)
        .forEach(clusterNumber
            -> IntStream.range(0, listOfNumberOfNodesInEachCluster.get(clusterNumber)).forEach(nodeNumber -> {
          Map<String, String> nodeMetadataMap = new HashMap<>();
          nodeMetadataMap.put(InstanceMetaDataConstants.NODE_UID, nodeId);
          nodeMetadataMap.put(InstanceMetaDataConstants.NODE_NAME, nodeName);
          nodeMetadataMap.put(InstanceMetaDataConstants.INSTANCE_FAMILY, instanceFamily);
          nodeMetadataMap.put(InstanceMetaDataConstants.OPERATING_SYSTEM, operatingSystem);
          nodeMetadataMap.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
          nodeMetadataMap.put(InstanceMetaDataConstants.REGION, region);
          nodeMetadataMap.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());

          double nodeCpu = cpuBaseline * (secureRandom.nextInt(3) + 1);
          double nodeMemory = memoryBaseline * (secureRandom.nextInt(5) + 1);

          long nodeStartTimeLocal = startTime + (secureRandom.nextInt(10) * oneDayMillis);
          long nodeEndTimeLocal = nodeStartTimeLocal + (secureRandom.nextInt(10) * oneDayMillis);

          InstanceData nodeInstanceInfo =
              InstanceData.builder()
                  .accountId(accountId)
                  .settingId(settingId)
                  .instanceId(nodeId + nodeNumber)
                  .instanceName(nodeName + nodeNumber)
                  .clusterId(clusterId + clusterNumber)
                  .clusterName(clusterName + clusterNumber)
                  .instanceType(InstanceType.K8S_NODE)
                  .totalResource(Resource.builder().cpuUnits(nodeCpu).memoryMb(nodeMemory).build())
                  .metaData(nodeMetadataMap)
                  .usageStartTime(Instant.ofEpochMilli(nodeStartTimeLocal))
                  .createdAt(nodeStartTimeLocal)
                  .lastUpdatedAt(nodeStartTimeLocal)
                  .build();
          if (nodeEndTimeLocal <= endTime) {
            nodeInstanceInfo.setUsageStopTime(Instant.ofEpochMilli(nodeEndTimeLocal));
          }
          listOfInstanceData.add(nodeInstanceInfo);
          int numberOfPods = listOfNumberOfPodsInEachNode.get(clusterNumber).get(nodeNumber);

          IntStream.range(0, numberOfPods).forEach(podNumber -> {
            Map<String, String> podMetadataMap = new HashMap<>();
            podMetadataMap.put(InstanceMetaDataConstants.NAMESPACE, namespace + podNumber);
            podMetadataMap.put(InstanceMetaDataConstants.POD_NAME, podName + podNumber);
            podMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, nodeId + nodeNumber);
            podMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_CPU, String.valueOf(nodeCpu));
            podMetadataMap.put(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY, String.valueOf(nodeMemory));
            podMetadataMap.put(InstanceMetaDataConstants.INSTANCE_FAMILY, instanceFamily);
            podMetadataMap.put(InstanceMetaDataConstants.OPERATING_SYSTEM, operatingSystem);
            podMetadataMap.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
            podMetadataMap.put(InstanceMetaDataConstants.REGION, region);
            podMetadataMap.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());

            long podStartTimeLocal = nodeStartTimeLocal + (secureRandom.nextInt(10) * oneDayMillis);
            long podEndTimeLocal = podStartTimeLocal + (secureRandom.nextInt(10) * oneDayMillis);

            InstanceData podInstanceInfo =
                InstanceData.builder()
                    .accountId(accountId)
                    .settingId(settingId)
                    .instanceId(podId + podNumber)
                    .instanceName(podName + podNumber)
                    .clusterId(clusterId + clusterNumber)
                    .clusterName(clusterName + clusterNumber)
                    .instanceType(InstanceType.K8S_POD)
                    .totalResource(
                        Resource.builder().cpuUnits(nodeCpu / numberOfPods).memoryMb(nodeMemory / numberOfPods).build())
                    .metaData(podMetadataMap)
                    .usageStartTime(Instant.ofEpochMilli(podEndTimeLocal))
                    .createdAt(podStartTimeLocal)
                    .lastUpdatedAt(podStartTimeLocal)
                    .harnessServiceInfo(harnessServiceInfoList.get(secureRandom.nextInt(harnessServiceInfoList.size())))
                    .build();
            if (podEndTimeLocal <= nodeEndTimeLocal) {
              podInstanceInfo.setUsageStopTime(Instant.ofEpochMilli(podEndTimeLocal));
            }
            listOfInstanceData.add(podInstanceInfo);
          });
        }));
    return listOfInstanceData;
  }

  public boolean createAndInsertDummyData(int numberOfCluster, List<Integer> listOfNumberOfNodesInEachCluster,
      List<List<Integer>> listOfNumberOfPodsInEachNode, long startTime, long endTime,
      List<HarnessServiceInfo> harnessServiceInfoList) {
    List<InstanceData> listOfDummyInstanceData = createK8sDummyData(numberOfCluster, listOfNumberOfNodesInEachCluster,
        listOfNumberOfPodsInEachNode, startTime, endTime, harnessServiceInfoList);
    writeData(listOfDummyInstanceData);
    return true;
  }

  private void writeData(List<InstanceData> instanceDataList) {
    instanceDataList.forEach(instanceData -> instanceDataDao.create(instanceData));
  }
}
