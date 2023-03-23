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
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail;
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
  private static final String SERVICE_INFRA_INFO = "service_infra_info";
  private static final String PIPELINE_EXECUTION_SUMMARY_CD_ID = "pipeline_execution_summary_cd_id";
  private static final String ARTIFACT_DISPLAY_NAME = "artifact_display_name";
  private static final String ARTIFACT_IMAGE = "artifact_image";
  private static final String TAG = "tag";
  private static final String ACCOUNT_ID = "accountid";
  private static final String ORG_ID = "orgidentifier";
  private static final String PROJECT_ID = "projectidentifier";
  private static final String SERVICE_ID = "service_id";
  private static final String SERVICE_NAME = "service_name";
  private static final String SERVICE_STARTTS = "service_startts";
  private static final String ID = "id";
  private static final String PIPELINE_EXECUTION_SUMMARY_CD = "pipeline_execution_summary_cd";
  private static final String STATUS = "status";

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

  public String queryToFetchExecutionIdAndArtifactDetails(String accountId, String orgId, String projectId,
      String serviceRef, long startInterval, long endInterval, String artifactPath, String artifactVersion,
      String artifact) {
    String query = String.format(
        "select %s, %s, %s, %s, %s, %s, %s, %s, %s, %s from %s where %s and %s is not null and %s >= %s and %s <= %s",
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, SERVICE_NAME, ARTIFACT_DISPLAY_NAME, ARTIFACT_IMAGE, TAG,
        PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_STARTTS, SERVICE_INFRA_INFO,
        getScopeEqualityCriteria(accountId, orgId, projectId), SERVICE_ID, SERVICE_STARTTS, startInterval,
        SERVICE_STARTTS, endInterval);
    if (serviceRef != null) {
      query = query + String.format(" and %s = '%s'", SERVICE_ID, serviceRef);
    }
    if (artifact != null) {
      query = query + String.format(" and %s = '%s'", ARTIFACT_DISPLAY_NAME, artifact);
    }
    if (artifactPath != null) {
      query = query + String.format(" and %s = '%s'", ARTIFACT_IMAGE, artifactPath);
    }
    if (artifactVersion != null) {
      query = query + String.format(" and %s = '%s'", TAG, artifactVersion);
    }
    return query;
  }

  public String queryToFetchStatusOfExecution(String accountId, String orgId, String projectId, String status) {
    String query = String.format("select %s, %s from %s where %s and %s = any (?)", ID, STATUS,
        PIPELINE_EXECUTION_SUMMARY_CD, getScopeEqualityCriteria(accountId, orgId, projectId), ID);
    if (status != null) {
      query = query + String.format(" and %s = '%s'", STATUS, status);
    }
    return query;
  }

  public String getArtifactPathFromDisplayName(String displayName) {
    if (EmptyPredicate.isNotEmpty(displayName)) {
      String[] res = displayName.split(":");
      int count = res.length;
      if (count > 1) {
        return res[0];
      }
    }
    return null;
  }

  public String getTagFromDisplayName(String displayName) {
    if (EmptyPredicate.isNotEmpty(displayName)) {
      String[] res = displayName.split(":");
      int count = res.length;
      if (count > 1) {
        return res[1];
      } else if (count == 1) {
        return res[0];
      }
    }
    return displayName;
  }

  public String getDisplayNameFromArtifact(String artifactPath, String buildId) {
    if (EmptyPredicate.isEmpty(artifactPath)) {
      return buildId;
    }
    return String.format("%s:%s", artifactPath, buildId);
  }

  public String getScopeEqualityCriteria(String accountId, String orgId, String projectId) {
    if (projectId != null) {
      return String.format(
          "%s = '%s' and %s = '%s' and %s = '%s'", ACCOUNT_ID, accountId, ORG_ID, orgId, PROJECT_ID, projectId);
    } else if (orgId != null) {
      return String.format("%s = '%s' and %s = '%s'", ACCOUNT_ID, accountId, ORG_ID, orgId);
    } else {
      return String.format("%s = '%s'", ACCOUNT_ID, accountId);
    }
  }

  public PipelineExecutionCountInfo getPipelineExecutionCountInfoHelper(
      List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList, Map<String, String> statusMap) {
    sortServiceArtifactExecutionDetail(serviceArtifactExecutionDetailList);
    Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap = new HashMap<>();
    Map<String, Map<String, Set<String>>> artifactExecutionMap = new HashMap<>();
    Map<String, String> serviceRefToNameMap = new HashMap<>();
    Map<String, Set<String>> serviceExecutionMap = new HashMap<>();

    constructServiceToExecutionIdListMap(serviceArtifactExecutionDetailList, serviceArtifactExecutionDetailMap,
        artifactExecutionMap, serviceRefToNameMap, serviceExecutionMap);

    return getCountGroupedOnServiceList(
        artifactExecutionMap, statusMap, serviceArtifactExecutionDetailMap, serviceRefToNameMap, serviceExecutionMap);
  }

  private void sortServiceArtifactExecutionDetail(
      List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList) {
    Collections.sort(serviceArtifactExecutionDetailList, new Comparator<ServiceArtifactExecutionDetail>() {
      public int compare(ServiceArtifactExecutionDetail o1, ServiceArtifactExecutionDetail o2) {
        Long o1Time = o1.getServiceStartTime();
        Long o2Time = o2.getServiceStartTime();
        if (o2Time == null && o1Time == null) {
          return 0;
        } else if (o2Time == null) {
          return -1;
        } else if (o1Time == null) {
          return 1;
        } else {
          return (int) (o2Time - o1Time);
        }
      }
    });
  }

  private PipelineExecutionCountInfo getCountGroupedOnServiceList(
      Map<String, Map<String, Set<String>>> artifactExecutionMap, Map<String, String> statusMap,
      Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap,
      Map<String, String> serviceRefToNameMap, Map<String, Set<String>> serviceExecutionMap) {
    List<PipelineExecutionCountInfo.CountGroupedOnService> countGroupedOnServiceList = new ArrayList<>();
    for (Map.Entry<String, Map<String, Set<String>>> entry : artifactExecutionMap.entrySet()) {
      String serviceRef = entry.getKey();
      String serviceName = serviceRefToNameMap.get(serviceRef);
      List<PipelineExecutionCountInfo.CountGroupedOnArtifact> countGroupedOnArtifactList =
          getCountGroupedOnArtifactList(entry.getValue(), statusMap, serviceArtifactExecutionDetailMap, serviceRef);
      if (EmptyPredicate.isEmpty(countGroupedOnArtifactList)) {
        continue;
      }
      Pair<Long, List<PipelineExecutionCountInfo.CountGroupedOnStatus>> countInfo =
          getCountGroupedOnStatusList(serviceExecutionMap.get(serviceRef), statusMap);
      PipelineExecutionCountInfo.CountGroupedOnService countGroupedOnService =
          PipelineExecutionCountInfo.CountGroupedOnService.builder()
              .serviceReference(serviceRef)
              .serviceName(serviceName)
              .count(countInfo.getKey())
              .executionCountGroupedOnStatusList(countInfo.getValue())
              .executionCountGroupedOnArtifactList(countGroupedOnArtifactList)
              .build();
      countGroupedOnServiceList.add(countGroupedOnService);
    }
    return PipelineExecutionCountInfo.builder().executionCountGroupedOnServiceList(countGroupedOnServiceList).build();
  }

  private List<PipelineExecutionCountInfo.CountGroupedOnArtifact> getCountGroupedOnArtifactList(
      Map<String, Set<String>> artifactToExecutionIdMap, Map<String, String> statusMap,
      Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap, String serviceRef) {
    List<PipelineExecutionCountInfo.CountGroupedOnArtifact> countGroupedOnArtifactList = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry1 : artifactToExecutionIdMap.entrySet()) {
      String artifact = entry1.getKey();
      ServiceArtifactExecutionDetail serviceArtifactExecutionDetail =
          serviceArtifactExecutionDetailMap.get(serviceRef).get(artifact);
      Pair<Long, List<PipelineExecutionCountInfo.CountGroupedOnStatus>> countInfo =
          getCountGroupedOnStatusList(entry1.getValue(), statusMap);
      List<PipelineExecutionCountInfo.CountGroupedOnStatus> countGroupedOnStatusList = countInfo.getValue();
      if (EmptyPredicate.isEmpty(countGroupedOnStatusList)) {
        continue;
      }
      PipelineExecutionCountInfo.CountGroupedOnArtifact countGroupedOnArtifact =
          PipelineExecutionCountInfo.CountGroupedOnArtifact.builder()
              .artifactPath(serviceArtifactExecutionDetail.getArtifactPath())
              .artifactVersion(serviceArtifactExecutionDetail.getArtifactTag())
              .artifact(serviceArtifactExecutionDetail.getArtifactDisplayName())
              .count(countInfo.getKey())
              .executionCountGroupedOnStatusList(countGroupedOnStatusList)
              .build();
      countGroupedOnArtifactList.add(countGroupedOnArtifact);
    }
    return countGroupedOnArtifactList;
  }

  private Pair<Long, List<PipelineExecutionCountInfo.CountGroupedOnStatus>> getCountGroupedOnStatusList(
      Set<String> executionIdList, Map<String, String> statusMap) {
    Map<String, Long> statusCountMap = new HashMap<>();
    Long totalExecution = 0L;
    for (String executionId : executionIdList) {
      if (!statusMap.containsKey(executionId)) {
        continue;
      }
      totalExecution++;
      String status = statusMap.get(executionId);
      Long count = statusCountMap.get(status);
      if (count == null) {
        statusCountMap.put(status, 1L);
      } else {
        statusCountMap.put(status, count + 1);
      }
    }
    List<PipelineExecutionCountInfo.CountGroupedOnStatus> countGroupedOnStatusList = new ArrayList<>();
    for (Map.Entry<String, Long> entry : statusCountMap.entrySet()) {
      countGroupedOnStatusList.add(PipelineExecutionCountInfo.CountGroupedOnStatus.builder()
                                       .status(entry.getKey())
                                       .count(entry.getValue())
                                       .build());
    }
    return MutablePair.of(totalExecution, countGroupedOnStatusList);
  }

  private void constructServiceToExecutionIdListMap(
      List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList,
      Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap,
      Map<String, Map<String, Set<String>>> artifactExecutionMap, Map<String, String> serviceRefToNameMap,
      Map<String, Set<String>> serviceExecutionMap) {
    serviceArtifactExecutionDetailList.forEach(serviceArtifactExecutionDetail -> {
      String accountId = serviceArtifactExecutionDetail.getAccountId();
      String orgId = serviceArtifactExecutionDetail.getOrgId();
      String projectId = serviceArtifactExecutionDetail.getProjectId();
      String serviceRef = serviceArtifactExecutionDetail.getServiceRef();
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgId, projectId);
      serviceRef = identifierRef.getFullyQualifiedName();
      serviceRefToNameMap.putIfAbsent(serviceRef, serviceArtifactExecutionDetail.getServiceName());
      String artifactPath = serviceArtifactExecutionDetail.getArtifactPath();
      String artifactTag = serviceArtifactExecutionDetail.getArtifactTag();
      String artifactDisplayName = serviceArtifactExecutionDetail.getArtifactDisplayName();
      String pipelineExecutionId = serviceArtifactExecutionDetail.getPipelineExecutionSummaryCDId();
      if (artifactDisplayName == null) {
        artifactDisplayName = getDisplayNameFromArtifact(artifactPath, artifactTag);
      }
      artifactExecutionMap.putIfAbsent(serviceRef, new HashMap<>());
      artifactExecutionMap.get(serviceRef).putIfAbsent(artifactDisplayName, new HashSet<>());
      artifactExecutionMap.get(serviceRef).get(artifactDisplayName).add(pipelineExecutionId);
      serviceExecutionMap.putIfAbsent(serviceRef, new HashSet<>());
      serviceExecutionMap.get(serviceRef).add(pipelineExecutionId);
      serviceArtifactExecutionDetailMap.putIfAbsent(serviceRef, new HashMap<>());
      serviceArtifactExecutionDetailMap.get(serviceRef)
          .putIfAbsent(artifactDisplayName, serviceArtifactExecutionDetail);
    });
  }

  public Long checkForDefaultEndInterval(Long endInterval) {
    if (endInterval == null) {
      // taking current time as default endInterval
      return System.currentTimeMillis();
    }
    return endInterval;
  }

  public Long checkForDefaultStartInterval(Long startInterval) {
    if (startInterval == null) {
      // taking 30 days interval as default
      return System.currentTimeMillis() - 2592000000L;
    }
    return startInterval;
  }
}
