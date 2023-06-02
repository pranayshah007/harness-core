/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.Instance;
import io.harness.models.ActiveServiceInstanceInfo;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.models.InstanceGroupedByPipelineExecution;
import io.harness.models.InstancesByBuildId;
import io.harness.ng.core.environment.beans.EnvironmentType;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@OwnedBy(HarnessTeam.DX)
public interface InstanceService {
  InstanceDTO save(InstanceDTO instanceDTO);

  List<InstanceDTO> saveAll(List<InstanceDTO> instanceDTOList);

  Optional<InstanceDTO> saveOrReturnEmptyIfAlreadyExists(InstanceDTO instanceDTO);

  void deleteById(String id);

  void softDeleteById(String id);

  void deleteAll(List<InstanceDTO> instanceDTOList);

  Optional<InstanceDTO> delete(@NotEmpty String instanceKey, @NotEmpty String accountIdentifier,
      @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier, String infrastructureMappingId);

  Optional<InstanceDTO> findAndReplace(InstanceDTO instanceDTO);

  List<InstanceDTO> getActiveInstancesByAccountOrgProjectAndService(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, long timestamp);

  List<InstanceDTO> getActiveInstances(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs);

  List<InstanceDTO> getActiveInstancesByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);

  List<InstanceDTO> getActiveInstancesByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  List<InstanceDTO> getActiveInstancesByInfrastructureMappingId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String infrastructureMappingId);

  List<InstanceDTO> getActiveInstancesByInstanceInfo(
      String accountIdentifier, String instanceInfoNamespace, String instanceInfoPodName);

  AggregationResults<EnvBuildInstanceCount> getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);

  AggregationResults<ActiveServiceInstanceInfo> getActiveServiceInstanceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  AggregationResults<ActiveServiceInstanceInfoV2> getActiveServiceInstanceInfo(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String envIdentifier, String serviceIdentifier,
      String buildIdentifier);

  AggregationResults<ActiveServiceInstanceInfoWithEnvType> getActiveServiceInstanceInfoWithEnvType(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier,
      String serviceIdentifier, String displayName, boolean isGitOps, boolean filterOnArtifact);

  AggregationResults<ActiveServiceInstanceInfo> getActiveServiceGitOpsInstanceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  AggregationResults<ActiveServiceInstanceInfoV2> getActiveServiceGitOpsInstanceInfo(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String envIdentifier, String serviceIdentifier,
      String buildIdentifier);

  AggregationResults<EnvironmentInstanceCountModel> getInstanceCountForEnvironmentFilteredByService(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      boolean isGitOps);

  AggregationResults<InstancesByBuildId> getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds,
      long timestampInMs, int limit, String infraId, String clusterId, String pipelineExecutionId);
  AggregationResults<ArtifactDeploymentDetailModel> getLastDeployedInstance(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, boolean isEnvironmentCard,
      boolean isGitOps);
  List<Instance> getActiveInstanceDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, String envId, String infraId, String clusterIdentifier, String pipelineExecutionId,
      String buildId, int limit);

  AggregationResults<InstanceGroupedByPipelineExecution> getActiveInstanceGroupedByPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, String envId,
      EnvironmentType environmentType, String infraId, String clusterIdentifier, String displayName);

  AggregationResults<CountByServiceIdAndEnvType> getActiveServiceInstanceCountBreakdown(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<String> serviceId, long timestampInMs);

  void updateInfrastructureMapping(List<String> instanceIds, String id);

  long countServiceInstancesDeployedInInterval(String accountId, long startTS, long endTS);

  long countServiceInstancesDeployedInInterval(
      String accountId, String orgId, String projectId, long startTS, long endTS);

  long countDistinctActiveServicesDeployedInInterval(
      String accountId, String orgId, String projectId, long startTS, long endTS);

  long countDistinctActiveServicesDeployedInInterval(String accountId, long startTS, long endTS);

  void deleteForAgent(@NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotEmpty String agentIdentifier);

  List<InstanceDTO> getActiveInstancesByServiceId(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String agentIdentifier);
}
