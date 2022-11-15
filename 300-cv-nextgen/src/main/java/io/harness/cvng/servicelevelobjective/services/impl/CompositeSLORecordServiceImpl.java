/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.annotations.retry.RetryOnException;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.beans.SLOValue;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord.CompositeSLORecordKeys;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mongodb.morphia.query.Sort;

public class CompositeSLORecordServiceImpl implements CompositeSLORecordService {
  @VisibleForTesting static int MAX_NUMBER_OF_POINTS = 2000;
  private static final int RETRY_COUNT = 3;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Override
  public void create(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap, int sloVersion,
      String verificationTaskId, Instant startTime, Instant endTime) {
    if (isEmpty(serviceLevelObjectivesDetailCompositeSLORecordMap)) {
      return;
    }
    double runningGoodCount = 0;
    double runningBadCount = 0;
    CompositeSLORecord lastCompositeSLORecord = getLastCompositeSLORecord(verificationTaskId, startTime);
    CompositeSLORecord latestCompositeSLORecord = getLatestCompositeSLORecord(verificationTaskId);
    if (Objects.nonNull(lastCompositeSLORecord)) {
      runningGoodCount = lastCompositeSLORecord.getRunningGoodCount();
      runningBadCount = lastCompositeSLORecord.getRunningBadCount();
    }
    if (Objects.nonNull(latestCompositeSLORecord) && latestCompositeSLORecord.getTimestamp().isAfter(startTime)) {
      // Update flow: fetch CompositeSLO Records to be updated
      updateCompositeSLORecords(serviceLevelObjectivesDetailCompositeSLORecordMap,
          objectivesDetailSLIMissingDataTypeMap, sloVersion, runningGoodCount, runningBadCount, verificationTaskId,
          startTime, endTime);
    } else {
      createCompositeSLORecords(serviceLevelObjectivesDetailCompositeSLORecordMap,
          objectivesDetailSLIMissingDataTypeMap, sloVersion, runningGoodCount, runningBadCount, verificationTaskId);
    }
  }

  @Override
  public SLOGraphData getGraphData(CompositeServiceLevelObjective compositeServiceLevelObjective, Instant startTime,
      Instant endTime, int totalErrorBudgetMinutes, int sloVersion) {
    return getGraphData(compositeServiceLevelObjective, startTime, endTime, totalErrorBudgetMinutes, sloVersion, null);
  }

  @Override
  public SLOGraphData getGraphData(CompositeServiceLevelObjective compositeServiceLevelObjective, Instant startTime,
      Instant endTime, int totalErrorBudgetMinutes, int sloVersion, TimeRangeParams filter) {
    Preconditions.checkState(totalErrorBudgetMinutes != 0, "Total error budget minutes should not be zero.");
    if (Objects.isNull(filter)) {
      filter = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    }
    List<CompositeSLORecord> sloRecords =
        compositeSLORecords(compositeServiceLevelObjective.getUuid(), startTime, endTime, filter);

    List<Point> sloTrend = new ArrayList<>();
    List<Point> errorBudgetBurndown = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    int errorBudgetRemaining = totalErrorBudgetMinutes;
    boolean isReCalculatingSLI = false;
    boolean isCalculatingSLI = false;
    boolean enabled = true;
    if (!sloRecords.isEmpty()) {
      SLOValue sloValue = null;
      double prevRecordGoodCount = 0;
      double prevRecordBadCount = 0;
      CompositeSLORecord lastCompositeSLORecord =
          getLastCompositeSLORecord(compositeServiceLevelObjective.getUuid(), startTime);
      if (Objects.nonNull(lastCompositeSLORecord)) {
        prevRecordGoodCount = lastCompositeSLORecord.getRunningGoodCount();
        prevRecordBadCount = lastCompositeSLORecord.getRunningBadCount();
      }
      for (CompositeSLORecord sloRecord : sloRecords) {
        double goodCountFromStart = sloRecord.getRunningGoodCount() - prevRecordGoodCount;
        double badCountFromStart = sloRecord.getRunningBadCount() - prevRecordBadCount;
        if (sloRecord.getSloVersion() != sloVersion) {
          isReCalculatingSLI = true;
          return SLOGraphData.builder()
              .errorBudgetBurndown(errorBudgetBurndown)
              .errorBudgetRemaining(errorBudgetRemaining)
              .sloPerformanceTrend(sloTrend)
              .isRecalculatingSLI(isReCalculatingSLI)
              .isCalculatingSLI(isCalculatingSLI)
              .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
              .build();
        }
        sloValue = SLOValue.builder().goodCount(goodCountFromStart).badCount(badCountFromStart).build();
        sloTrend.add(SLODashboardWidget.Point.builder()
                         .timestamp(sloRecord.getTimestamp().toEpochMilli())
                         .value(sloValue.sloPercentage())
                         .enabled(enabled)
                         .build());
        errorBudgetBurndown.add(
            SLODashboardWidget.Point.builder()
                .timestamp(sloRecord.getTimestamp().toEpochMilli())
                .value(((totalErrorBudgetMinutes - sloValue.getBadCount()) * 100.0) / totalErrorBudgetMinutes)
                .enabled(enabled)
                .build());
      }
      errorBudgetRemainingPercentage = errorBudgetBurndown.get(errorBudgetBurndown.size() - 1).getValue();
      errorBudgetRemaining = totalErrorBudgetMinutes - (int) sloValue.getBadCount();
    } else {
      isCalculatingSLI = true;
    }

    long startFilter = filter.getStartTime().toEpochMilli();
    long endFilter = filter.getEndTime().toEpochMilli();

    sloTrend = sloTrend.stream()
                   .filter(slo -> slo.getTimestamp() >= startFilter)
                   .filter(slo -> slo.getTimestamp() <= endFilter)
                   .collect(Collectors.toList());
    errorBudgetBurndown = errorBudgetBurndown.stream()
                              .filter(e -> e.getTimestamp() >= startFilter)
                              .filter(e -> e.getTimestamp() <= endFilter)
                              .collect(Collectors.toList());

    return SLOGraphData.builder()
        .errorBudgetBurndown(errorBudgetBurndown)
        .errorBudgetRemaining(errorBudgetRemaining)
        .sloPerformanceTrend(sloTrend)
        .isRecalculatingSLI(isReCalculatingSLI)
        .isCalculatingSLI(isCalculatingSLI)
        .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
        .build();
  }

