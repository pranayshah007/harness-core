/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.jooq.impl.DSL.row;

import io.harness.aggregates.AggregateProjectInfo;
import io.harness.aggregates.AggregateServiceInfo;
import io.harness.aggregates.TimeWiseExecutionSummary;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDDashboardServiceHelper;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.dashboards.DashboardHelper;
import io.harness.dashboards.DeploymentStatsSummary;
import io.harness.dashboards.EnvCount;
import io.harness.dashboards.PipelineExecutionDashboardInfo;
import io.harness.dashboards.PipelinesExecutionDashboardInfo;
import io.harness.dashboards.ProjectDashBoardInfo;
import io.harness.dashboards.ProjectsDashboardInfo;
import io.harness.dashboards.ServiceDashboardInfo;
import io.harness.dashboards.ServicesCount;
import io.harness.dashboards.ServicesDashboardInfo;
import io.harness.dashboards.SortBy;
import io.harness.dashboards.TimeBasedDeploymentInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.pms.dashboards.GroupBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;
import io.harness.timescaledb.tables.pojos.Services;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Row2;
import org.jooq.Table;
import org.jooq.impl.DSL;

@OwnedBy(PIPELINE)
public class CDLandingDashboardServiceImpl implements CDLandingDashboardService {
  public static final long DAY_IN_MS = 86400000; // 24*60*60*1000

  @Inject private TimeScaleDAL timeScaleDAL;

