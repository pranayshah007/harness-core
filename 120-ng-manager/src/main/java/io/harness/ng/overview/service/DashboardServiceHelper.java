/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.overview.dto.ArtifactDeploymentDetail;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentInstanceDetails;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.utils.IdentifierRefHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
public class DashboardServiceHelper {
  public InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentListHelper(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps) {
    // nested map - environmentId, environmentType, infrastructureId, displayName, (count, lastDeployedAt)
    Map<String, Map<EnvironmentType, Map<String, Map<String, Pair<Integer, Long>>>>> instanceCountMap = new HashMap<>();
    Map<String, String> envIdToNameMap = new HashMap<>();
    // since we are already filtering instances on service type (gitOps or non-gitOps), infraIdToNameMap will contain
    // clusterId to agentId map in case of gitOps
    Map<String, String> infraIdToNameMap = new HashMap<>();

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final EnvironmentType envType = activeServiceInstanceInfo.getEnvType();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();

      if (envId == null || lastDeployedAt == null) {
        return;
      }

      final String envName = activeServiceInstanceInfo.getEnvName();
      envIdToNameMap.putIfAbsent(envId, envName);
      instanceCountMap.putIfAbsent(envId, new HashMap<>());

      instanceCountMap.putIfAbsent(envId, new HashMap<>());
      instanceCountMap.get(envId).putIfAbsent(envType, new HashMap<>());

      String infraId = activeServiceInstanceInfo.getClusterIdentifier() != null
          ? activeServiceInstanceInfo.getClusterIdentifier()
          : activeServiceInstanceInfo.getInfraIdentifier();
      String infraName = activeServiceInstanceInfo.getAgentIdentifier() != null
          ? activeServiceInstanceInfo.getAgentIdentifier()
          : activeServiceInstanceInfo.getInfraName();

      infraIdToNameMap.putIfAbsent(infraId, infraName);
      instanceCountMap.get(envId).get(envType).putIfAbsent(infraId, new HashMap<>());
      instanceCountMap.get(envId).get(envType).get(infraId).putIfAbsent(activeServiceInstanceInfo.getDisplayName(),
          MutablePair.of(activeServiceInstanceInfo.getCount(), activeServiceInstanceInfo.getLastDeployedAt()));
    });
    return InstanceGroupedByEnvironmentList.builder()
        .instanceGroupedByEnvironmentList(
            groupByEnvironment(instanceCountMap, infraIdToNameMap, envIdToNameMap, isGitOps))
        .build();
  }

  public InstanceGroupedOnArtifactList getInstanceGroupedByArtifactListHelper(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps) {
    // nested map - displayName, envId, environmentType, instanceGroupedByInfrastructure
    Map<String, Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
        instanceCountMap = new HashMap<>();
    Map<String, String> envIdToNameMap = new HashMap<>();

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();

      if (envId == null || lastDeployedAt == null) {
        return;
      }

      final String envName = activeServiceInstanceInfo.getEnvName();
      envIdToNameMap.putIfAbsent(envId, envName);

      final String displayName = activeServiceInstanceInfo.getDisplayName();
      instanceCountMap.putIfAbsent(displayName, new HashMap<>());
      instanceCountMap.get(displayName).putIfAbsent(envId, new HashMap<>());

      final EnvironmentType environmentType = activeServiceInstanceInfo.getEnvType();
      instanceCountMap.get(displayName).get(envId).putIfAbsent(environmentType, new ArrayList<>());
      instanceCountMap.get(displayName)
          .get(envId)
          .get(environmentType)
          .add(getInstanceGroupedByInfrastructure(activeServiceInstanceInfo, isGitOps));
    });
    return InstanceGroupedOnArtifactList.builder()
        .instanceGroupedOnArtifactList(groupByArtifact(instanceCountMap, envIdToNameMap))
        .build();
  }

  private InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure getInstanceGroupedByInfrastructure(
      ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType, boolean isGitOps) {
    InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure
        .InstanceGroupedOnInfrastructureBuilder instanceGroupedByInfrastructureBuilder =
        InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure.builder();
    if (isGitOps) {
      instanceGroupedByInfrastructureBuilder.clusterId(activeServiceInstanceInfoWithEnvType.getClusterIdentifier())
          .agentId(activeServiceInstanceInfoWithEnvType.getAgentIdentifier());
    } else {
      instanceGroupedByInfrastructureBuilder.infrastructureId(activeServiceInstanceInfoWithEnvType.getInfraIdentifier())
          .infrastructureName(activeServiceInstanceInfoWithEnvType.getInfraName());
    }

    return instanceGroupedByInfrastructureBuilder.count(activeServiceInstanceInfoWithEnvType.getCount())
        .lastDeployedAt(activeServiceInstanceInfoWithEnvType.getLastDeployedAt())
        .build();
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact> groupByArtifact(
      Map<String,
          Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact> instanceGroupedByArtifactList = new ArrayList<>();
    for (Map.Entry<String,
             Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
             entry : instanceCountMap.entrySet()) {
      final String displayName = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
          groupByEnvironment(entry.getValue(), envIdToNameMap);
      long lastDeployedAt = 0l;
      if (EmptyPredicate.isNotEmpty(instanceGroupedByEnvironmentList)) {
        lastDeployedAt = instanceGroupedByEnvironmentList.get(0).getLastDeployedAt();
      }
      instanceGroupedByArtifactList.add(InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact.builder()
                                            .artifact(displayName)
                                            .lastDeployedAt(lastDeployedAt)
                                            .instanceGroupedOnEnvironmentList(instanceGroupedByEnvironmentList)
                                            .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByArtifactList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> groupByEnvironment(
      Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
        new ArrayList<>();
    for (Map.Entry<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>
             entry : instanceCountMap.entrySet()) {
      final String envId = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> instanceGroupedByEnvironmentTypeList =
          groupByEnvironmentType(entry.getValue());
      long lastDeployedAt = 0l;
      if (EmptyPredicate.isNotEmpty(instanceGroupedByEnvironmentTypeList)) {
        lastDeployedAt = instanceGroupedByEnvironmentTypeList.get(0).getLastDeployedAt();
      }
      instanceGroupedByEnvironmentList.add(
          InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment.builder()
              .envId(envId)
              .envName(envIdToNameMap.get(envId))
              .lastDeployedAt(lastDeployedAt)
              .instanceGroupedOnEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
              .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedByEnvironmentList, new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByEnvironmentList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> groupByEnvironmentType(
      Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>> instanceCountMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> instanceGroupedByEnvironmentTypeList =
        new ArrayList<>();
    for (Map.Entry<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>> entry :
        instanceCountMap.entrySet()) {
      EnvironmentType environmentType = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure> instanceGroupedByInfrastructureList =
          entry.getValue();
      // sort based on last deployed time
      Collections.sort(instanceGroupedByInfrastructureList,
          new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>() {
            public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure o1,
                InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure o2) {
              return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
            }
          });
      long lastDeployedAt = 0l;
      if (EmptyPredicate.isNotEmpty(instanceGroupedByInfrastructureList)) {
        lastDeployedAt = instanceGroupedByInfrastructureList.get(0).getLastDeployedAt();
      }
      instanceGroupedByEnvironmentTypeList.add(
          InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType.builder()
              .environmentType(environmentType)
              .lastDeployedAt(lastDeployedAt)
              .instanceGroupedOnInfrastructureList(instanceGroupedByInfrastructureList)
              .build());
    }
    // sort based on last deployed time
    Collections.sort(instanceGroupedByEnvironmentTypeList,
        new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByEnvironmentTypeList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment> groupByEnvironment(
      Map<String, Map<EnvironmentType, Map<String, Map<String, Pair<Integer, Long>>>>> instanceCountMap,
      Map<String, String> infraIdToNameMap, Map<String, String> envIdToNameMap, boolean isGitOps) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList =
        new ArrayList<>();

    for (Map.Entry<String, Map<EnvironmentType, Map<String, Map<String, Pair<Integer, Long>>>>> entry :
        instanceCountMap.entrySet()) {
      final String envId = entry.getKey();

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> instanceGroupedByEnvironmentTypeList =
          groupedByEnvironmentTypes(entry.getValue(), infraIdToNameMap, isGitOps);

      long lastDeployedAt = 0l;
      if (EmptyPredicate.isNotEmpty(instanceGroupedByEnvironmentTypeList)) {
        lastDeployedAt = instanceGroupedByEnvironmentTypeList.get(0).getLastDeployedAt();
      }

      instanceGroupedByEnvironmentList.add(
          InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment.builder()
              .envId(envId)
              .envName(envIdToNameMap.get(envId))
              .instanceGroupedByEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
              .lastDeployedAt(lastDeployedAt)
              .build());
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByEnvironmentList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByEnvironmentList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> groupedByEnvironmentTypes(
      Map<EnvironmentType, Map<String, Map<String, Pair<Integer, Long>>>> instanceCountMap,
      Map<String, String> infraIdToNameMap, boolean isGitOps) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> instanceGroupedByEnvironmentTypeList =
        new ArrayList<>();

    for (Map.Entry<EnvironmentType, Map<String, Map<String, Pair<Integer, Long>>>> entry :
        instanceCountMap.entrySet()) {
      EnvironmentType environmentType = entry.getKey();

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList =
          groupedByInfrastructures(entry.getValue(), infraIdToNameMap, isGitOps);

      long lastDeployedAt = 0l;
      if (EmptyPredicate.isNotEmpty(instanceGroupedByInfrastructureList)) {
        lastDeployedAt = instanceGroupedByInfrastructureList.get(0).getLastDeployedAt();
      }

      instanceGroupedByEnvironmentTypeList.add(
          InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType.builder()
              .environmentType(environmentType)
              .instanceGroupedByInfrastructureList(instanceGroupedByInfrastructureList)
              .lastDeployedAt(lastDeployedAt)
              .build());
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByEnvironmentTypeList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByEnvironmentTypeList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure> groupedByInfrastructures(
      Map<String, Map<String, Pair<Integer, Long>>> instanceCountMap, Map<String, String> infraIdToNameMap,
      boolean isGitOps) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList =
        new ArrayList<>();

    for (Map.Entry<String, Map<String, Pair<Integer, Long>>> entry : instanceCountMap.entrySet()) {
      String infraId = entry.getKey();

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact> instanceGroupedByArtifactList =
          groupedByArtifacts(entry.getValue());

      long lastDeployedAt = 0l;
      if (EmptyPredicate.isNotEmpty(instanceGroupedByArtifactList)) {
        lastDeployedAt = instanceGroupedByArtifactList.get(0).getLastDeployedAt();
      }

      InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure
          .InstanceGroupedByInfrastructureBuilder infrastructureBuilder =
          InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure.builder();
      if (isGitOps) {
        infrastructureBuilder.clusterId(infraId).agentId(infraIdToNameMap.get(infraId));
      } else {
        infrastructureBuilder.infrastructureId(infraId).infrastructureName(infraIdToNameMap.get(infraId));
      }
      infrastructureBuilder.instanceGroupedByArtifactList(instanceGroupedByArtifactList).lastDeployedAt(lastDeployedAt);
      instanceGroupedByInfrastructureList.add(infrastructureBuilder.build());
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByInfrastructureList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByInfrastructureList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact> groupedByArtifacts(
      Map<String, Pair<Integer, Long>> instanceCountMap) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact> instanceGroupedByArtifactList = new ArrayList<>();

    for (Map.Entry<String, Pair<Integer, Long>> entry : instanceCountMap.entrySet()) {
      instanceGroupedByArtifactList.add(InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact.builder()
                                            .artifact(entry.getKey())
                                            .count(entry.getValue().getKey())
                                            .lastDeployedAt(entry.getValue().getValue())
                                            .build());
    }

    // sort based on last deployed time

    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByArtifactList;
  }

  public ArtifactInstanceDetails getArtifactInstanceDetailsFromMap(
      Map<String, Map<String, ArtifactDeploymentDetail>> artifactDeploymentDetailsMap,
      Map<String, String> envIdToEnvNameMap, Map<String, EnvironmentType> envIdToEnvTypeMap) {
    List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetails = new ArrayList<>();
    for (Map.Entry<String, Map<String, ArtifactDeploymentDetail>> entry : artifactDeploymentDetailsMap.entrySet()) {
      final String displayName = entry.getKey();
      List<EnvironmentInstanceDetails.EnvironmentInstanceDetail> environmentInstanceDetailList = new ArrayList<>();
      for (Map.Entry<String, ArtifactDeploymentDetail> entry1 : entry.getValue().entrySet()) {
        final String envId = entry1.getKey();
        final ArtifactDeploymentDetail artifactDeploymentDetail = entry1.getValue();
        if (envId == null || artifactDeploymentDetail == null) {
          continue;
        }
        final String envName = envIdToEnvNameMap.get(envId);
        final EnvironmentType environmentType = envIdToEnvTypeMap.get(envId);
        EnvironmentInstanceDetails.EnvironmentInstanceDetail environmentInstanceDetail =
            EnvironmentInstanceDetails.EnvironmentInstanceDetail.builder()
                .envId(envId)
                .envName(envName)
                .environmentType(environmentType)
                .artifactDeploymentDetail(artifactDeploymentDetail)
                .build();
        environmentInstanceDetailList.add(environmentInstanceDetail);
      }

      if (EmptyPredicate.isEmpty(environmentInstanceDetailList)) {
        continue;
      }

      sortEnvironmentInstanceDetailList(environmentInstanceDetailList);
      artifactInstanceDetails.add(
          ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
              .artifact(displayName)
              .environmentInstanceDetails(EnvironmentInstanceDetails.builder()
                                              .environmentInstanceDetails(environmentInstanceDetailList)
                                              .build())
              .build());
    }
    sortArtifactInstanceDetailList(artifactInstanceDetails);
    return ArtifactInstanceDetails.builder().artifactInstanceDetails(artifactInstanceDetails).build();
  }

  private void sortArtifactInstanceDetailList(
      List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetailList) {
    Collections.sort(artifactInstanceDetailList, new Comparator<ArtifactInstanceDetails.ArtifactInstanceDetail>() {
      public int compare(
          ArtifactInstanceDetails.ArtifactInstanceDetail o1, ArtifactInstanceDetails.ArtifactInstanceDetail o2) {
        int c;
        if (o1.getArtifact() == null && o2.getArtifact() == null) {
          c = 0;
        } else if (o1.getArtifact() == null) {
          c = -1;
        } else if (o2.getArtifact() == null) {
          c = 1;
        } else {
          c = o1.getArtifact().compareTo(o2.getArtifact());
        }
        return c;
      }
    });
  }

  private void sortEnvironmentInstanceDetailList(
      List<EnvironmentInstanceDetails.EnvironmentInstanceDetail> environmentInstanceDetailList) {
    Collections.sort(
        environmentInstanceDetailList, new Comparator<EnvironmentInstanceDetails.EnvironmentInstanceDetail>() {
          public int compare(EnvironmentInstanceDetails.EnvironmentInstanceDetail o1,
              EnvironmentInstanceDetails.EnvironmentInstanceDetail o2) {
            int c;
            if (o1.getEnvironmentType() == null && o2.getEnvironmentType() == null) {
              c = 0;
            } else if (o1.getEnvironmentType() == null) {
              c = -1;
            } else if (o2.getEnvironmentType() == null) {
              c = 1;
            } else {
              c = o1.getEnvironmentType().compareTo(o2.getEnvironmentType());
            }
            if (c == 0) {
              if (o1.getEnvName() != null && o2.getEnvName() != null) {
                c = o1.getEnvName().compareTo(o2.getEnvName());
              } else if (o2.getEnvName() != null) {
                c = -1;
              } else if (o1.getEnvName() != null) {
                c = 1;
              }
            }
            return c;
          }
        });
  }

  public void sortActiveServiceInstanceInfoWithEnvTypeList(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList) {
    // sort based on last deployed time
    Collections.sort(activeServiceInstanceInfoList, new Comparator<ActiveServiceInstanceInfoWithEnvType>() {
      public int compare(ActiveServiceInstanceInfoWithEnvType o1, ActiveServiceInstanceInfoWithEnvType o2) {
        return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
      }
    });
  }

  public void sortServicePipelineInfoList(List<ServicePipelineInfo> servicePipelineInfoList) {
    // sort based on last deployed time
    Collections.sort(servicePipelineInfoList, new Comparator<ServicePipelineInfo>() {
      public int compare(ServicePipelineInfo o1, ServicePipelineInfo o2) {
        return (int) (o2.getLastExecutedAt() - o1.getLastExecutedAt());
      }
    });
  }

  public void constructEnvironmentNameAndTypeMap(List<Environment> environments, Map<String, String> envIdToNameMap,
      Map<String, EnvironmentType> envIdToEnvTypeMap) {
    for (Environment environment : environments) {
      String envId = environment.getIdentifier();
      if (envId == null) {
        continue;
      }
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
          envId, environment.getAccountId(), environment.getOrgIdentifier(), environment.getProjectIdentifier());
      envId = identifierRef.buildScopedIdentifier();
      final String envName = environment.getName();
      final EnvironmentType environmentType = environment.getType();
      envIdToNameMap.put(envId, envName);
      envIdToEnvTypeMap.put(envId, environmentType);
    }
  }

  public Map<String, Map<String, ArtifactDeploymentDetail>> constructArtifactToLastDeploymentMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails, List<String> envIds) {
    Map<String, Map<String, ArtifactDeploymentDetail>> map = new HashMap<>();
    Set<String> envIdSet = new HashSet<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      final String displayName = artifactDeploymentDetail.getDisplayName();
      map.putIfAbsent(displayName, new HashMap<>());
      map.get(displayName)
          .putIfAbsent(envId,
              ArtifactDeploymentDetail.builder()
                  .artifact(displayName)
                  .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                  .build());
      envIdSet.add(envId);
    }
    envIds.addAll(envIdSet);
    return map;
  }

  public EnvironmentInstanceDetails getEnvironmentInstanceDetailsFromMap(
      Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailsMap, Map<String, Integer> envToCountMap,
      Map<String, String> envIdToEnvNameMap, Map<String, EnvironmentType> envIdToEnvTypeMap) {
    List<EnvironmentInstanceDetails.EnvironmentInstanceDetail> environmentInstanceDetails = new ArrayList<>();

    for (Map.Entry<String, Integer> entry : envToCountMap.entrySet()) {
      final String envId = entry.getKey();
      final EnvironmentType envType = envIdToEnvTypeMap.get(envId);
      final String envName = envIdToEnvNameMap.get(envId);
      final Integer count = entry.getValue();
      final ArtifactDeploymentDetail artifactDeploymentDetail = artifactDeploymentDetailsMap.get(envId);
      if (artifactDeploymentDetail == null) {
        continue;
      }
      environmentInstanceDetails.add(EnvironmentInstanceDetails.EnvironmentInstanceDetail.builder()
                                         .environmentType(envType)
                                         .envId(envId)
                                         .envName(envName)
                                         .artifactDeploymentDetail(artifactDeploymentDetail)
                                         .count(count)
                                         .build());
    }

    DashboardServiceHelper.sortEnvironmentInstanceDetailList(environmentInstanceDetails);

    return EnvironmentInstanceDetails.builder().environmentInstanceDetails(environmentInstanceDetails).build();
  }

  public void constructEnvironmentCountMap(List<EnvironmentInstanceCountModel> environmentInstanceCounts,
      Map<String, Integer> envToCountMap, List<String> envIds) {
    for (EnvironmentInstanceCountModel environmentInstanceCountModel : environmentInstanceCounts) {
      final String envId = environmentInstanceCountModel.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      envToCountMap.put(envId, environmentInstanceCountModel.getCount());
      envIds.add(envId);
    }
  }

  public Map<String, ArtifactDeploymentDetail> constructEnvironmentToArtifactDeploymentMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails) {
    Map<String, ArtifactDeploymentDetail> map = new HashMap<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      map.putIfAbsent(envId,
          ArtifactDeploymentDetail.builder()
              .artifact(artifactDeploymentDetail.getDisplayName())
              .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
              .build());
    }
    return map;
  }

  public String buildOpenTaskQuery(
      String accountId, String orgId, String projectId, String serviceId, long startInterval) {
    return String.format(
        "select pipeline_execution_summary_cd_id from service_infra_info where accountid = '%s' and orgidentifier = '%s' and projectidentifier = '%s' and service_id = '%s' and service_startts > %s",
        accountId, orgId, projectId, serviceId, startInterval);
  }
}
