/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.instance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.DX)
public interface InstanceRepositoryCustom {
  Instance findAndReplace(Criteria criteria, Instance instance);

  Instance findAndModify(Criteria criteria, Update update);

  List<Instance> getActiveInstancesByAccountOrgProjectAndService(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, long timestamp);

  List<Instance> getActiveInstances(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs);

  List<Instance> getActiveInstancesByInstanceInfo(
      String accountIdentifier, String instanceInfoNamespace, String instanceInfoPodName);

  List<Instance> getActiveInstancesByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);

  List<Instance> getActiveInstancesByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  List<Instance> getActiveInstancesByInfrastructureMappingId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String infrastructureMappingId);

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

  Instance findFirstInstance(Criteria criteria);

  void updateInfrastructureMapping(String instanceId, String infrastructureMappingId);

  long countServiceInstancesDeployedInInterval(String accountId, long startTS, long endTS);

  long countServiceInstancesDeployedInInterval(
      String accountId, String orgId, String projectId, long startTS, long endTS);

  long countDistinctActiveServicesDeployedInInterval(String accountId, long startTS, long endTS);

  long countDistinctActiveServicesDeployedInInterval(
      String accountId, String orgId, String projectId, long startTS, long endTS);

  UpdateResult updateMany(Criteria criteria, Update update);

  List<Instance> getActiveInstancesByServiceId(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, String agentIdentifier);

  List<Instance> getInstancesCreatedBefore(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, long timestamp);
}