  private void createCompositeSLORecords(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap, int sloVersion,
      double runningGoodCount, double runningBadCount, String verificationTaskId) {
    Map<Instant, Double> timeStampToGoodValue = new HashMap<>();
    Map<Instant, Double> timeStampToBadValue = new HashMap<>();
    Map<Instant, Integer> timeStampToTotalValue = new HashMap<>();
    getTimeStampToValueMaps(serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap,
        timeStampToGoodValue, timeStampToBadValue, timeStampToTotalValue);
    List<CompositeSLORecord> sloRecordList = new ArrayList<>();
    for (Instant instant : ImmutableSortedSet.copyOf(timeStampToTotalValue.keySet())) {
      if (timeStampToTotalValue.get(instant).equals(serviceLevelObjectivesDetailCompositeSLORecordMap.size())) {
        runningGoodCount += timeStampToGoodValue.getOrDefault(instant, 0.0);
        runningBadCount += timeStampToBadValue.getOrDefault(instant, 0.0);
        CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                           .runningBadCount(runningBadCount)
                                           .runningGoodCount(runningGoodCount)
                                           .sloId(verificationTaskId)
                                           .sloVersion(sloVersion)
                                           .verificationTaskId(verificationTaskId)
                                           .timestamp(instant)
                                           .build();
        sloRecordList.add(sloRecord);
      }
    }
    hPersistence.save(sloRecordList);
  }

  @RetryOnException(retryCount = RETRY_COUNT, retryOn = ConcurrentModificationException.class)
  public void updateCompositeSLORecords(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap, int sloVersion,
      double runningGoodCount, double runningBadCount, String verificationTaskId, Instant startTime, Instant endTime) {
    List<CompositeSLORecord> toBeUpdatedSLORecords =
        getSLORecords(verificationTaskId, startTime, endTime.plus(1, ChronoUnit.MINUTES));
    Map<Instant, CompositeSLORecord> sloRecordMap =
        toBeUpdatedSLORecords.stream().collect(Collectors.toMap(CompositeSLORecord::getTimestamp, Function.identity()));
    List<CompositeSLORecord> updateOrCreateSLORecords = new ArrayList<>();
    Map<Instant, Double> timeStampToGoodValue = new HashMap<>();
    Map<Instant, Double> timeStampToBadValue = new HashMap<>();
    Map<Instant, Integer> timeStampToTotalValue = new HashMap<>();
    getTimeStampToValueMaps(serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap,
        timeStampToGoodValue, timeStampToBadValue, timeStampToTotalValue);
    for (Instant instant : ImmutableSortedSet.copyOf(timeStampToTotalValue.keySet())) {
      if (timeStampToTotalValue.get(instant).equals(serviceLevelObjectivesDetailCompositeSLORecordMap.size())) {
        CompositeSLORecord sloRecord = sloRecordMap.get(instant);
        runningGoodCount += timeStampToGoodValue.getOrDefault(instant, 0.0);
        runningBadCount += timeStampToBadValue.getOrDefault(instant, 0.0);
        if (Objects.nonNull(sloRecord)) {
          sloRecord.setRunningGoodCount(runningGoodCount);
          sloRecord.setRunningBadCount(runningBadCount);
          sloRecord.setSloVersion(sloVersion);
        } else {
          sloRecord = CompositeSLORecord.builder()
                          .runningBadCount(runningBadCount)
                          .runningGoodCount(runningGoodCount)
                          .sloId(verificationTaskId)
                          .sloVersion(sloVersion)
                          .verificationTaskId(verificationTaskId)
                          .timestamp(instant)
                          .build();
        }
        updateOrCreateSLORecords.add(sloRecord);
      }
    }
    hPersistence.save(updateOrCreateSLORecords);
  }

