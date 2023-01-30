/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import io.harness.data.structure.EmptyPredicate;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.overview.dto.EnvironmentInstanceDetails;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  public void sortEnvironmentInstanceDetailList(
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
              if (o1.getEnvName() == null && o2.getEnvName() != null) {
                c = -1;
              } else if (o2.getEnvName() == null && o1.getEnvName() != null) {
                c = 1;
              } else {
                c = o1.getEnvName().compareTo(o2.getEnvName());
              }
            }
            return c;
          }
        });
  }
}
