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
import static io.harness.beans.ExecutionStatus.finalStatuses;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.time.EpochUtils.PST_ZONE_ID;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.mongodb.morphia.aggregation.Accumulator.accumulator;
import static org.mongodb.morphia.aggregation.Group.grouping;
import static org.mongodb.morphia.aggregation.Projection.expression;
import static org.mongodb.morphia.aggregation.Projection.projection;

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
import software.wings.beans.stats.TopConsumer.TopConsumerBuilder;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.StatisticsService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.AggregationOptions;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.aggregation.AggregationPipeline;
import org.mongodb.morphia.aggregation.Group;
import org.mongodb.morphia.query.Query;

@Singleton
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WingsPersistence wingsPersistence;

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
          workflowExecutionService.obtainWorkflowExecutions(appIds, fromDateEpochMilli, projectionKeys);
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

  public void addRbacAndAppIdFilterToBaseQuery(Query<WorkflowExecution> query, List<String> appIds) {
    User user = UserThreadLocal.get();
    // If its not a user operation, return
    if (user == null) {
      return;
    }

    UserRequestContext userRequestContext = user.getUserRequestContext();
    // No user request context set by the filter.
    if (userRequestContext == null) {
      return;
    }
    if (userRequestContext.isAppIdFilterRequired()) {
      if (isNotEmpty(userRequestContext.getAppIds())) {
        UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
        if (userPermissionInfo.isHasAllAppAccess()) {
          return;
          // not required since we are adding in base query the accountid
          //          query.field("accountId").equal(userRequestContext.getAccountId());
        } else {
          if (isNotEmpty(appIds)) {
            List<String> appIdsWithPerms = appIdMerged(appIds, userRequestContext.getAppIds());
            query.field("appId").in(appIdsWithPerms);
          } else {
            query.field("appId").in(userRequestContext.getAppIds());
          }
        }
      } else {
        log.error("Some unexpected error occurred");
      }
    }
  }

  private List<String> appIdMerged(List<String> appIds, Set<String> appIdsWithPerms) {
    Set<String> appIdSet = new HashSet<>(appIds);
    Set<String> appIdWithPermsSet = new HashSet<>(appIdsWithPerms);
    return appIdSet.stream()
        .map(appId -> {
          if (appIdWithPermsSet.contains(appId)) {
            return appId;
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public DeploymentStatistics getDeploymentStatisticsNew(String accountId, List<String> appIds, int numOfDays) {
    long fromDateEpochMilli = getEpochMilliPSTZone(numOfDays);
    Query<WorkflowExecution> baseQuery = wingsPersistence.createQuery(WorkflowExecution.class)
                                             .field(WorkflowExecutionKeys.createdAt)
                                             .greaterThanOrEq(fromDateEpochMilli)
                                             .filter(WorkflowExecutionKeys.accountId, accountId)
                                             .field(WorkflowExecutionKeys.pipelineExecutionId)
                                             .doesNotExist();
    AdvancedDatastore datastore = wingsPersistence.getDefaultAnalyticsDatastore(WorkflowExecution.class);
    addRbacAndAppIdFilterToBaseQuery(baseQuery, appIds);

    Query<WorkflowExecution> prodEnvBaseQuery = baseQuery.cloneQuery().filter(WorkflowExecutionKeys.envType, PROD);
    Query<WorkflowExecution> nonProdEnvBaseQuery =
        baseQuery.cloneQuery().field(WorkflowExecutionKeys.envType).notEqual(PROD);

    DeploymentStatistics deploymentStats = new DeploymentStatistics();
    AggregatedDayStats prodEnvStats = getAggregatedDayStats(numOfDays, prodEnvBaseQuery, datastore);
    AggregatedDayStats nonProdEnvStats = getAggregatedDayStats(numOfDays, nonProdEnvBaseQuery, datastore);
    deploymentStats.getStatsMap().put(PROD, prodEnvStats);
    deploymentStats.getStatsMap().put(NON_PROD, nonProdEnvStats);
    deploymentStats.getStatsMap().put(
        ALL, merge(deploymentStats.getStatsMap().get(PROD), deploymentStats.getStatsMap().get(NON_PROD)));
    return deploymentStats;
  }

  private AggregatedDayStats getAggregatedDayStats(
      int numOfDays, Query<WorkflowExecution> baseQuery, AdvancedDatastore datastore) {
    Map<Long, Integer> dayIndexTotalExecutionCountMap = new HashMap<>();
    Map<Long, Integer> dayIndexFailedExecutionCountMap = new HashMap<>();
    Map<Long, Integer> dayIndexInstancesDeployedViaWorkflowCountMap = new HashMap<>();
    Map<Long, Integer> dayIndexInstancesDeployedViaPipelineCountMap = new HashMap<>();

    List<ExecutionCount> totalExecutionCount = getTotalExecutionsPerDay(baseQuery, datastore);
    List<ExecutionCount> failedExecutionCount = getFailedExecutionsPerDay(baseQuery, datastore);
    List<ExecutionCount> instancesDeployedViaWorkflow = getInstancesDeployedViaWorkflowPerDay(baseQuery, datastore);
    List<ExecutionCount> instancesDeployedViaPipeline = getInstancedDeployedViaPipelinePerDay(baseQuery, datastore);

    totalExecutionCount.forEach(executionCount -> populateDayCountMap(dayIndexTotalExecutionCountMap, executionCount));
    failedExecutionCount.forEach(
        executionCount -> populateDayCountMap(dayIndexFailedExecutionCountMap, executionCount));
    instancesDeployedViaPipeline.forEach(
        executionCount -> populateDayCountMap(dayIndexInstancesDeployedViaPipelineCountMap, executionCount));
    instancesDeployedViaWorkflow.forEach(
        executionCount -> populateDayCountMap(dayIndexInstancesDeployedViaWorkflowCountMap, executionCount));
    int aggTotalCount = 0;
    int aggFailureCount = 0;
    int aggInstanceCount = 0;
    List<DayStat> dayStats = new ArrayList<>();

    for (int i = 0; i < numOfDays; i++) {
      Long timeOffset = getEpochMilliPSTZone(numOfDays - i);

      int failedInDay = dayIndexFailedExecutionCountMap.getOrDefault(timeOffset, 0);
      aggFailureCount += failedInDay;

      int totalInDay = dayIndexTotalExecutionCountMap.getOrDefault(timeOffset, 0);
      aggTotalCount += totalInDay;

      int instancesDeployedInDay = 0;
      instancesDeployedInDay += dayIndexInstancesDeployedViaPipelineCountMap.getOrDefault(timeOffset, 0);
      instancesDeployedInDay += dayIndexInstancesDeployedViaWorkflowCountMap.getOrDefault(timeOffset, 0);
      aggInstanceCount += instancesDeployedInDay;

      dayStats.add(DayStat.builder()
                       .totalCount(totalInDay)
                       .failedCount(failedInDay)
                       .instancesCount(instancesDeployedInDay)
                       .date(timeOffset)
                       .build());
    }

    return AggregatedDayStats.builder()
        .daysStats(dayStats)
        .failedCount(aggFailureCount)
        .totalCount(aggTotalCount)
        .instancesCount(aggInstanceCount)
        .build();
  }

  private void populateDayCountMap(Map<Long, Integer> dayIndexTotalExecutionCountMap, ExecutionCount executionCount) {
    long createdAt = executionCount.getCreatedAt();
    long startOfTheDayEpoch = EpochUtils.obtainStartOfTheDayEpoch(createdAt, PST_ZONE_ID);
    dayIndexTotalExecutionCountMap.put(startOfTheDayEpoch, executionCount.getCount());
  }

  private List<ExecutionCount> getInstancedDeployedViaPipelinePerDay(
      Query<WorkflowExecution> baseQuery, AdvancedDatastore datastore) {
    List<ExecutionCount> instancesDeployedViaPipeline = new ArrayList<>();
    Query<WorkflowExecution> pipelineInstancesDeployedQuery = baseQuery.cloneQuery();
    pipelineInstancesDeployedQuery.filter(WorkflowExecutionKeys.workflowType, PIPELINE)
        .field(WorkflowExecutionKeys.pipelineExecution)
        .exists();
    AggregationPipeline pipelineInstancesDeployedAggregation =
        datastore.createAggregation(WorkflowExecution.class)
            .match(pipelineInstancesDeployedQuery)
            .unwind(WorkflowExecutionKeys.pipelineExecution_pipelineStageExecutions)
            .unwind(WorkflowExecutionKeys.pipelineExecution_pipelineStageExecutions + ".workflowExecutions")
            .project(projection("serviceExecutionSummaries",
                         "pipelineExecution.pipelineStageExecutions.workflowExecutions.serviceExecutionSummaries"),
                projection("createdAt", "createdAt"));
    addInstanceDeployedAggregationInternal(pipelineInstancesDeployedAggregation);
    pipelineInstancesDeployedAggregation
        .aggregate(ExecutionCount.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(WorkflowExecution.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(e -> { getExecutionCount(instancesDeployedViaPipeline, e); });
    return instancesDeployedViaPipeline;
  }

  private void getExecutionCount(List<ExecutionCount> resultList, ExecutionCount e) {
    ExecutionCount executionCount =
        ExecutionCount.builder().count(e.getCount())._id(e.get_id()).createdAt(e.getCreatedAt()).build();
    resultList.add(executionCount);
  }

  private List<ExecutionCount> getInstancesDeployedViaWorkflowPerDay(
      Query<WorkflowExecution> baseQuery, AdvancedDatastore datastore) {
    List<ExecutionCount> instancesDeployedViaWorkflow = new ArrayList<>();
    Query<WorkflowExecution> workflowInstancesDeployed = baseQuery.cloneQuery();
    workflowInstancesDeployed.and(
        workflowInstancesDeployed.criteria(WorkflowExecutionKeys.workflowType).equal(ORCHESTRATION));
    AggregationPipeline workflowInstancesDeployedAggregation =
        datastore.createAggregation(WorkflowExecution.class).match(workflowInstancesDeployed);

    addInstanceDeployedAggregationInternal(workflowInstancesDeployedAggregation);
    workflowInstancesDeployedAggregation
        .aggregate(ExecutionCount.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(WorkflowExecution.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(e -> getExecutionCount(instancesDeployedViaWorkflow, e));
    return instancesDeployedViaWorkflow;
  }

  private void addInstanceDeployedAggregationInternal(AggregationPipeline workflowInstancesDeployedAggregation) {
    workflowInstancesDeployedAggregation
        .project(projection("day", expression("$add", new Date(0), "$createdAt")),
            projection(
                WorkflowExecutionKeys.serviceExecutionSummaries, WorkflowExecutionKeys.serviceExecutionSummaries),
            projection("createdAt", "createdAt"))
        .project(
            projection("date",
                expression("$dayOfYear", new BasicDBObject("date", "$day").append("timezone", "America/Los_Angeles"))),
            projection("createdAt", "createdAt"), projection("serviceExecutionSummaries", "serviceExecutionSummaries"))
        .unwind("serviceExecutionSummaries")
        .project(projection("date"), projection("createdAt"),
            projection("instanceIds", "serviceExecutionSummaries.instanceStatusSummaries.instanceElement.uuid"))
        .project(projection("createdAt"), projection("date"),
            projection("instanceIds", expression("$setUnion", "$instanceIds", new ArrayList<>())))
        .match(wingsPersistence.createQuery(WorkflowExecution.class)
                   .disableValidation()
                   .field("instanceIds")
                   .notEqual(null))
        .project(projection("date", "date"), projection("createdAt", "createdAt"),
            expression("instanceCount", new BasicDBObject("$size", "$instanceIds")))
        .group(Group.id(grouping("date")), grouping("createdAt", Group.first("createdAt")),
            grouping("count", accumulator("$sum", "instanceCount")));
  }

  private List<ExecutionCount> getFailedExecutionsPerDay(
      Query<WorkflowExecution> baseQuery, AdvancedDatastore datastore) {
    List<ExecutionCount> failedExecutionCount = new ArrayList<>();
    Query<WorkflowExecution> totalFailedExecutions = baseQuery.cloneQuery();
    totalFailedExecutions.and(
        totalFailedExecutions.criteria(WorkflowExecutionKeys.status).in(ExecutionStatus.negativeStatuses()));
    AggregationPipeline totalFailedExecutionAggregation =
        datastore.createAggregation(WorkflowExecution.class)
            .match(totalFailedExecutions)
            .project(projection("day", expression("$add", new Date(0), "$createdAt")),
                projection("createdAt", "createdAt"), projection("createdAt", "createdAt"))
            .project(projection("date",
                         expression("$dayOfYear",
                             new BasicDBObject("date", "$day").append("timezone", "America/Los_Angeles"))),
                projection("createdAt", "createdAt"))
            .group(Group.id(grouping("date")), grouping("createdAt", Group.first("createdAt")),
                grouping("count", accumulator("$sum", 1)));
    totalFailedExecutionAggregation
        .aggregate(ExecutionCount.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(WorkflowExecution.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(e -> getExecutionCount(failedExecutionCount, e));
    return failedExecutionCount;
  }

  private List<ExecutionCount> getTotalExecutionsPerDay(
      Query<WorkflowExecution> baseQuery, AdvancedDatastore datastore) {
    List<ExecutionCount> totalExecutionCount = new ArrayList<>();

    AggregationPipeline totalExecutionAggregation =
        datastore.createAggregation(WorkflowExecution.class)
            .match(baseQuery)
            .project(
                projection("day", expression("$add", new Date(0), "$createdAt")), projection("createdAt", "createdAt"))
            .project(projection("date",
                         expression("$dayOfYear",
                             new BasicDBObject("date", "$day").append("timezone", "America/Los_Angeles"))),
                projection("createdAt", "createdAt"))
            .group(Group.id(grouping("date")), grouping("createdAt", Group.first("createdAt")),
                grouping("count", accumulator("$sum", 1)));
    totalExecutionAggregation
        .aggregate(ExecutionCount.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(WorkflowExecution.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(e -> getExecutionCount(totalExecutionCount, e));
    return totalExecutionCount;
  }

  @Override
  public ServiceInstanceStatistics getServiceInstanceStatisticsNew(
      String accountId, List<String> appIds, int numOfDays) {
    long fromDateEpochMilli = getEpochMilliPSTZone(numOfDays);

    ServiceInstanceStatistics instanceStats = new ServiceInstanceStatistics();
    Query<WorkflowExecution> baseQuery = wingsPersistence.createQuery(WorkflowExecution.class)
                                             .field(WorkflowExecutionKeys.createdAt)
                                             .greaterThanOrEq(fromDateEpochMilli)
                                             .filter(WorkflowExecutionKeys.accountId, accountId)
                                             .field(WorkflowExecutionKeys.pipelineExecutionId)
                                             .doesNotExist();
    AdvancedDatastore datastore = wingsPersistence.getDefaultAnalyticsDatastore(WorkflowExecution.class);
    addRbacAndAppIdFilterToBaseQuery(baseQuery, appIds);
    baseQuery.field(WorkflowExecutionKeys.status).in(finalStatuses());

    Query<WorkflowExecution> prodEnvBaseQuery = baseQuery.cloneQuery().filter(WorkflowExecutionKeys.envType, PROD);
    Query<WorkflowExecution> nonProdEnvBaseQuery =
        baseQuery.cloneQuery().field(WorkflowExecutionKeys.envType).notEqual(PROD);

    Comparator<TopConsumer> byCount = comparing(TopConsumer::getTotalCount, reverseOrder());

    List<TopConsumer> allEnvTopConsumers = getTopConsumers(baseQuery, datastore, byCount);
    List<TopConsumer> prodEnvTopConsumers = getTopConsumers(prodEnvBaseQuery, datastore, byCount);
    List<TopConsumer> nonProdEnvTopConsumers = getTopConsumers(nonProdEnvBaseQuery, datastore, byCount);

    instanceStats.getStatsMap().put(ALL, allEnvTopConsumers);
    instanceStats.getStatsMap().put(PROD, prodEnvTopConsumers);
    instanceStats.getStatsMap().put(NON_PROD, nonProdEnvTopConsumers);
    return instanceStats;
  }

  private List<TopConsumer> getTopConsumers(
      Query<WorkflowExecution> baseQuery, AdvancedDatastore datastore, Comparator<TopConsumer> comparator) {
    Map<String, TopConsumer> topConsumerMap = new HashMap<>();

    Query<WorkflowExecution> queryForWorkflow = getWorkflowExecutionQuery(baseQuery);
    populateTopConsumerMapForWorkflow(datastore, topConsumerMap, queryForWorkflow);

    Query<WorkflowExecution> queryForPipeline = getPipelineExecutionQuery(baseQuery);
    populateTopConsumerMapForPipeline(datastore, topConsumerMap, queryForPipeline);

    return topConsumerMap.values().stream().sorted(comparator).collect(Collectors.toList());
  }

  private Query<WorkflowExecution> getPipelineExecutionQuery(Query<WorkflowExecution> baseQuery) {
    Query<WorkflowExecution> queryForPipeline = baseQuery.cloneQuery();
    baseQuery.filter(WorkflowExecutionKeys.workflowType, PIPELINE);
    return queryForPipeline;
  }

  @NotNull
  private Query<WorkflowExecution> getWorkflowExecutionQuery(Query<WorkflowExecution> baseQuery) {
    Query<WorkflowExecution> queryForWorkflow = baseQuery.cloneQuery();
    queryForWorkflow.filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION);
    return queryForWorkflow;
  }

  private void populateTopConsumerMapForPipeline(
      AdvancedDatastore datastore, Map<String, TopConsumer> topConsumerMap, Query<WorkflowExecution> query) {
    AggregationPipeline pipelineServiceDeployedAggregation =
        datastore.createAggregation(WorkflowExecution.class)
            .match(query)
            .unwind(WorkflowExecutionKeys.pipelineExecution_pipelineStageExecutions)
            .unwind(WorkflowExecutionKeys.pipelineExecution_pipelineStageExecutions + ".workflowExecutions")
            .project(projection(WorkflowExecutionKeys.serviceExecutionSummaries,
                         WorkflowExecutionKeys.pipelineExecution_pipelineStageExecutions
                             + ".workflowExecutions.serviceExecutionSummaries"),
                projection("createdAt", "createdAt"),
                projection(WorkflowExecutionKeys.status, WorkflowExecutionKeys.status), projection("appId", "appId"),
                projection("appName", "appName"), projection("executionId", "_id"));

    addServiceGroupingLogic(pipelineServiceDeployedAggregation);

    pipelineServiceDeployedAggregation
        .aggregate(ConsumersInfo.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(WorkflowExecution.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(consumersInfo -> { populateTopConsumerMap(topConsumerMap, consumersInfo); });
  }

  private void populateTopConsumerMapForWorkflow(
      AdvancedDatastore datastore, Map<String, TopConsumer> topConsumerMap, Query<WorkflowExecution> queryForWorkflow) {
    AggregationPipeline workflowServiceDeployedAggregation =
        datastore.createAggregation(WorkflowExecution.class)
            .match(queryForWorkflow)
            .project(projection(WorkflowExecutionKeys.serviceExecutionSummaries,
                         WorkflowExecutionKeys.serviceExecutionSummaries),
                projection("createdAt", "createdAt"),
                projection(WorkflowExecutionKeys.status, WorkflowExecutionKeys.status), projection("appId", "appId"),
                projection("appName", "appName"), projection("executionId", "_id"));
    addServiceGroupingLogic(workflowServiceDeployedAggregation);

    workflowServiceDeployedAggregation
        .aggregate(ConsumersInfo.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(WorkflowExecution.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(consumersInfo -> { populateTopConsumerMap(topConsumerMap, consumersInfo); });
  }
  private void populateTopConsumerMap(Map<String, TopConsumer> topConsumerMap, ConsumersInfo consumersInfo) {
    if (!topConsumerMap.containsKey(consumersInfo.getServiceId())) {
      TopConsumerBuilder topConsumerBuilder = TopConsumer.builder()
                                                  .serviceId(consumersInfo.getServiceId())
                                                  .serviceName(consumersInfo.getServiceName())
                                                  .appId(consumersInfo.getAppId())
                                                  .appName(consumersInfo.getAppName())
                                                  .totalCount(consumersInfo.getCount());
      if (consumersInfo.getStatus().equals(SUCCESS)) {
        topConsumerBuilder.successfulActivityCount(consumersInfo.getCount());
      } else {
        topConsumerBuilder.failedActivityCount(consumersInfo.getCount());
      }
      topConsumerMap.put(consumersInfo.getServiceId(), topConsumerBuilder.build());
    } else {
      TopConsumer topConsumer = topConsumerMap.get(consumersInfo.getServiceId());
      if (consumersInfo.getStatus().equals(SUCCESS)) {
        topConsumer.setSuccessfulActivityCount(topConsumer.getSuccessfulActivityCount() + consumersInfo.getCount());
      } else {
        topConsumer.setFailedActivityCount(consumersInfo.getCount() + topConsumer.getFailedActivityCount());
      }
      topConsumer.setTotalCount(topConsumer.getTotalCount() + consumersInfo.getCount());
    }
  }

  private void addServiceGroupingLogic(AggregationPipeline aggregationPipeline) {
    aggregationPipeline.unwind(WorkflowExecutionKeys.serviceExecutionSummaries)
        .project(projection(
                     WorkflowExecutionKeys.serviceExecutionSummaries, WorkflowExecutionKeys.serviceExecutionSummaries),
            projection("createdAt", "createdAt"),
            projection(WorkflowExecutionKeys.status, WorkflowExecutionKeys.status),
            projection("originalStatus",
                expression("$cond",
                    new BasicDBObject(
                        "if", new BasicDBObject("$eq", Arrays.asList("$serviceExecutionSummaries.status", null)))
                        .append("then", "$status")
                        .append("else", "$serviceExecutionSummaries.status"))),
            projection("appId", "appId"), projection("appName", "appName"), projection("executionId", "executionId"))
        .project(projection(
                     WorkflowExecutionKeys.serviceExecutionSummaries, WorkflowExecutionKeys.serviceExecutionSummaries),
            projection("createdAt", "createdAt"),
            projection(WorkflowExecutionKeys.status, WorkflowExecutionKeys.status),
            projection("finalStatus",
                expression("$cond",
                    new BasicDBObject("if", new BasicDBObject("$eq", Arrays.asList("$originalStatus", "SUCCESS")))
                        .append("then", "SUCCESS")
                        .append("else", "FAILED"))),
            projection("appId", "appId"), projection("appName", "appName"), projection("executionId", "executionId"))
        .group(
            Group.id(grouping("serviceId", "serviceExecutionSummaries.contextElement.uuid"), grouping("executionId")),
            grouping("status", Group.last("finalStatus")), grouping("appId", Group.first("appId")),
            grouping("appName", Group.first("appName")),
            grouping("serviceName", Group.first("serviceExecutionSummaries.contextElement.name")))
        .group(Group.id(grouping("serviceId", "_id.serviceId"), grouping("status", "status")),
            grouping("appId", Group.first("appId")), grouping("appName", Group.first("appName")),
            grouping("serviceName", Group.first("serviceName")), grouping("count", accumulator("$sum", 1)))
        .project(projection("status", "_id.status"), projection("serviceId", "_id.serviceId"),
            projection("appId", "appId"), projection("appName", "appName"), projection("serviceName", "serviceName"),
            projection("count", "count"), projection("_id").suppress());
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
          workflowExecutionService.obtainWorkflowExecutions(appIds, fromDateEpochMilli, projectionKeys);
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