  private List<CompositeSLORecord> compositeSLORecords(
      String sloId, Instant startTime, Instant endTime, TimeRangeParams filter) {
    CompositeSLORecord firstRecord = getFirstCompositeSLORecord(sloId, startTime);
    CompositeSLORecord lastRecord = getLastCompositeSLORecord(sloId, endTime);
    CompositeSLORecord firstRecordInRange = getFirstCompositeSLORecord(sloId, filter.getStartTime());
    CompositeSLORecord lastRecordInRange = getLastCompositeSLORecord(sloId, filter.getEndTime());
    if (firstRecordInRange == null || lastRecordInRange == null) {
      return Collections.emptyList();
    } else {
      startTime = firstRecordInRange.getTimestamp();
      endTime = lastRecordInRange.getTimestamp().plus(Duration.ofMinutes(1));
    }
    List<Instant> minutes = new ArrayList<>();
    long totalMinutes = Duration.between(startTime, endTime).toMinutes();
    long diff = totalMinutes / MAX_NUMBER_OF_POINTS;
    if (diff == 0) {
      diff = 1L;
    }
    minutes.add(firstRecord.getTimestamp());
    minutes.add(startTime);
    Duration diffDuration = Duration.ofMinutes(diff);
    for (Instant current = startTime.plus(Duration.ofMinutes(diff)); current.isBefore(endTime);
         current = current.plus(diffDuration)) {
      minutes.add(current);
    }
    minutes.add(endTime.minus(Duration.ofMinutes(1)));
    minutes.add(lastRecord.getTimestamp());
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .in(minutes)
        .order(Sort.ascending(CompositeSLORecordKeys.timestamp))
        .asList();
  }

  @Override
  public List<CompositeSLORecord> getSLORecords(String sloId, Instant startTimeStamp, Instant endTimeStamp) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .greaterThanOrEq(startTimeStamp)
        .field(CompositeSLORecordKeys.timestamp)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(CompositeSLORecordKeys.timestamp))
        .asList();
  }

  private CompositeSLORecord getLastCompositeSLORecord(String sloId, Instant startTimeStamp) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .lessThan(startTimeStamp)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .get();
  }
  private CompositeSLORecord getFirstCompositeSLORecord(String sloId, Instant timestampInclusive) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .greaterThanOrEq(timestampInclusive)
        .order(Sort.ascending(CompositeSLORecordKeys.timestamp))
        .get();
  }

  @Override
  public CompositeSLORecord getLatestCompositeSLORecord(String sloId) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .get();
  }

  private void getTimeStampToValueMaps(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap,
      Map<Instant, Double> timeStampToGoodValue, Map<Instant, Double> timeStampToBadValue,
      Map<Instant, Integer> timeStampToTotalValue) {
    for (ServiceLevelObjectivesDetail objectivesDetail : serviceLevelObjectivesDetailCompositeSLORecordMap.keySet()) {
      for (SLIRecord sliRecord : serviceLevelObjectivesDetailCompositeSLORecordMap.get(objectivesDetail)) {
        if (SLIRecord.SLIState.GOOD.equals(sliRecord.getSliState())
            || (SLIRecord.SLIState.NO_DATA.equals(sliRecord.getSliState())
                && objectivesDetailSLIMissingDataTypeMap.get(objectivesDetail).equals(SLIMissingDataType.GOOD))) {
          double goodCount = timeStampToGoodValue.getOrDefault(sliRecord.getTimestamp(), 0.0);
          goodCount += objectivesDetail.getWeightagePercentage() / 100;
          timeStampToGoodValue.put(sliRecord.getTimestamp(), goodCount);
        } else if (SLIRecord.SLIState.BAD.equals(sliRecord.getSliState())
            || (SLIRecord.SLIState.NO_DATA.equals(sliRecord.getSliState())
                && objectivesDetailSLIMissingDataTypeMap.get(objectivesDetail).equals(SLIMissingDataType.BAD))) {
          double badCount = timeStampToBadValue.getOrDefault(sliRecord.getTimestamp(), 0.0);
          badCount += objectivesDetail.getWeightagePercentage() / 100;
          timeStampToBadValue.put(sliRecord.getTimestamp(), badCount);
        }
        timeStampToTotalValue.put(
            sliRecord.getTimestamp(), timeStampToTotalValue.getOrDefault(sliRecord.getTimestamp(), 0) + 1);
      }
    }
  }

  @Override
  public CompositeSLORecord getLatestCompositeSLORecordWithVersion(String sloId, int sloVersion) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .filter(CompositeSLORecordKeys.sloVersion, sloVersion)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .get();
  }
}
