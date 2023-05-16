/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.RecommendationsIgnoreListDAO;
import io.harness.ccm.commons.entities.recommendations.RecommendationAzureVmId;
import io.harness.ccm.commons.entities.recommendations.RecommendationEC2InstanceId;
import io.harness.ccm.commons.entities.recommendations.RecommendationECSServiceId;
import io.harness.ccm.commons.entities.recommendations.RecommendationNodepoolId;
import io.harness.ccm.commons.entities.recommendations.RecommendationWorkloadId;
import io.harness.ccm.commons.entities.recommendations.RecommendationsIgnoreList;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsIgnoreResourcesDTO;
import io.harness.data.structure.CollectionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RecommendationsIgnoreListService {
  @Inject private RecommendationsIgnoreListDAO ignoreListDAO;
  @Inject private K8sRecommendationDAO k8sRecommendationDAO;
  @Inject private ECSRecommendationDAO ecsRecommendationDAO;
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;
  @Inject private AzureRecommendationDAO azureRecommendationDAO;

  public RecommendationsIgnoreList getIgnoreList(String accountId) {
    Optional<RecommendationsIgnoreList> ignoreListFromDB = ignoreListDAO.get(accountId);
    if (ignoreListFromDB.isEmpty()) {
      return RecommendationsIgnoreList.builder()
          .accountId(accountId)
          .workloadIgnoreList(new HashSet<>())
          .nodepoolIgnoreList(new HashSet<>())
          .ecsServiceIgnoreList(new HashSet<>())
          .ec2InstanceIgnoreList(new HashSet<>())
          .azureVmIgnoreList(new HashSet<>())
          .build();
    }
    RecommendationsIgnoreList ignoreList = ignoreListFromDB.get();
    ignoreList.setWorkloadIgnoreList(CollectionUtils.emptyIfNull(ignoreList.getWorkloadIgnoreList()));
    ignoreList.setNodepoolIgnoreList(CollectionUtils.emptyIfNull(ignoreList.getNodepoolIgnoreList()));
    ignoreList.setEcsServiceIgnoreList(CollectionUtils.emptyIfNull(ignoreList.getEcsServiceIgnoreList()));
    ignoreList.setEc2InstanceIgnoreList(CollectionUtils.emptyIfNull(ignoreList.getEc2InstanceIgnoreList()));
    ignoreList.setAzureVmIgnoreList(CollectionUtils.emptyIfNull(ignoreList.getAzureVmIgnoreList()));
    return ignoreList;
  }

  public RecommendationsIgnoreList addResources(
      String accountId, RecommendationsIgnoreResourcesDTO ignoreResourcesDTO) {
    RecommendationsIgnoreList ignoreList = getIgnoreList(accountId);
    Set<RecommendationWorkloadId> workloadIgnoreList =
        ignoreWorkloads(accountId, ignoreList.getWorkloadIgnoreList(), ignoreResourcesDTO.getWorkloads());
    Set<RecommendationNodepoolId> nodepoolIgnoreList =
        ignoreNodepools(accountId, ignoreList.getNodepoolIgnoreList(), ignoreResourcesDTO.getNodepools());
    Set<RecommendationECSServiceId> ecsServiceIgnoreList =
        ignoreECSServices(accountId, ignoreList.getEcsServiceIgnoreList(), ignoreResourcesDTO.getEcsServices());
    Set<RecommendationEC2InstanceId> ec2InstanceIgnoreList =
        ignoreEC2Instances(accountId, ignoreList.getEc2InstanceIgnoreList(), ignoreResourcesDTO.getEc2Instances());
    Set<RecommendationAzureVmId> azureVmIgnoreList =
        ignoreAzureVmInstances(accountId, ignoreList.getAzureVmIgnoreList(), ignoreResourcesDTO.getAzureVmIds());

    ignoreListDAO.save(RecommendationsIgnoreList.builder()
                           .accountId(accountId)
                           .workloadIgnoreList(workloadIgnoreList)
                           .nodepoolIgnoreList(nodepoolIgnoreList)
                           .ecsServiceIgnoreList(ecsServiceIgnoreList)
                           .ec2InstanceIgnoreList(ec2InstanceIgnoreList)
                           .azureVmIgnoreList(azureVmIgnoreList)
                           .build());
    return getIgnoreList(accountId);
  }

  public RecommendationsIgnoreList removeResources(
      String accountId, RecommendationsIgnoreResourcesDTO ignoreResourcesDTO) {
    RecommendationsIgnoreList ignoreList = getIgnoreList(accountId);
    Set<RecommendationWorkloadId> workloadIgnoreList =
        unIgnoreWorkloads(accountId, ignoreList.getWorkloadIgnoreList(), ignoreResourcesDTO.getWorkloads());
    Set<RecommendationNodepoolId> nodepoolIgnoreList =
        unIgnoreNodepools(accountId, ignoreList.getNodepoolIgnoreList(), ignoreResourcesDTO.getNodepools());
    Set<RecommendationECSServiceId> ecsServiceIgnoreList =
        unIgnoreECSServices(accountId, ignoreList.getEcsServiceIgnoreList(), ignoreResourcesDTO.getEcsServices());
    Set<RecommendationEC2InstanceId> ec2InstanceIgnoreList =
        unIgnoreEC2Instances(accountId, ignoreList.getEc2InstanceIgnoreList(), ignoreResourcesDTO.getEc2Instances());
    Set<RecommendationAzureVmId> azureVmIgnoreList =
        unIgnoreAzureVmInstances(accountId, ignoreList.getAzureVmIgnoreList(), ignoreResourcesDTO.getAzureVmIds());

    ignoreListDAO.save(RecommendationsIgnoreList.builder()
                           .accountId(accountId)
                           .workloadIgnoreList(workloadIgnoreList)
                           .nodepoolIgnoreList(nodepoolIgnoreList)
                           .ecsServiceIgnoreList(ecsServiceIgnoreList)
                           .ec2InstanceIgnoreList(ec2InstanceIgnoreList)
                           .azureVmIgnoreList(azureVmIgnoreList)
                           .build());
    return getIgnoreList(accountId);
  }

  // Marks a Workload recommendation as IGNORED if it is in ignoreList
  public void updateWorkloadRecommendationState(
      String recommendationId, String accountId, String clusterName, String namespace, String workloadName) {
    RecommendationState recommendationState = k8sRecommendationDAO.getRecommendationState(recommendationId);
    if (recommendationState != null && recommendationState.equals(RecommendationState.OPEN)) {
      Optional<RecommendationsIgnoreList> recommendationsIgnoreList = ignoreListDAO.get(accountId);
      if (recommendationsIgnoreList.isPresent()) {
        RecommendationsIgnoreList ignoreList = recommendationsIgnoreList.get();
        if (ignoreList.getWorkloadIgnoreList() != null
            && ignoreList.getWorkloadIgnoreList().contains(RecommendationWorkloadId.builder()
                                                               .clusterName(clusterName)
                                                               .namespace(namespace)
                                                               .workloadName(workloadName)
                                                               .build())) {
          k8sRecommendationDAO.updateRecommendationState(recommendationId, RecommendationState.IGNORED);
        }
      }
    }
  }

  // Marks a Node recommendation as IGNORED if it is in ignoreList
  public void updateNodeRecommendationState(
      String recommendationId, String accountId, String clusterName, String nodePoolName) {
    RecommendationState recommendationState = k8sRecommendationDAO.getRecommendationState(recommendationId);
    if (recommendationState != null && recommendationState.equals(RecommendationState.OPEN)) {
      Optional<RecommendationsIgnoreList> recommendationsIgnoreList = ignoreListDAO.get(accountId);
      if (recommendationsIgnoreList.isPresent()) {
        RecommendationsIgnoreList ignoreList = recommendationsIgnoreList.get();
        if (ignoreList.getNodepoolIgnoreList() != null
            && ignoreList.getNodepoolIgnoreList().contains(
                RecommendationNodepoolId.builder().clusterName(clusterName).nodepoolName(nodePoolName).build())) {
          k8sRecommendationDAO.updateRecommendationState(recommendationId, RecommendationState.IGNORED);
        }
      }
    }
  }

  // Marks an ECS recommendation as IGNORED if it is in ignoreList
  public void updateECSRecommendationState(
      String recommendationId, String accountId, String clusterName, String serviceName) {
    RecommendationState recommendationState = k8sRecommendationDAO.getRecommendationState(recommendationId);
    if (recommendationState != null && recommendationState.equals(RecommendationState.OPEN)) {
      Optional<RecommendationsIgnoreList> recommendationsIgnoreList = ignoreListDAO.get(accountId);
      if (recommendationsIgnoreList.isPresent()) {
        RecommendationsIgnoreList ignoreList = recommendationsIgnoreList.get();
        if (ignoreList.getEcsServiceIgnoreList() != null
            && ignoreList.getEcsServiceIgnoreList().contains(
                RecommendationECSServiceId.builder().clusterName(clusterName).ecsServiceName(serviceName).build())) {
          k8sRecommendationDAO.updateRecommendationState(recommendationId, RecommendationState.IGNORED);
        }
      }
    }
  }

  // Marks an EC2 recommendation as IGNORED if it is in ignoreList
  public void updateEC2RecommendationState(
      String recommendationId, String accountId, String awsAccountId, String instanceId) {
    RecommendationState recommendationState = k8sRecommendationDAO.getRecommendationState(recommendationId);
    if (recommendationState != null && recommendationState.equals(RecommendationState.OPEN)) {
      Optional<RecommendationsIgnoreList> recommendationsIgnoreList = ignoreListDAO.get(accountId);
      if (recommendationsIgnoreList.isPresent()) {
        RecommendationsIgnoreList ignoreList = recommendationsIgnoreList.get();
        if (ignoreList.getEc2InstanceIgnoreList() != null
            && ignoreList.getEc2InstanceIgnoreList().contains(
                RecommendationEC2InstanceId.builder().awsAccountId(awsAccountId).instanceId(instanceId).build())) {
          k8sRecommendationDAO.updateRecommendationState(recommendationId, RecommendationState.IGNORED);
        }
      }
    }
  }

  private Set<RecommendationWorkloadId> ignoreWorkloads(
      String accountId, Set<RecommendationWorkloadId> workloadIgnoreList, Set<RecommendationWorkloadId> addWorkloads) {
    if (addWorkloads != null) {
      List<RecommendationWorkloadId> toIgnore = new ArrayList<>();
      for (RecommendationWorkloadId workload : addWorkloads) {
        if (!workloadIgnoreList.contains(workload)) {
          toIgnore.add(workload);
        }
      }
      workloadIgnoreList.addAll(toIgnore);
      k8sRecommendationDAO.ignoreWorkloadRecommendations(accountId, toIgnore);
    }
    return workloadIgnoreList;
  }

  private Set<RecommendationNodepoolId> ignoreNodepools(
      String accountId, Set<RecommendationNodepoolId> nodepoolIgnoreList, Set<RecommendationNodepoolId> addNodepools) {
    if (addNodepools != null) {
      List<RecommendationNodepoolId> toIgnore = new ArrayList<>();
      for (RecommendationNodepoolId nodepool : addNodepools) {
        if (!nodepoolIgnoreList.contains(nodepool)) {
          toIgnore.add(nodepool);
        }
      }
      nodepoolIgnoreList.addAll(toIgnore);
      k8sRecommendationDAO.ignoreNodepoolRecommendations(accountId, toIgnore);
    }
    return nodepoolIgnoreList;
  }

  private Set<RecommendationECSServiceId> ignoreECSServices(String accountId,
      Set<RecommendationECSServiceId> ecsServiceIgnoreList, Set<RecommendationECSServiceId> addECSServices) {
    if (addECSServices != null) {
      List<RecommendationECSServiceId> toIgnore = new ArrayList<>();
      for (RecommendationECSServiceId ecsService : addECSServices) {
        if (!ecsServiceIgnoreList.contains(ecsService)) {
          toIgnore.add(ecsService);
        }
      }
      ecsServiceIgnoreList.addAll(toIgnore);
      ecsRecommendationDAO.ignoreECSRecommendations(accountId, toIgnore);
    }
    return ecsServiceIgnoreList;
  }

  private Set<RecommendationEC2InstanceId> ignoreEC2Instances(String accountId,
      Set<RecommendationEC2InstanceId> ec2InstanceIgnoreList, Set<RecommendationEC2InstanceId> addEC2Instances) {
    if (addEC2Instances != null) {
      List<RecommendationEC2InstanceId> toIgnore = new ArrayList<>();
      for (RecommendationEC2InstanceId ec2Instance : addEC2Instances) {
        if (!ec2InstanceIgnoreList.contains(ec2Instance)) {
          toIgnore.add(ec2Instance);
        }
      }
      ec2InstanceIgnoreList.addAll(toIgnore);
      ec2RecommendationDAO.ignoreEC2Recommendations(accountId, toIgnore);
    }
    return ec2InstanceIgnoreList;
  }

  private Set<RecommendationAzureVmId> ignoreAzureVmInstances(
      String accountId, Set<RecommendationAzureVmId> azureVmIgnoreList, Set<RecommendationAzureVmId> addAzureVms) {
    if (addAzureVms != null) {
      List<RecommendationAzureVmId> toIgnore = new ArrayList<>();
      for (RecommendationAzureVmId azureInstance : addAzureVms) {
        if (!azureVmIgnoreList.contains(azureInstance)) {
          toIgnore.add(azureInstance);
        }
      }
      azureVmIgnoreList.addAll(toIgnore);
      azureRecommendationDAO.ignoreAzureVmRecommendations(accountId, toIgnore);
    }
    return azureVmIgnoreList;
  }

  private Set<RecommendationWorkloadId> unIgnoreWorkloads(String accountId,
      Set<RecommendationWorkloadId> workloadIgnoreList, Set<RecommendationWorkloadId> removeWorkloads) {
    if (removeWorkloads != null) {
      List<RecommendationWorkloadId> toUnIgnore = new ArrayList<>();
      for (RecommendationWorkloadId workload : removeWorkloads) {
        if (workloadIgnoreList.contains(workload)) {
          workloadIgnoreList.remove(workload);
          toUnIgnore.add(workload);
        }
      }
      k8sRecommendationDAO.unignoreWorkloadRecommendations(accountId, toUnIgnore);
    }
    return workloadIgnoreList;
  }

  private Set<RecommendationNodepoolId> unIgnoreNodepools(String accountId,
      Set<RecommendationNodepoolId> nodepoolIgnoreList, Set<RecommendationNodepoolId> removeNodepools) {
    if (removeNodepools != null) {
      List<RecommendationNodepoolId> toUnIgnore = new ArrayList<>();
      for (RecommendationNodepoolId nodepool : removeNodepools) {
        if (nodepoolIgnoreList.contains(nodepool)) {
          nodepoolIgnoreList.remove(nodepool);
          toUnIgnore.add(nodepool);
        }
      }
      k8sRecommendationDAO.unignoreNodepoolRecommendations(accountId, toUnIgnore);
    }
    return nodepoolIgnoreList;
  }

  private Set<RecommendationECSServiceId> unIgnoreECSServices(String accountId,
      Set<RecommendationECSServiceId> ecsServiceIgnoreList, Set<RecommendationECSServiceId> removeECSServices) {
    if (removeECSServices != null) {
      List<RecommendationECSServiceId> toUnIgnore = new ArrayList<>();
      for (RecommendationECSServiceId ecsService : removeECSServices) {
        if (ecsServiceIgnoreList.contains(ecsService)) {
          ecsServiceIgnoreList.remove(ecsService);
          toUnIgnore.add(ecsService);
        }
      }
      ecsRecommendationDAO.unignoreECSRecommendations(accountId, toUnIgnore);
    }
    return ecsServiceIgnoreList;
  }

  private Set<RecommendationEC2InstanceId> unIgnoreEC2Instances(String accountId,
      Set<RecommendationEC2InstanceId> ec2InstanceIgnoreList, Set<RecommendationEC2InstanceId> removeEC2Instances) {
    if (removeEC2Instances != null) {
      List<RecommendationEC2InstanceId> toUnIgnore = new ArrayList<>();
      for (RecommendationEC2InstanceId ec2Instance : removeEC2Instances) {
        if (ec2InstanceIgnoreList.contains(ec2Instance)) {
          ec2InstanceIgnoreList.remove(ec2Instance);
          toUnIgnore.add(ec2Instance);
        }
      }
      ec2RecommendationDAO.unignoreEC2Recommendations(accountId, toUnIgnore);
    }
    return ec2InstanceIgnoreList;
  }

  private Set<RecommendationAzureVmId> unIgnoreAzureVmInstances(
      String accountId, Set<RecommendationAzureVmId> azureVmIgnoreList, Set<RecommendationAzureVmId> removeAzureVms) {
    if (removeAzureVms != null) {
      List<RecommendationAzureVmId> toUnIgnore = new ArrayList<>();
      for (RecommendationAzureVmId azureInstance : removeAzureVms) {
        if (azureVmIgnoreList.contains(azureInstance)) {
          azureVmIgnoreList.remove(azureInstance);
          toUnIgnore.add(azureInstance);
        }
      }
      azureRecommendationDAO.unIgnoreAzureVmRecommendations(accountId, toUnIgnore);
    }
    return azureVmIgnoreList;
  }
}
