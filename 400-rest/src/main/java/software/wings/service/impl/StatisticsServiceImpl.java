/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.SPG_SERVICES_OVERVIEW_RBAC;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.time.EpochUtils.PST_ZONE_ID;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.time.EpochUtils;

import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.stats.DeploymentStatistics;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats.DayStat;
import software.wings.beans.stats.ServiceInstanceStatistics;
import software.wings.beans.stats.TopConsumer;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.StatisticsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WingsPersistence wingsPersistence;

  @Inject private UsageRestrictionsService usageRestrictionsService;

  private static final String[] workflowExecutionKeys = {WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.accountId,
      WorkflowExecutionKeys.appId, WorkflowExecutionKeys.appName, WorkflowExecutionKeys.createdAt,
      WorkflowExecutionKeys.createdBy, WorkflowExecutionKeys.endTs, WorkflowExecutionKeys.envId,
      WorkflowExecutionKeys.envIds, WorkflowExecutionKeys.envType, WorkflowExecutionKeys.pipelineExecution,
      WorkflowExecutionKeys.pipelineExecutionId, WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.releaseNo,
      WorkflowExecutionKeys.rollbackDuration, WorkflowExecutionKeys.rollbackStartTs,
      WorkflowExecutionKeys.serviceExecutionSummaries, WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.serviceIds,
      WorkflowExecutionKeys.status, WorkflowExecutionKeys.name, WorkflowExecutionKeys.workflowId,
      WorkflowExecutionKeys.orchestrationType, WorkflowExecutionKeys.workflowType, WorkflowExecutionKeys.startTs,
      WorkflowExecutionKeys.environments, WorkflowExecutionKeys.deploymentTriggerId, WorkflowExecutionKeys.triggeredBy};

  private static final String[] workflowExecutionKeys2 = {WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.accountId,
      WorkflowExecutionKeys.appId, WorkflowExecutionKeys.appName, WorkflowExecutionKeys.createdAt,
      WorkflowExecutionKeys.createdBy, WorkflowExecutionKeys.endTs, WorkflowExecutionKeys.envId,
      WorkflowExecutionKeys.envIds, WorkflowExecutionKeys.envType,
      WorkflowExecutionKeys.pipelineExecution_pipelineStageExecutions, WorkflowExecutionKeys.pipelineExecutionId,
      WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.releaseNo, WorkflowExecutionKeys.rollbackDuration,
      WorkflowExecutionKeys.rollbackStartTs, WorkflowExecutionKeys.serviceExecutionSummaries,
      WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.serviceIds, WorkflowExecutionKeys.status,
      WorkflowExecutionKeys.name, WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.orchestrationType,
      WorkflowExecutionKeys.workflowType, WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.environments,
      WorkflowExecutionKeys.deploymentTriggerId, WorkflowExecutionKeys.triggeredBy};
  @Override
  public DeploymentStatistics getDeploymentStatistics(String accountId, List<String> appIds, int numOfDays) {
    long fromDateEpochMilli = getEpochMilliPSTZone(numOfDays);
    DeploymentStatistics deploymentStats = new DeploymentStatistics();
    List<WorkflowExecution> workflowExecutions;
    String[] projectionKeys =
        featureFlagService.isEnabled(FeatureName.DISABLE_DEPLOYMENTS_SEARCH_AND_LIMIT_DEPLOYMENT_STATS, accountId)
        ? workflowExecutionKeys2
        : workflowExecutionKeys;
    if (isEmpty(appIds)) {
      workflowExecutions =
          workflowExecutionService.obtainWorkflowExecutions(accountId, fromDateEpochMilli, projectionKeys);
    } else {
      workflowExecutions =
          workflowExecutionService.obtainWorkflowExecutions(accountId, appIds, fromDateEpochMilli, projectionKeys);
    }

    if (isEmpty(workflowExecutions)) {
      return deploymentStats;
    }

    Map<EnvironmentType, List<WorkflowExecution>> wflExecutionByEnvType =
        workflowExecutions.stream().collect(groupingBy(wex -> PROD == wex.getEnvType() ? PROD : NON_PROD));

    deploymentStats.getStatsMap().put(
        PROD, getDeploymentStatisticsByEnvType(numOfDays, wflExecutionByEnvType.get(EnvironmentType.PROD)));
    deploymentStats.getStatsMap().put(
        NON_PROD, getDeploymentStatisticsByEnvType(numOfDays, wflExecutionByEnvType.get(EnvironmentType.NON_PROD)));

    notNullCheck("Non Production Deployment stats", deploymentStats.getStatsMap().get(NON_PROD));
    deploymentStats.getStatsMap().put(
        ALL, merge(deploymentStats.getStatsMap().get(PROD), deploymentStats.getStatsMap().get(NON_PROD)));

    return deploymentStats;
  }

  @Override
  public DeploymentStatistics getDeploymentStatisticsNew(String accountId, List<String> appIds, int numOfDays) {
    long fromDateEpochMilli = getEpochMilliPSTZone(numOfDays);
    Query<WorkflowExecution> query =
        wingsPersistence.createAuthorizedQueryOnAnalyticNode(WorkflowExecution.class)
            .field(WorkflowExecutionKeys.createdAt)
            .greaterThanOrEq(fromDateEpochMilli)
            .filter(WorkflowExecutionKeys.accountId, accountId)
            .field(WorkflowExecutionKeys.pipelineExecutionId)
            .doesNotExist()
            .project("pipelineExecution.pipelineStageExecutions.workflowExecutions.serviceExecutionSummaries", true)
            .project("serviceExecutionSummaries", true)
            .project("accountId", true)
            .project("appId", true)
            .project("createdAt", true)
            .project("envType", true)
            .project("workflowType", true)
            .project(WorkflowExecutionKeys.status, true);

    if (isNotEmpty(appIds)) {
      query.field(WorkflowExecutionKeys.appId).in(appIds);
    }
    List<WorkflowExecution> workflowExecutions = query.asList();

    DeploymentStatistics deploymentStats = new DeploymentStatistics();
    if (isEmpty(workflowExecutions)) {
      return deploymentStats;
    }

    Map<EnvironmentType, List<WorkflowExecution>> wflExecutionByEnvType =
        workflowExecutions.stream().collect(groupingBy(wex -> PROD == wex.getEnvType() ? PROD : NON_PROD));

    deploymentStats.getStatsMap().put(
        PROD, getDeploymentStatisticsByEnvType(numOfDays, wflExecutionByEnvType.get(EnvironmentType.PROD)));
    deploymentStats.getStatsMap().put(
        NON_PROD, getDeploymentStatisticsByEnvType(numOfDays, wflExecutionByEnvType.get(EnvironmentType.NON_PROD)));

    notNullCheck("Non Production Deployment stats", deploymentStats.getStatsMap().get(NON_PROD));
    deploymentStats.getStatsMap().put(
        ALL, merge(deploymentStats.getStatsMap().get(PROD), deploymentStats.getStatsMap().get(NON_PROD)));

    return deploymentStats;
  }

  @Override
  public ServiceInstanceStatistics getServiceInstanceStatisticsNew(
      String accountId, List<String> appIds, int numOfDays) {
    long fromDateEpochMilli = getEpochMilliPSTZone(numOfDays);
    Set<String> allowedSvcIds = new HashSet<>();
    ServiceInstanceStatistics instanceStats = new ServiceInstanceStatistics();
    Query<WorkflowExecution> query =
        wingsPersistence.createAuthorizedQueryOnAnalyticNode(WorkflowExecution.class)
            .field(WorkflowExecutionKeys.createdAt)
            .greaterThanOrEq(fromDateEpochMilli)
            .filter(WorkflowExecutionKeys.accountId, accountId)
            .field(WorkflowExecutionKeys.pipelineExecutionId)
            .doesNotExist()
            .field(WorkflowExecutionKeys.status)
            .in(ExecutionStatus.finalStatuses())
            .project("pipelineExecution.pipelineStageExecutions.workflowExecutions.serviceExecutionSummaries", true)
            .project(WorkflowExecutionKeys.serviceExecutionSummaries, true)
            .project(WorkflowExecutionKeys.accountId, true)
            .project(WorkflowExecutionKeys.appId, true)
            .project(WorkflowExecutionKeys.appName, true)
            .project(WorkflowExecutionKeys.createdAt, true)
            .project(WorkflowExecutionKeys.envType, true)
            .project(WorkflowExecutionKeys.workflowType, true)
            .project(WorkflowExecutionKeys.status, true);

    if (isNotEmpty(appIds)) {
      query.field(WorkflowExecutionKeys.appId).in(appIds);
    }

    if (featureFlagService.isEnabled(SPG_SERVICES_OVERVIEW_RBAC, accountId) && hasUserContext()) {
      UserRequestContext userRequestContext = UserThreadLocal.get().getUserRequestContext();
      Map<String, Set<String>> appSvcMap = usageRestrictionsService.getAppSvcMapFromUserPermissions(
          accountId, userRequestContext.getUserPermissionInfo(), PermissionAttribute.Action.READ);
      for (String appId : appSvcMap.keySet()) {
        allowedSvcIds.addAll(appSvcMap.get(appId));
      }
      query.field(WorkflowExecutionKeys.serviceIds).hasAnyOf(allowedSvcIds);
    }

    List<WorkflowExecution> workflowExecutions = query.asList();

    if (isEmpty(workflowExecutions)) {
      return instanceStats;
    }

    Comparator<TopConsumer> byCount = comparing(TopConsumer::getTotalCount, reverseOrder());

    List<TopConsumer> allTopConsumers = new ArrayList<>();
    getTopServicesDeployed(allTopConsumers, workflowExecutions);

    allTopConsumers = allTopConsumers.stream().sorted(byCount).collect(toList());

    Map<EnvironmentType, List<WorkflowExecution>> wflExecutionByEnvType =
        workflowExecutions.stream().collect(groupingBy(wex -> PROD == wex.getEnvType() ? PROD : NON_PROD));

    List<TopConsumer> prodTopConsumers = new ArrayList<>();
    getTopServicesDeployed(prodTopConsumers, wflExecutionByEnvType.get(PROD));
    prodTopConsumers = prodTopConsumers.stream().sorted(byCount).collect(toList());

    List<TopConsumer> nonProdTopConsumers = new ArrayList<>();
    getTopServicesDeployed(nonProdTopConsumers, wflExecutionByEnvType.get(NON_PROD));

    nonProdTopConsumers = nonProdTopConsumers.stream().sorted(byCount).collect(toList());

    instanceStats.getStatsMap().put(ALL, allTopConsumers);
    instanceStats.getStatsMap().put(PROD, prodTopConsumers);
    instanceStats.getStatsMap().put(NON_PROD, nonProdTopConsumers);
    return instanceStats;
  }

  private boolean hasUserContext() {
    User user = UserThreadLocal.get();
    return user != null && user.getUserRequestContext() != null;
  }

  @Override
  public ServiceInstanceStatistics getServiceInstanceStatistics(String accountId, List<String> appIds, int numOfDays) {
    long fromDateEpochMilli = getEpochMilliPSTZone(numOfDays);

    ServiceInstanceStatistics instanceStats = new ServiceInstanceStatistics();
    List<WorkflowExecution> workflowExecutions;
    String[] projectionKeys =
        featureFlagService.isEnabled(FeatureName.DISABLE_DEPLOYMENTS_SEARCH_AND_LIMIT_DEPLOYMENT_STATS, accountId)
        ? workflowExecutionKeys2
        : workflowExecutionKeys;
    if (isEmpty(appIds)) {
      workflowExecutions =
          workflowExecutionService.obtainWorkflowExecutions(accountId, fromDateEpochMilli, projectionKeys);
    } else {
      workflowExecutions =
          workflowExecutionService.obtainWorkflowExecutions(accountId, appIds, fromDateEpochMilli, projectionKeys);
    }
    if (isEmpty(workflowExecutions)) {
      return instanceStats;
    }
    Comparator<TopConsumer> byCount = comparing(TopConsumer::getTotalCount, reverseOrder());

    List<TopConsumer> allTopConsumers = new ArrayList<>();
    getTopServicesDeployed(allTopConsumers, workflowExecutions);

    allTopConsumers = allTopConsumers.stream().sorted(byCount).collect(toList());

    Map<EnvironmentType, List<WorkflowExecution>> wflExecutionByEnvType =
        workflowExecutions.stream().collect(groupingBy(wex -> PROD == wex.getEnvType() ? PROD : NON_PROD));

    List<TopConsumer> prodTopConsumers = new ArrayList<>();
    getTopServicesDeployed(prodTopConsumers, wflExecutionByEnvType.get(PROD));
    prodTopConsumers = prodTopConsumers.stream().sorted(byCount).collect(toList());

    List<TopConsumer> nonProdTopConsumers = new ArrayList<>();
    getTopServicesDeployed(nonProdTopConsumers, wflExecutionByEnvType.get(NON_PROD));

    nonProdTopConsumers = nonProdTopConsumers.stream().sorted(byCount).collect(toList());

    instanceStats.getStatsMap().put(ALL, allTopConsumers);
    instanceStats.getStatsMap().put(PROD, prodTopConsumers);
    instanceStats.getStatsMap().put(NON_PROD, nonProdTopConsumers);
    return instanceStats;
  }

  private AggregatedDayStats merge(AggregatedDayStats prodAggStats, AggregatedDayStats nonProdAggStats) {
    if (prodAggStats == null && nonProdAggStats == null) {
      return new AggregatedDayStats();
    }

    if (prodAggStats == null) {
      return nonProdAggStats;
    }

    if (nonProdAggStats == null) {
      return prodAggStats;
    }

    List<DayStat> dayStats = new ArrayList<>(prodAggStats.getDaysStats().size());

    IntStream.range(0, prodAggStats.getDaysStats().size()).forEach(idx -> {
      DayStat prod = prodAggStats.getDaysStats().get(idx);
      DayStat nonProd = nonProdAggStats.getDaysStats().get(idx);
      dayStats.add(
          new DayStat(prod.getTotalCount() + nonProd.getTotalCount(), prod.getFailedCount() + nonProd.getFailedCount(),
              prod.getInstancesCount() + nonProd.getInstancesCount(), prod.getDate()));
    });
    return new AggregatedDayStats(prodAggStats.getTotalCount() + nonProdAggStats.getTotalCount(),
        prodAggStats.getFailedCount() + nonProdAggStats.getFailedCount(),
        prodAggStats.getInstancesCount() + nonProdAggStats.getInstancesCount(), dayStats);
  }

  private AggregatedDayStats getDeploymentStatisticsByEnvType(
      int numOfDays, List<WorkflowExecution> workflowExecutions) {
    List<DayStat> dayStats = new ArrayList<>(numOfDays);

    Map<Long, List<WorkflowExecution>> wflExecutionByDate = new HashMap<>();
    if (workflowExecutions != null) {
      wflExecutionByDate = workflowExecutions.stream().collect(
          groupingBy(wfl -> EpochUtils.obtainStartOfTheDayEpoch(wfl.getCreatedAt(), PST_ZONE_ID)));
    }

    int aggTotalCount = 0;
    int aggFailureCount = 0;
    int aggInstanceCount = 0;

    for (int idx = 0; idx < numOfDays; idx++) {
      int totalCount = 0;
      int failureCount = 0;
      int instanceCount = 0;

      Long timeOffset = getEpochMilliPSTZone(numOfDays - idx);
      List<WorkflowExecution> wflExecutions = wflExecutionByDate.get(timeOffset);
      if (wflExecutions != null) {
        totalCount = wflExecutions.size();
        failureCount = (int) wflExecutions.stream()
                           .map(WorkflowExecution::getStatus)
                           .filter(ExecutionStatus.negativeStatuses()::contains)
                           .count();
        for (WorkflowExecution workflowExecution : wflExecutions) {
          instanceCount += workflowExecutionService.getInstancesDeployedFromExecution(workflowExecution);
        }
      }

      dayStats.add(new DayStat(totalCount, failureCount, instanceCount, timeOffset));
      aggTotalCount += totalCount;
      aggFailureCount += failureCount;
      aggInstanceCount += instanceCount;
    }

    return new AggregatedDayStats(aggTotalCount, aggFailureCount, aggInstanceCount, dayStats);
  }

  private void getTopServicesDeployed(List<TopConsumer> topConsumers, List<WorkflowExecution> wflExecutions) {
    Map<String, TopConsumer> topConsumerMap = new HashMap<>();
    if (isEmpty(wflExecutions)) {
      return;
    }
    for (WorkflowExecution execution : wflExecutions) {
      if (!ExecutionStatus.isFinalStatus(execution.getStatus())) {
        continue;
      }
      final List<ElementExecutionSummary> serviceExecutionSummaries = new ArrayList<>();
      if (execution.getWorkflowType() == PIPELINE && execution.getPipelineExecution() != null
          && isNotEmpty(execution.getPipelineExecution().getPipelineStageExecutions())) {
        execution.getPipelineExecution()
            .getPipelineStageExecutions()
            .stream()
            .filter(pipelineStageExecution -> pipelineStageExecution.getWorkflowExecutions() != null)
            .flatMap(pipelineStageExecution -> pipelineStageExecution.getWorkflowExecutions().stream())
            .filter(workflowExecution -> workflowExecution.getServiceExecutionSummaries() != null)
            .forEach(workflowExecution -> {
              serviceExecutionSummaries.addAll(workflowExecution.getServiceExecutionSummaries());
            });
      } else if (execution.getServiceExecutionSummaries() != null) {
        serviceExecutionSummaries.addAll(execution.getServiceExecutionSummaries());
      }
      Map<String, ElementExecutionSummary> serviceExecutionStatusMap = new HashMap<>();
      for (ElementExecutionSummary serviceExecutionSummary : serviceExecutionSummaries) {
        if (serviceExecutionSummary.getContextElement() == null) {
          continue;
        }
        String serviceId = serviceExecutionSummary.getContextElement().getUuid();
        serviceExecutionStatusMap.put(serviceId, serviceExecutionSummary);
      }
      for (ElementExecutionSummary serviceExecutionSummary : serviceExecutionStatusMap.values()) {
        String serviceId = serviceExecutionSummary.getContextElement().getUuid();
        ExecutionStatus serviceExecutionStatus = serviceExecutionSummary.getStatus();
        if (serviceExecutionStatus == null) {
          serviceExecutionStatus = execution.getStatus();
        }
        TopConsumer topConsumer;
        if (!topConsumerMap.containsKey(serviceId)) {
          TopConsumer tempConsumer = TopConsumer.builder()
                                         .appId(execution.getAppId())
                                         .appName(execution.getAppName())
                                         .serviceId(serviceId)
                                         .serviceName(serviceExecutionSummary.getContextElement().getName())
                                         .build();
          topConsumerMap.put(serviceId, tempConsumer);
          topConsumers.add(tempConsumer);
        }
        topConsumer = topConsumerMap.get(serviceId);
        if (serviceExecutionStatus == SUCCESS) {
          topConsumer.setSuccessfulActivityCount(topConsumer.getSuccessfulActivityCount() + 1);
          topConsumer.setTotalCount(topConsumer.getTotalCount() + 1);
        } else {
          topConsumer.setFailedActivityCount(topConsumer.getFailedActivityCount() + 1);
          topConsumer.setTotalCount(topConsumer.getTotalCount() + 1);
        }
      }
    }
  }

  private long getEpochMilliPSTZone(int days) {
    return EpochUtils.calculateEpochMilliOfStartOfDayForXDaysInPastFromNow(days, PST_ZONE_ID);
  }
}
