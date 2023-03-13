/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.dashboard.DashboardExecutionStatusInfo;
import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummary;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummaryV2;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.DashboardWorkloadDeployment;
import io.harness.ng.overview.dto.DashboardWorkloadDeploymentV2;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfoList;
import io.harness.ng.overview.dto.EnvIdCountPair;
import io.harness.ng.overview.dto.EnvironmentInstanceDetails;
import io.harness.ng.overview.dto.ExecutionDeploymentInfo;
import io.harness.ng.overview.dto.HealthDeploymentDashboard;
import io.harness.ng.overview.dto.HealthDeploymentDashboardV2;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.InstancesByBuildIdList;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTO;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTOV2;
import io.harness.ng.overview.dto.ServiceDeploymentListInfo;
import io.harness.ng.overview.dto.ServiceDeploymentListInfoV2;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTO;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTOV2;
import io.harness.ng.overview.dto.ServiceHeaderInfo;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.dto.TimeValuePairListDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public interface CDOverviewDashboardService {
  HealthDeploymentDashboard getHealthDeploymentDashboard(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval);

  HealthDeploymentDashboardV2 getHealthDeploymentDashboardV2(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval);

  ExecutionDeploymentInfo getExecutionDeploymentDashboard(
      String accountId, String orgId, String projectId, long startInterval, long endInterval);

  DashboardExecutionStatusInfo getDeploymentActiveFailedRunningInfo(
      String accountId, String orgId, String projectId, long days, long startInterval, long endInterval);

  DashboardWorkloadDeployment getDashboardWorkloadDeployment(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType);

  DashboardWorkloadDeploymentV2 getDashboardWorkloadDeploymentV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType);

  ServiceDeploymentListInfo getServiceDeploymentsInfo(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception;

  ServiceDeploymentListInfoV2 getServiceDeploymentsInfoV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception;

  ServiceDeploymentInfoDTO getServiceDeployments(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays);

  ServiceDeploymentInfoDTOV2 getServiceDeploymentsV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays);

  ServiceDetailsInfoDTO getServiceDetailsList(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long startTime, long endTime, List<String> sort) throws Exception;

  ServiceDetailsInfoDTOV2 getServiceDetailsListV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort) throws Exception;

  io.harness.ng.overview.dto.TimeValuePairListDTO<Integer> getServicesGrowthTrend(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTimeInMs, long endTimeInMs,
      TimeGroupType timeGroupType);

  InstanceCountDetailsByEnvTypeAndServiceId getActiveServiceInstanceCountBreakdown(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceId);

  ActiveServiceInstanceSummary getActiveServiceInstanceSummary(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);
  ActiveServiceInstanceSummaryV2 getActiveServiceInstanceSummaryV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);

  EnvBuildIdAndInstanceCountInfoList getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, String environmentId);
  InstanceGroupedOnArtifactList getInstanceGroupedOnArtifactList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, String environmentId, String displayName, boolean filterOnArtifact);

  InstanceGroupedByServiceList.InstanceGroupedByService getInstanceGroupedByArtifactList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  InstanceGroupedByServiceList getInstanceGroupedByServiceList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier, String buildIdentifier);

  io.harness.ng.overview.dto.EnvironmentDeploymentInfo getEnvironmentDeploymentDetailsByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  InstanceGroupedByServiceList.InstanceGroupedByService getActiveServiceDeploymentsList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  EnvironmentInstanceDetails getEnvironmentInstanceDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier);

  ArtifactInstanceDetails getArtifactInstanceDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier);

  OpenTaskDetails getOpenTasks(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, long startInterval);

  InstancesByBuildIdList getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, String envId, List<String> buildIds, String infraId, String clusterId,
      String pipelineExecutionId);

  InstanceDetailsByBuildId getActiveInstanceDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String envIdentifier, String infraIdentifier,
      String clusterIdentifier, String pipelineExecutionId, String buildId);

  InstanceDetailGroupedByPipelineExecutionList getInstanceDetailGroupedByPipelineExecution(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, String envIdentifier,
      EnvironmentType environmentType, String infraIdentifier, String clusterIdentifier, String displayName);

  io.harness.ng.overview.dto.TimeValuePairListDTO<Integer> getInstanceGrowthTrend(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs);

  TimeValuePairListDTO<EnvIdCountPair> getInstanceCountHistory(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs);

  DeploymentsInfo getDeploymentsByServiceId(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, long startTimeInMs, long endTimeInMs);

  ServiceHeaderInfo getServiceHeaderInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);

  Map<String, String> getLastPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIds);

  Map<String, String> getLastPipeline(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Set<String> serviceIds, Set<String> envIds);

  Map<String, ServicePipelineInfo> getPipelineExecutionDetails(List<String> pipelineExecutionIdList);
}