  @Override
  public ServicesDashboardInfo getActiveServices(@NotNull String accountIdentifier,
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval,
      @NotNull SortBy sortBy) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesDashboardInfo.builder().build();
    }

    if (sortBy == SortBy.INSTANCES) {
      return getActiveServicesByInstances(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
    }
    return getActiveServicesByDeployments(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
  }

  ServicesDashboardInfo getActiveServicesByDeployments(@NotNull String accountIdentifier,
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<AggregateServiceInfo> serviceInfraInfoList = timeScaleDAL.getTopServicesByDeploymentCount(accountIdentifier,
        startInterval, endInterval, orgProjectTable, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(serviceInfraInfoList)) {
      return ServicesDashboardInfo.builder().build();
    }

    List<ServiceDashboardInfo> servicesDashboardInfoList = new ArrayList<>();
    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfo serviceInfraInfo : serviceInfraInfoList) {
      ServiceDashboardInfo serviceDashboardInfo = ServiceDashboardInfo.builder()
                                                      .identifier(serviceInfraInfo.getServiceId())
                                                      .accountIdentifier(accountIdentifier)
                                                      .orgIdentifier(serviceInfraInfo.getOrgidentifier())
                                                      .projectIdentifier(serviceInfraInfo.getProjectidentifier())
                                                      .totalDeploymentsCount(serviceInfraInfo.getCount())
                                                      .build();
      servicesDashboardInfoList.add(serviceDashboardInfo);
      combinedIdToRecordMap.put(getCombinedId(serviceDashboardInfo.getOrgIdentifier(),
                                    serviceDashboardInfo.getProjectIdentifier(), serviceDashboardInfo.getIdentifier()),
          serviceDashboardInfo);
    }

    Table<Record3<String, String, String>> orgProjectServiceTable =
        NgServiceInfraInfoUtils.getOrgProjectServiceTable(serviceInfraInfoList);
    prepareServicesChangeRate(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval, servicesDashboardInfoList);
    prepareStatusWiseCount(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval, servicesDashboardInfoList);
    addServiceNames(combinedIdToRecordMap, accountIdentifier, orgProjectServiceTable);

    return ServicesDashboardInfo.builder().serviceDashboardInfoList(servicesDashboardInfoList).build();
  }

  ServicesDashboardInfo getActiveServicesByInstances(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesDashboardInfo.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<AggregateServiceInfo> serviceInfraInfoList =
        timeScaleDAL.getTopServicesByInstanceCount(accountIdentifier, startInterval, endInterval, orgProjectTable);

    if (EmptyPredicate.isEmpty(serviceInfraInfoList)) {
      return ServicesDashboardInfo.builder().build();
    }

    List<ServiceDashboardInfo> servicesDashboardInfoList = new ArrayList<>();
    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfo serviceInfraInfo : serviceInfraInfoList) {
      ServiceDashboardInfo serviceDashboardInfo = ServiceDashboardInfo.builder()
                                                      .identifier(serviceInfraInfo.getServiceId())
                                                      .accountIdentifier(accountIdentifier)
                                                      .orgIdentifier(serviceInfraInfo.getOrgidentifier())
                                                      .projectIdentifier(serviceInfraInfo.getProjectidentifier())
                                                      .instancesCount(serviceInfraInfo.getCount())
                                                      .build();
      servicesDashboardInfoList.add(serviceDashboardInfo);
      combinedIdToRecordMap.put(getCombinedId(serviceDashboardInfo.getOrgIdentifier(),
                                    serviceDashboardInfo.getProjectIdentifier(), serviceDashboardInfo.getIdentifier()),
          serviceDashboardInfo);
    }

    Table<Record3<String, String, String>> orgProjectServiceTable =
        NgServiceInfraInfoUtils.getOrgProjectServiceTable(serviceInfraInfoList);
    prepareServiceInstancesChangeRate(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);
    addServiceNames(combinedIdToRecordMap, accountIdentifier, orgProjectServiceTable);

    return ServicesDashboardInfo.builder().serviceDashboardInfoList(servicesDashboardInfoList).build();
  }

  void prepareServiceInstancesChangeRate(Table<Record3<String, String, String>> orgProjectServiceTable,
      String accountIdentifier, long startInterval, long endInterval,
      Map<String, ServiceDashboardInfo> combinedIdToRecordMap) {
    if (EmptyPredicate.isEmpty(combinedIdToRecordMap)) {
      return;
    }
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateServiceInfo> serviceInstanceList = timeScaleDAL.getInstanceCountForGivenServices(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval);

    if (EmptyPredicate.isEmpty(serviceInstanceList)) {
      return;
    }

    for (AggregateServiceInfo aggregateServiceInfo : serviceInstanceList) {
      String combinedId = getCombinedId(aggregateServiceInfo.getOrgidentifier(),
          aggregateServiceInfo.getProjectidentifier(), aggregateServiceInfo.getServiceId());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(combinedId);
      double changeRate = getChangeRate(aggregateServiceInfo.getCount(), serviceDashboardInfo.getInstancesCount());
      serviceDashboardInfo.setInstancesCountChangeRate(changeRate);
    }
  }

  void addServiceNames(Map<String, ServiceDashboardInfo> combinedIdToRecordMap, String accountIdentifier,
      Table<Record3<String, String, String>> orgProjectServiceTable) {
    List<Services> servicesList = timeScaleDAL.getNamesForServiceIds(accountIdentifier, orgProjectServiceTable);

    if (EmptyPredicate.isEmpty(servicesList)) {
      return;
    }

    for (Services service : servicesList) {
      String key = getCombinedId(service.getOrgIdentifier(), service.getProjectIdentifier(), service.getIdentifier());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);
      serviceDashboardInfo.setName(service.getName());
    }
  }

  void prepareStatusWiseCount(Table<Record3<String, String, String>> orgProjectServiceTable, String accountIdentifier,
      long startInterval, long endInterval, List<ServiceDashboardInfo> serviceDashboardInfoList) {
    if (EmptyPredicate.isEmpty(serviceDashboardInfoList)) {
      return;
    }

    List<AggregateServiceInfo> previousServiceInfraInfoList =
        timeScaleDAL.getStatusWiseDeploymentCountForGivenServices(orgProjectServiceTable, accountIdentifier,
            startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousServiceInfraInfoList)) {
      return;
    }

    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (ServiceDashboardInfo serviceDashboardInfo : serviceDashboardInfoList) {
      String key = getCombinedId(serviceDashboardInfo.getOrgIdentifier(), serviceDashboardInfo.getProjectIdentifier(),
          serviceDashboardInfo.getIdentifier());
      combinedIdToRecordMap.put(key, serviceDashboardInfo);
    }

    for (AggregateServiceInfo aggregateServiceInfo : previousServiceInfraInfoList) {
      String key = getCombinedId(aggregateServiceInfo.getOrgidentifier(), aggregateServiceInfo.getProjectidentifier(),
          aggregateServiceInfo.getServiceId());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);

      String status = aggregateServiceInfo.getServiceStatus();
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        serviceDashboardInfo.setSuccessDeploymentsCount(aggregateServiceInfo.getCount());
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        serviceDashboardInfo.setFailureDeploymentsCount(
            aggregateServiceInfo.getCount() + serviceDashboardInfo.getFailureDeploymentsCount());
      }
    }
  }

  void prepareServicesChangeRate(Table<Record3<String, String, String>> orgProjectServiceTable,
      String accountIdentifier, long startInterval, long endInterval,
      List<ServiceDashboardInfo> serviceDashboardInfoList) {
    if (EmptyPredicate.isEmpty(serviceDashboardInfoList)) {
      return;
    }
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateServiceInfo> previousServiceInfraInfoList =
        timeScaleDAL.getDeploymentCountForGivenServices(orgProjectServiceTable, accountIdentifier, startInterval,
            endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousServiceInfraInfoList)) {
      return;
    }

    Map<String, AggregateServiceInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfo aggregateServiceInfo : previousServiceInfraInfoList) {
      String key = getCombinedId(aggregateServiceInfo.getOrgidentifier(), aggregateServiceInfo.getProjectidentifier(),
          aggregateServiceInfo.getServiceId());
      combinedIdToRecordMap.put(key, aggregateServiceInfo);
    }

    for (ServiceDashboardInfo serviceDashboardInfo : serviceDashboardInfoList) {
      String key = getCombinedId(serviceDashboardInfo.getOrgIdentifier(), serviceDashboardInfo.getProjectIdentifier(),
          serviceDashboardInfo.getIdentifier());
      if (combinedIdToRecordMap.containsKey(key)) {
        AggregateServiceInfo previousServiceInfo = combinedIdToRecordMap.get(key);
        serviceDashboardInfo.setTotalDeploymentsChangeRate(
            getChangeRate(previousServiceInfo.getCount(), serviceDashboardInfo.getTotalDeploymentsCount()));
      }
    }
  }

  @org.jetbrains.annotations.NotNull
  private String getCombinedId(String... keys) {
    StringBuilder combinedId = new StringBuilder();

    for (String key : keys) {
      combinedId.append(key).append('-');
    }
    combinedId.deleteCharAt(combinedId.length() - 1);

    return combinedId.toString();
  }

  @org.jetbrains.annotations.NotNull
  private Table<Record2<String, String>> getOrgProjectTable(@NotNull List<OrgProjectIdentifier> orgProjectIdentifiers) {
    Row2<String, String>[] orgProjectRows = new Row2[orgProjectIdentifiers.size()];
    int index = 0;
    for (OrgProjectIdentifier orgProjectIdentifier : orgProjectIdentifiers) {
      orgProjectRows[index++] =
          row(orgProjectIdentifier.getOrgIdentifier(), orgProjectIdentifier.getProjectIdentifier());
    }

    return DSL.values(orgProjectRows).as("t", "orgId", "projectId");
  }

  @Override
  public ProjectsDashboardInfo getTopProjects(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ProjectsDashboardInfo.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<AggregateProjectInfo> projectInfoList = timeScaleDAL.getTopProjectsByDeploymentCount(accountIdentifier,
        startInterval, endInterval, orgProjectTable, CDDashboardServiceHelper.getSuccessFailedStatusList());

    List<ProjectDashBoardInfo> projectDashBoardInfoList = new ArrayList<>();

    if (EmptyPredicate.isEmpty(projectInfoList)) {
      return ProjectsDashboardInfo.builder().build();
    }

    for (AggregateProjectInfo projectInfo : projectInfoList) {
      projectDashBoardInfoList.add(ProjectDashBoardInfo.builder()
                                       .accountId(accountIdentifier)
                                       .orgIdentifier(projectInfo.getOrgidentifier())
                                       .projectIdentifier(projectInfo.getProjectidentifier())
                                       .deploymentsCount(projectInfo.getCount())
                                       .build());
    }

    Table<Record2<String, String>> topOrgProjectTable = prepareOrgProjectTable(projectInfoList);
    Map<String, ProjectDashBoardInfo> combinedIdToRecordMap = getCombinedIdToRecordMap(projectDashBoardInfoList);

    prepareProjectsChangeRate(topOrgProjectTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);

    prepareProjectsStatusWiseCount(
        topOrgProjectTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);

    return ProjectsDashboardInfo.builder().projectDashBoardInfoList(projectDashBoardInfoList).build();
  }

  @Override
  public ServicesCount getServicesCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesCount.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    Integer totalServicesCount = timeScaleDAL.getTotalServicesCount(accountIdentifier, orgProjectTable);
    int trendCount = timeScaleDAL.getNewServicesCount(accountIdentifier, startInterval, endInterval, orgProjectTable)
        - timeScaleDAL.getDeletedServiceCount(accountIdentifier, startInterval, endInterval, orgProjectTable);

    return ServicesCount.builder().totalCount(totalServicesCount).newCount(trendCount).build();
  }

  @Override
  public EnvCount getEnvCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return EnvCount.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    Integer totalCount = timeScaleDAL.getTotalEnvCount(accountIdentifier, orgProjectTable);
    int trendCount = timeScaleDAL.getNewEnvCount(accountIdentifier, startInterval, endInterval, orgProjectTable)
        - timeScaleDAL.getDeletedEnvCount(accountIdentifier, startInterval, endInterval, orgProjectTable);

    return EnvCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  @Override
  public PipelinesExecutionDashboardInfo getActiveDeploymentStats(
      String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<String> requiredStatuses =
        new ArrayList<>(Arrays.asList(ExecutionStatus.APPROVAL_WAITING.name(), ExecutionStatus.APPROVALWAITING.name(),
            ExecutionStatus.INTERVENTION_WAITING.name(), ExecutionStatus.INTERVENTIONWAITING.name()));
    requiredStatuses.addAll(io.harness.ng.overview.service.CDOverviewDashboardServiceImpl.activeStatusList);

    List<PipelineExecutionSummaryCd> executionsList =
        timeScaleDAL.getPipelineExecutionsForGivenExecutionStatus(accountIdentifier, orgProjectTable, requiredStatuses);

    PipelinesExecutionDashboardInfo pipelinesExecutionDashboardInfo =
        filterByStatuses(accountIdentifier, executionsList);

    pipelinesExecutionDashboardInfo.setFailed24HrsExecutions(
        getLast24HrsFailedExecutions(accountIdentifier, orgProjectTable));
    return pipelinesExecutionDashboardInfo;
  }

  @Override
  public DeploymentStatsSummary getDeploymentStatsSummary(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval, GroupBy groupBy) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return DeploymentStatsSummary.builder().build();
    }
    DeploymentStatsSummary currentDeploymentStatsSummary = getDeploymentStatsSummaryWithoutChangeRate(
        accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);

    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    DeploymentStatsSummary previousDeploymentStatsSummary = getDeploymentStatsSummaryWithoutChangeRate(
        accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);

    double totalCountChangeRate =
        getChangeRate(previousDeploymentStatsSummary.getTotalCount(), currentDeploymentStatsSummary.getTotalCount());
    double failureRateChangeRate =
        getChangeRate(previousDeploymentStatsSummary.getFailureRate(), currentDeploymentStatsSummary.getFailureRate());
    double deploymentRateChangeRate = getChangeRate(
        previousDeploymentStatsSummary.getDeploymentRate(), currentDeploymentStatsSummary.getDeploymentRate());

    currentDeploymentStatsSummary.setTotalCountChangeRate(totalCountChangeRate);
    currentDeploymentStatsSummary.setFailureRateChangeRate(failureRateChangeRate);
    currentDeploymentStatsSummary.setDeploymentRateChangeRate(deploymentRateChangeRate);

    return currentDeploymentStatsSummary;
  }

  private DeploymentStatsSummary getDeploymentStatsSummaryWithoutChangeRate(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval, GroupBy groupBy) {
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<TimeWiseExecutionSummary> timeWiseDeploymentStatsList =
        timeScaleDAL.getTimeExecutionStatusWiseDeploymentCount(accountIdentifier, startInterval, endInterval, groupBy,
            orgProjectTable, CDDashboardServiceHelper.getSuccessFailedStatusList());

    List<TimeBasedDeploymentInfo> timeWiseDeploymentInfoList = new ArrayList<>();
    long totalDeploymentsCount = 0;
    long failedDeploymentsCount = 0;

    long prevEpoch = 0;
    TimeBasedDeploymentInfo prevTimeDeploymentInfo = null;
    for (TimeWiseExecutionSummary deploymentStats : timeWiseDeploymentStatsList) {
      long count = deploymentStats.getCount();
      String status = deploymentStats.getStatus();

      if (deploymentStats.getEpoch() != prevEpoch || prevTimeDeploymentInfo == null) {
        TimeBasedDeploymentInfo timeBasedDeploymentInfo =
            TimeBasedDeploymentInfo.builder().epochTime(deploymentStats.getEpoch()).build();

        prevEpoch = deploymentStats.getEpoch();
        prevTimeDeploymentInfo = timeBasedDeploymentInfo;
        timeWiseDeploymentInfoList.add(timeBasedDeploymentInfo);
      }
      prevTimeDeploymentInfo.setTotalCount(prevTimeDeploymentInfo.getTotalCount() + count);
      totalDeploymentsCount += count;
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        prevTimeDeploymentInfo.setSuccessCount(prevTimeDeploymentInfo.getSuccessCount() + count);
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        prevTimeDeploymentInfo.setFailedCount(prevTimeDeploymentInfo.getFailedCount() + count);
        failedDeploymentsCount += count;
      }
    }

    timeWiseDeploymentInfoList.forEach(timeBasedDeploymentInfo
        -> timeBasedDeploymentInfo.setFailureRate(
            getRate(timeBasedDeploymentInfo.getFailedCount(), timeBasedDeploymentInfo.getTotalCount())));

    double failureRate = getRate(failedDeploymentsCount, totalDeploymentsCount);
    double noOfBuckets = Math.ceil(((endInterval - startInterval) * 1.0) / groupBy.getNoOfMilliseconds());
    double deploymentRate = DashboardHelper.truncate(totalDeploymentsCount / noOfBuckets);

    return DeploymentStatsSummary.builder()
        .totalCount(totalDeploymentsCount)
        .failureRate(failureRate)
        .deploymentRate(deploymentRate)
        .timeBasedDeploymentInfoList(timeWiseDeploymentInfoList)
        .build();
  }

  private double getRate(double count, double totalCount) {
    if (count == 0) {
      return 0;
    }
    return totalCount == 0 ? DashboardHelper.MAX_VALUE : DashboardHelper.truncate(count * 100 / totalCount);
  }

  PipelinesExecutionDashboardInfo filterByStatuses(
      String accountIdentifier, List<PipelineExecutionSummaryCd> executionsList) {
    if (EmptyPredicate.isEmpty(executionsList)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    List<PipelineExecutionDashboardInfo> runningExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingApprovalExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions = new ArrayList<>();

    for (PipelineExecutionSummaryCd execution : executionsList) {
      String status = execution.getStatus();

      PipelineExecutionDashboardInfo pipelineExecutionDashboardInfo =
          PipelineExecutionDashboardInfo.builder()
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(execution.getOrgidentifier())
              .projectIdentifier(execution.getProjectidentifier())
              .identifier(execution.getPipelineidentifier())
              .name(execution.getName())
              .planExecutionId(execution.getPlanexecutionid())
              .startTs(execution.getStartts())
              .build();

      if (ExecutionStatus.APPROVAL_WAITING.name().equals(status)
          || ExecutionStatus.APPROVALWAITING.name().equals(status)) {
        pendingApprovalExecutions.add(pipelineExecutionDashboardInfo);
      } else if (ExecutionStatus.INTERVENTION_WAITING.name().equals(status)
          || ExecutionStatus.INTERVENTIONWAITING.name().equals(status)) {
        pendingManualInterventionExecutions.add(pipelineExecutionDashboardInfo);
      } else if (CDOverviewDashboardServiceImpl.activeStatusList.contains(status)) {
        runningExecutions.add(pipelineExecutionDashboardInfo);
      }
    }

    return PipelinesExecutionDashboardInfo.builder()
        .pendingApprovalExecutions(pendingApprovalExecutions)
        .pendingManualInterventionExecutions(pendingManualInterventionExecutions)
        .runningExecutions(runningExecutions)
        .build();
  }

  List<PipelineExecutionDashboardInfo> getLast24HrsFailedExecutions(
      String accountIdentifier, Table<Record2<String, String>> orgProjectTable) {
    long endTime = System.currentTimeMillis();
    long startTime = System.currentTimeMillis() - DAY_IN_MS;

    List<PipelineExecutionSummaryCd> executionsList =
        timeScaleDAL.getFailedExecutionsForGivenTimeRange(accountIdentifier, orgProjectTable, endTime, startTime);

    return executionsList.stream()
        .map(execution
            -> PipelineExecutionDashboardInfo.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(execution.getOrgidentifier())
                   .projectIdentifier(execution.getProjectidentifier())
                   .identifier(execution.getPipelineidentifier())
                   .name(execution.getName())
                   .planExecutionId(execution.getPlanexecutionid())
                   .startTs(execution.getStartts())
                   .build())
        .collect(Collectors.toList());
  }

  private Map<String, ProjectDashBoardInfo> getCombinedIdToRecordMap(List<ProjectDashBoardInfo> projectInfoList) {
    Map<String, ProjectDashBoardInfo> combinedIdToRecordMap = new HashMap<>();

    for (ProjectDashBoardInfo projectDashBoardInfo : projectInfoList) {
      String key = getCombinedId(projectDashBoardInfo.getOrgIdentifier(), projectDashBoardInfo.getProjectIdentifier());
      combinedIdToRecordMap.put(key, projectDashBoardInfo);
    }

    return combinedIdToRecordMap;
  }

  void prepareProjectsStatusWiseCount(Table<Record2<String, String>> orgProjectTable, String accountIdentifier,
      long startInterval, long endInterval, Map<String, ProjectDashBoardInfo> combinedIdToRecordMap) {
    List<AggregateProjectInfo> projectInfoList = timeScaleDAL.getProjectWiseStatusWiseDeploymentCount(orgProjectTable,
        accountIdentifier, startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(projectInfoList)) {
      return;
    }

    for (AggregateProjectInfo aggregateProjectInfo : projectInfoList) {
      String key = getCombinedId(aggregateProjectInfo.getOrgidentifier(), aggregateProjectInfo.getProjectidentifier());
      ProjectDashBoardInfo projectDashBoardInfo = combinedIdToRecordMap.get(key);

      String status = aggregateProjectInfo.getStatus();
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        projectDashBoardInfo.setSuccessDeploymentsCount(aggregateProjectInfo.getCount());
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        projectDashBoardInfo.setFailedDeploymentsCount(
            aggregateProjectInfo.getCount() + projectDashBoardInfo.getFailedDeploymentsCount());
      }
    }
  }

  void prepareProjectsChangeRate(Table<Record2<String, String>> orgProjectTable, String accountIdentifier,
      long startInterval, long endInterval, Map<String, ProjectDashBoardInfo> combinedIdToRecordMap) {
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateProjectInfo> previousProjectInfoList = timeScaleDAL.getProjectWiseDeploymentCount(orgProjectTable,
        accountIdentifier, startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousProjectInfoList)) {
      return;
    }

    for (AggregateProjectInfo previousProjectInfo : previousProjectInfoList) {
      String key = getCombinedId(previousProjectInfo.getOrgidentifier(), previousProjectInfo.getProjectidentifier());
      ProjectDashBoardInfo projectDashBoardInfo = combinedIdToRecordMap.get(key);
      projectDashBoardInfo.setDeploymentsCountChangeRate(
          getChangeRate(previousProjectInfo.getCount(), projectDashBoardInfo.getDeploymentsCount()));
    }
  }

  private double getChangeRate(double previousValue, double newValue) {
    double change = newValue - previousValue;
    if (change == 0) {
      return 0;
    }
    double rate = previousValue != 0 ? (change * 100.0) / previousValue : Double.MAX_VALUE;
    return DashboardHelper.truncate(rate);
  }

  private Table<Record2<String, String>> prepareOrgProjectTable(List<AggregateProjectInfo> projectInfoList) {
    List<OrgProjectIdentifier> orgProjectIdentifiers =
        projectInfoList.stream()
            .map(aggregateProjectInfo
                -> OrgProjectIdentifier.builder()
                       .orgIdentifier(aggregateProjectInfo.getOrgidentifier())
                       .projectIdentifier(aggregateProjectInfo.getProjectidentifier())
                       .build())
            .collect(Collectors.toList());

    return getOrgProjectTable(orgProjectIdentifiers);
  }
}
