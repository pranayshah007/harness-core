/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.core.utils.DateTimeUtils.roundDownToMinBoundary;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.HistoricalTrend;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapKeys;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk.HeatMapRiskKeys;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.utils.CVNGParallelExecutor;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.DBCollectionUpdateOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import org.mongodb.morphia.UpdateOptions;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.Query;

public class HeatMapServiceImpl implements HeatMapService {
  private static final int RISK_TIME_BUFFER_MINS = 15;
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;
  @Inject private CVNGParallelExecutor cvngParallelExecutor;

  @Override
  public void updateRiskScore(String accountId, String orgIdentifier, String projectIdentifier, CVConfig cvConfig,
      CVMonitoringCategory category, Instant timeStamp, double riskScore, long anomalousMetricsCount,
      long anomalousLogsCount) {
    List<Callable<Void>> callables = new ArrayList<>();
    // update for service/env
    callables.add(() -> {
      updateRiskScore(category, accountId, orgIdentifier, projectIdentifier, cvConfig.getMonitoredServiceIdentifier(),
          timeStamp, riskScore, anomalousMetricsCount, anomalousLogsCount);
      return null;
    });

    cvngParallelExecutor.executeParallel(callables);
  }

  private void updateRiskScore(CVMonitoringCategory category, String accountId, String orgIdentifier,
      String projectIdentifier, String monitoredServiceIdentifier, Instant timeStamp, double riskScore,
      long anomalousMetricsCount, long anomalousLogsCount) {
    UpdateOptions options = new UpdateOptions();
    options.upsert(true);
    for (HeatMapResolution heatMapResolution : HeatMapResolution.values()) {
      Instant bucketStartTime = getBoundaryOfResolution(timeStamp, heatMapResolution.getBucketSize());
      Instant bucketEndTime = bucketStartTime.plusMillis(heatMapResolution.getBucketSize().toMillis());
      Instant heatMapStartTime = getBoundaryOfResolution(timeStamp, heatMapResolution.getResolution());
      Instant heatMapEndTime = heatMapStartTime.plusMillis(heatMapResolution.getResolution().toMillis());

      Query<HeatMap> heatMapQuery = hPersistence.createQuery(HeatMap.class)
                                        .filter(HeatMapKeys.accountId, accountId)
                                        .filter(HeatMapKeys.orgIdentifier, orgIdentifier)
                                        .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                        .filter(HeatMapKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
                                        .filter(HeatMapKeys.category, category)
                                        .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                        .filter(HeatMapKeys.heatMapBucketStartTime, bucketStartTime)
                                        .filter(HeatMapKeys.heatMapBucketEndTime, bucketEndTime);

      // first create the heatmap record if it doesn't exists
      hPersistence.getDatastore(HeatMap.class)
          .update(heatMapQuery,
              hPersistence.createUpdateOperations(HeatMap.class)
                  .setOnInsert(HeatMapKeys.accountId, accountId)
                  .setOnInsert(HeatMapKeys.validUntil, HeatMap.builder().build().getValidUntil())
                  .addToSet(HeatMapKeys.heatMapRisks,
                      HeatMapRisk.builder()
                          .riskScore(riskScore)
                          .startTime(heatMapStartTime)
                          .endTime(heatMapEndTime)
                          .build()),
              options);

      DBCollectionUpdateOptions arrayFilterOptions = new DBCollectionUpdateOptions();
      arrayFilterOptions.upsert(true);
      arrayFilterOptions.multi(false);
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put("elem." + HeatMapRiskKeys.startTime, heatMapStartTime);
      filterMap.put("elem." + HeatMapRiskKeys.endTime, heatMapEndTime);
      filterMap.put("elem." + HeatMapRiskKeys.riskScore, new BasicDBObject("$lt", riskScore));
      arrayFilterOptions.arrayFilters(Lists.newArrayList(new BasicDBObject(filterMap)));
      hPersistence.getCollection(HeatMap.class)
          .update(heatMapQuery.getQueryObject(),
              new BasicDBObject("$set",
                  new BasicDBObject(HeatMapKeys.heatMapRisks + ".$[elem]." + HeatMapRiskKeys.riskScore, riskScore)),
              arrayFilterOptions);

      /**
       * Update anomalous metrics and logs count in all heatmap risk objects
       * */

      filterMap = new HashMap<>();
      filterMap.put("elem." + HeatMapRiskKeys.startTime, heatMapStartTime);
      filterMap.put("elem." + HeatMapRiskKeys.endTime, heatMapEndTime);
      arrayFilterOptions.arrayFilters(Lists.newArrayList(new BasicDBObject(filterMap)));

      BasicDBObject updateObject = new BasicDBObject("$inc",
          new BasicDBObject(
              HeatMapKeys.heatMapRisks + ".$[elem]." + HeatMapRiskKeys.anomalousMetricsCount, anomalousMetricsCount)
              .append(HeatMapKeys.heatMapRisks + ".$[elem]." + HeatMapRiskKeys.anomalousLogsCount, anomalousLogsCount));
      hPersistence.getCollection(HeatMap.class).update(heatMapQuery.getQueryObject(), updateObject, arrayFilterOptions);
    }
  }

  private List<HeatMap> getLatestHeatMaps(
      ProjectParams projectParams, long riskTimeBufferMins, List<String> monitoredServiceIdentifiers) {
    HeatMapResolution heatMapResolution = HeatMapResolution.FIVE_MIN;
    Instant bucketEndTime = roundDownTo5MinBoundary(clock.instant()).minus(riskTimeBufferMins, ChronoUnit.MINUTES);
    Query<HeatMap> heatMapQuery = hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                      .filter(HeatMapKeys.accountId, projectParams.getAccountIdentifier())
                                      .filter(HeatMapKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                      .filter(HeatMapKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                      .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                      .field(HeatMapKeys.heatMapBucketEndTime)
                                      .greaterThanOrEq(bucketEndTime);
    if (monitoredServiceIdentifiers != null) {
      heatMapQuery.field(HeatMapKeys.monitoredServiceIdentifier).in(monitoredServiceIdentifiers);
    }
    List<HeatMap> heatMapList = heatMapQuery.asList();
    Map<HeatMapKey, HeatMap> heatMapMap = new HashMap<>();
    heatMapList.forEach(heatMap -> {
      HeatMapKey key = new HeatMapKey(heatMap.getMonitoredServiceIdentifier(), heatMap.getCategory());
      if (!heatMapMap.containsKey(key)) {
        heatMapMap.put(key, heatMap);
      }
      if (heatMapMap.get(key).getHeatMapBucketEndTime().isBefore(heatMap.getHeatMapBucketEndTime())) {
        heatMapMap.put(key, heatMap);
      }
    });
    List<HeatMap> uniqueHeatMaps = new ArrayList<>();
    heatMapMap.forEach((key, value) -> {
      SortedSet<HeatMapRisk> risks = new TreeSet<>(
          value.getHeatMapRisks().stream().filter(x -> x.getRiskScore() != -1).collect(Collectors.toList()));
      if (isNotEmpty(risks)) {
        HeatMapRisk last = risks.last();
        if (last.getEndTime().isAfter(bucketEndTime)) {
          value.setHeatMapRisks(Lists.newArrayList(last));
          uniqueHeatMaps.add(value);
        }
      }
    });
    return uniqueHeatMaps;
  }

  @Override
  public Map<String, RiskData> getLatestHealthScore(
      @NonNull ProjectParams projectParams, @NonNull List<String> monitoredServiceIdentifiers) {
    Map<String, RiskData> latestHealthScoresMap = new HashMap<>();
    List<HeatMap> latestHeatMaps = getLatestHeatMaps(projectParams, RISK_TIME_BUFFER_MINS, monitoredServiceIdentifiers);

    latestHeatMaps.stream()
        .collect(Collectors.groupingBy(HeatMap::getMonitoredServiceIdentifier))
        .forEach((monitoredServiceIdentifier, heatMaps) -> {
          double riskScore = heatMaps.stream()
                                 .mapToDouble(x -> x.getHeatMapRisks().iterator().next().getRiskScore())
                                 .max()
                                 .orElse(-1.0);
          latestHealthScoresMap.put(monitoredServiceIdentifier,
              RiskData.builder()
                  .healthScore(Risk.getHealthScoreFromRiskScore(riskScore))
                  .riskStatus(Risk.getRiskFromRiskScore(riskScore))
                  .build());
        });
    return latestHealthScoresMap;
  }

  @Value
  @AllArgsConstructor
  private static class HeatMapKey {
    String monitoredServiceIdentifier;
    CVMonitoringCategory category;
  }

  @Override
  public List<HistoricalTrend> getHistoricalTrend(String accountId, String orgIdentifier, String projectIdentifier,
      List<String> monitoredServiceIdentifiers, int hours) {
    Preconditions.checkArgument(monitoredServiceIdentifiers.size() <= 10,
        "Based on page size, the health score calculation should be done for less than 10 services");
    int bucketsBasedOn30MinFrame = hours * 2;
    int size = monitoredServiceIdentifiers.size();
    if (size == 0) {
      return Collections.emptyList();
    }
    List<HistoricalTrend> historicalTrendList = new ArrayList<>();
    Map<String, Integer> monitoredServiceIndex = new HashMap<>();

    Instant endTime = roundDownToMinBoundary(clock.instant(), 30);
    Instant startTime = endTime.minus(hours, ChronoUnit.HOURS);

    for (int i = 0; i < size; i++) {
      historicalTrendList.add(HistoricalTrend.builder()
                                  .size(bucketsBasedOn30MinFrame)
                                  .trendStartTime(startTime)
                                  .trendEndTime(endTime)
                                  .build());
      monitoredServiceIndex.put(monitoredServiceIdentifiers.get(i), i);
    }

    HeatMapResolution heatMapResolution = HeatMapResolution.THIRTY_MINUTES;

    Query<HeatMap> heatMapQuery = hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                      .filter(HeatMapKeys.accountId, accountId)
                                      .filter(HeatMapKeys.orgIdentifier, orgIdentifier)
                                      .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                      .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                      .field(HeatMapKeys.heatMapBucketEndTime)
                                      .greaterThan(startTime);

    Criteria criterias[] = new Criteria[size];

    for (int i = 0; i < size; i++) {
      criterias[i] =
          heatMapQuery.criteria(HeatMapKeys.monitoredServiceIdentifier).equal(monitoredServiceIdentifiers.get(i));
    }
    heatMapQuery.or(criterias);
    List<HeatMap> heatMaps = heatMapQuery.asList();

    heatMaps.forEach(heatMap -> {
      SortedSet<HeatMapRisk> risks = new TreeSet<>(heatMap.getHeatMapRisks());
      risks.forEach(heatMapRisk -> {
        int index = getIndex(bucketsBasedOn30MinFrame, heatMapRisk.getEndTime(), endTime, heatMapResolution);
        if (index >= 0 && index < bucketsBasedOn30MinFrame) {
          int indexPosition = monitoredServiceIndex.get(heatMap.getMonitoredServiceIdentifier());
          RiskData riskData = historicalTrendList.get(indexPosition).getHealthScores().get(index);
          Integer healthScore = Risk.getHealthScoreFromRiskScore(heatMapRisk.getRiskScore());
          Risk risk = Risk.getRiskFromRiskScore(heatMapRisk.getRiskScore());
          if (healthScore != null
              && (riskData.getHealthScore() == null || riskData.getHealthScore().compareTo(healthScore) == 1)) {
            riskData.setHealthScore(healthScore);
            riskData.setRiskStatus(risk);
          }
        }
      });
    });
    return historicalTrendList;
  }

  private int getIndex(int totalSize, Instant timeFrame, Instant endTime, HeatMapResolution heatMapResolution) {
    return totalSize - 1
        - (int) ChronoUnit.MINUTES.between(timeFrame, endTime) / (int) heatMapResolution.getResolution().toMinutes();
  }

  @Override
  public Map<String, RiskData> getLatestRiskScoreByMonitoredService(
      ProjectParams projectParams, List<String> monitoredServiceIdentifiers) {
    Preconditions.checkArgument(monitoredServiceIdentifiers.size() <= 100,
        "Based on page size, the health score calculation should be done for less than 100 services");
    int size = monitoredServiceIdentifiers.size();
    if (size == 0) {
      return Collections.emptyMap();
    }
    Map<String, RiskData> monitoredServiceIdentifierToRiskDataMap = new HashMap<>();

    for (String monitoredServiceIdentifier : monitoredServiceIdentifiers) {
      monitoredServiceIdentifierToRiskDataMap.put(
          monitoredServiceIdentifier, RiskData.builder().riskStatus(Risk.NO_DATA).healthScore(null).build());
    }
    Map<String, RiskData> latestHealthScores = getLatestHealthScore(projectParams, monitoredServiceIdentifiers);
    latestHealthScores.forEach((key, value) -> {
      if (monitoredServiceIdentifierToRiskDataMap.containsKey(key)) {
        monitoredServiceIdentifierToRiskDataMap.put(key, value);
      }
    });

    return monitoredServiceIdentifierToRiskDataMap;
  }

  @Override
  public boolean isEveryHeatMapBelowThresholdForRiskTimeBuffer(ProjectParams projectParams,
      String monitoredServiceIdentifier, double healthScoreThreshold, long riskTimeBufferMins) {
    Preconditions.checkArgument(monitoredServiceIdentifier != null, "Monitored Service Identifier cannot be null");
    List<HeatMap> latestHeatMaps =
        getLatestHeatMaps(projectParams, riskTimeBufferMins, Arrays.asList(monitoredServiceIdentifier));
    if (isEmpty(latestHeatMaps)) {
      return false;
    }

    for (HeatMap latestHeatMap : latestHeatMaps) {
      for (HeatMapRisk heatMapRisk : latestHeatMap.getHeatMapRisks()) {
        Integer healthScore = Risk.getHealthScoreFromRiskScore(heatMapRisk.getRiskScore());
        if (healthScore != null && healthScore >= healthScoreThreshold) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public List<RiskData> getLatestRiskScoreForAllServicesList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> monitoredServiceIdentifiers) {
    int size = monitoredServiceIdentifiers.size();
    if (size == 0) {
      return Collections.emptyList();
    }
    List<RiskData> latestRiskScoreList = new ArrayList<>();
    Map<String, Integer> serviceEnvironmentIndex = new HashMap<>();

    for (int i = 0; i < size; i++) {
      latestRiskScoreList.add(RiskData.builder().riskStatus(Risk.NO_DATA).healthScore(null).build());
      serviceEnvironmentIndex.put(monitoredServiceIdentifiers.get(i), i);
    }
    // todo: pass this project params from calling method.
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    Map<String, RiskData> latestHealthScores = getLatestHealthScore(projectParams, monitoredServiceIdentifiers);

    latestHealthScores.forEach((key, value) -> {
      if (serviceEnvironmentIndex.containsKey(key)) {
        int index = serviceEnvironmentIndex.get(key);
        latestRiskScoreList.set(index, value);
      }
    });
    return latestRiskScoreList;
  }

  @Override
  public HistoricalTrend getOverAllHealthScore(
      ProjectParams projectParams, String monitoredServiceIdentifier, DurationDTO duration, Instant endTime) {
    HistoricalTrend historicalTrend = getHealthScoreBars(projectParams, monitoredServiceIdentifier, duration, endTime,
        HeatMapResolution.resolutionForDurationDTO(duration));
    historicalTrend.reduceHealthScoreDataToXPoints(
        historicalTrend.getHealthScores().size() / CVNextGenConstants.CVNG_TIMELINE_BUCKET_COUNT);
    return historicalTrend;
  }

  private HistoricalTrend getHealthScoreBars(ProjectParams projectParams, String monitoredServiceIdentifier,
      DurationDTO duration, Instant endTime, HeatMapResolution resolutionToReadFrom) {
    Preconditions.checkState(duration.getDuration().toMinutes() % resolutionToReadFrom.getResolution().toMinutes() == 0
            && (duration.getDuration().toMinutes() / resolutionToReadFrom.getResolution().toMinutes()) % 48 == 0,
        String.format("Cannot calculate health score bar for the given duration %s per minutes %s", duration,
            resolutionToReadFrom.getResolution().toMinutes()));
    int totalSize = (int) duration.getDuration().toMinutes() / (int) resolutionToReadFrom.getResolution().toMinutes();
    HeatMapResolution heatMapResolution = resolutionToReadFrom;
    Instant trendEndTime = resolutionToReadFrom.getNextResolutionEndTime(endTime);
    Instant trendStartTime = trendEndTime.minus(duration.getDuration());
    HistoricalTrend historicalTrend =
        HistoricalTrend.builder().size(totalSize).trendStartTime(trendStartTime).trendEndTime(trendEndTime).build();
    Query<HeatMap> heatMapQuery = hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                      .filter(HeatMapKeys.accountId, projectParams.getAccountIdentifier())
                                      .filter(HeatMapKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                      .filter(HeatMapKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                      .filter(HeatMapKeys.monitoredServiceIdentifier, monitoredServiceIdentifier)
                                      .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                      .field(HeatMapKeys.heatMapBucketEndTime)
                                      .greaterThan(trendStartTime)
                                      .field(HeatMapKeys.heatMapBucketStartTime)
                                      .lessThanOrEq(trendEndTime);
    List<HeatMap> heatMaps = heatMapQuery.asList();

    heatMaps.forEach(heatMap -> {
      SortedSet<HeatMapRisk> risks = new TreeSet<>(heatMap.getHeatMapRisks());
      risks.forEach(heatMapRisk -> {
        int index = getIndex(totalSize, heatMapRisk.getEndTime(), trendEndTime, heatMapResolution);
        if (index >= 0 && index < totalSize) {
          RiskData riskData = historicalTrend.getHealthScores().get(index);
          Integer healthScore = Risk.getHealthScoreFromRiskScore(heatMapRisk.getRiskScore());
          Risk risk = Risk.getRiskFromRiskScore(heatMapRisk.getRiskScore());
          if (healthScore != null
              && (riskData.getHealthScore() == null || riskData.getHealthScore().compareTo(healthScore) == 1)) {
            riskData.setHealthScore(healthScore);
            riskData.setRiskStatus(risk);
          }
        }
      });
    });
    return historicalTrend;
  }

  private Instant getBoundaryOfResolution(Instant input, Duration resolution) {
    long timeStamp = input.toEpochMilli();
    return Instant.ofEpochMilli(timeStamp - (timeStamp % resolution.toMillis()));
  }
}
