/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.services.impl.SLIRecordBucketServiceImpl.SLI_RECORD_BUCKET_SIZE;

import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLIValue;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataServiceV2;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

public class GraphDataServiceV2Impl implements GraphDataServiceV2 {
  public static final int CALCULATING_STATE_SLI_MINUTES = 10;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SLIRecordBucketService sliRecordBucketService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject EntityDisabledTimeService entityDisabledTimeService;
  @Inject GraphDataServiceImpl graphDataServiceImpl;
  @Inject Clock clock;
  @VisibleForTesting public static final int MAX_NUMBER_OF_POINTS = 2000;

  @Override
  public SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter) {
    return getGraphData(
        serviceLevelObjective, startTime, endTime, totalErrorBudgetMinutes, filter, MAX_NUMBER_OF_POINTS);
  }

  @Override
  public SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter,
      long numOfDataPointsInBetween) {
    if (serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE)) {
      CompositeServiceLevelObjective compositeServiceLevelObjective =
          (CompositeServiceLevelObjective) serviceLevelObjective;
      return graphDataServiceImpl.getGraphDataForCompositeSLO(compositeServiceLevelObjective, startTime, endTime,
          totalErrorBudgetMinutes, compositeServiceLevelObjective.getVersion(), filter, numOfDataPointsInBetween);

    } else {
      SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(simpleServiceLevelObjective.getAccountId())
                                        .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                                        .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                                        .build();

      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      // just passing ID to get one SLO
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          projectParams, simpleServiceLevelObjective.getServiceLevelIndicators().get(0)); // As this is simple SLO
      return getGraphDataForSimpleSLO(serviceLevelIndicator, startTime, endTime, totalErrorBudgetMinutes, filter,
          serviceLevelObjective, numOfDataPointsInBetween);
    }
  }

  public SLODashboardWidget.SLOGraphData getGraphDataForSimpleSLO(ServiceLevelIndicator serviceLevelIndicator,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter,
      AbstractServiceLevelObjective serviceLevelObjective, long numOfDataPointsInBetween) {
    SLIMissingDataType sliMissingDataType = serviceLevelIndicator.getSliMissingDataType();
    int sliVersion = serviceLevelIndicator.getVersion();
    filter = validateAndgetTimeRangeParams(serviceLevelIndicator, startTime, endTime, totalErrorBudgetMinutes, filter);
    List<SLIRecordBucket> sliRecordBuckets = sliRecordBucketService.getSLIRecordBucketsForFilterRange(
        serviceLevelIndicator.getUuid(), startTime, endTime, filter, numOfDataPointsInBetween);
    List<SLODashboardWidget.Point> sliTrends = new ArrayList<>();
    List<SLODashboardWidget.Point> errorBudgetBurndowns = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    double sliStatusPercentage = 0;
    long initialErrorBudget =
        serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW ? totalErrorBudgetMinutes : 0;
    long totalErrorBudget = initialErrorBudget;
    long errorBudgetRemaining = initialErrorBudget;
    long errorBudgetBurned = 0L;
    boolean isCalculatingSLI = false;
    if (!sliRecordBuckets.isEmpty()) {
      long disabledMinutesFromStart = 0;
      long skippedRecordFromStart = 0;
      long badCountTillRangeEndTime = 0L;
      long badCountTillRangeStartTime = -1L;
      int currentDisabledRangeIndex = 0;
      long sliBeginningMinute = sliRecordBuckets.get(0).getBucketStartTime().getEpochSecond() / 60;
      List<EntityDisableTime> disableTimes = getEntityDisableTimes(serviceLevelIndicator);
      SLIRecordBucket firstSLIRecord = sliRecordBuckets.get(0);
      SLIValue lastSLIValue = SLIValue.builder().build();
      Pair<Long, Long> sliBaselineRunningCountPair =
          sliRecordBucketService.getPreviousBucketRunningCount(firstSLIRecord, serviceLevelIndicator);
      for (int currentSLIBucketIndex = 0; currentSLIBucketIndex < sliRecordBuckets.size(); currentSLIBucketIndex++) {
        SLIRecordBucket currentSLIRecordBucket = sliRecordBuckets.get(currentSLIBucketIndex);
        long currentBucketEndTime = currentSLIRecordBucket.getBucketStartTime()
                                        .plus(SLI_RECORD_BUCKET_SIZE - 1, ChronoUnit.MINUTES)
                                        .toEpochMilli();
        if (CollectionUtils.isNotEmpty(disableTimes) && currentDisabledRangeIndex <= disableTimes.size()
            && currentSLIBucketIndex != 0) {
          long lastBucketEndTime = sliRecordBuckets.get(currentSLIBucketIndex - 1)
                                       .getBucketStartTime()
                                       .plus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES)
                                       .toEpochMilli();
          Pair<Long, Long> disabledMinutesWithIndexPair = entityDisabledTimeService.getDisabledMinBetweenRecords(
              lastBucketEndTime, currentBucketEndTime, currentDisabledRangeIndex, disableTimes);
          currentDisabledRangeIndex = Math.toIntExact(disabledMinutesWithIndexPair.getRight());
          disabledMinutesFromStart += disabledMinutesWithIndexPair.getLeft();
        }
        if (currentSLIRecordBucket.getSliVersion() != sliVersion) {
          return getSloGraphDataForVersionMismatch(serviceLevelIndicator, errorBudgetRemaining, totalErrorBudget,
              errorBudgetRemainingPercentage, errorBudgetBurndowns, sliTrends, sliStatusPercentage, errorBudgetBurned);
        }
        skippedRecordFromStart +=
            currentSLIRecordBucket.getSliStates().stream().filter(sliState -> sliState == SLIState.SKIP_DATA).count();

        SLIValue currentSLIValue = sliRecordBucketService.calculateSLIValue(
            serviceLevelIndicator.getSLIEvaluationType(), sliMissingDataType, currentSLIRecordBucket,
            sliBaselineRunningCountPair, sliBeginningMinute, skippedRecordFromStart, disabledMinutesFromStart);

        if (currentSLIBucketIndex == sliRecordBuckets.size() - 1) {
          lastSLIValue = currentSLIValue;
        }
        badCountTillRangeStartTime = getBadCountForSelectedRangeStart(serviceLevelIndicator, sliMissingDataType, filter,
            badCountTillRangeStartTime, currentSLIRecordBucket, currentSLIValue);
        badCountTillRangeEndTime =
            getBadCountForSelectedRangeEnd(filter, currentSLIRecordBucket, badCountTillRangeEndTime, currentSLIValue);
        errorBudgetBurned = Math.max(badCountTillRangeEndTime - badCountTillRangeStartTime, 0);
        boolean isPointEnabled = entityDisabledTimeService.isMinuteEnabled(disableTimes, currentDisabledRangeIndex,
            currentSLIRecordBucket); // TODO how to handle disabling in parital windows
        if (isBucketWithinFilterRange(filter, currentSLIRecordBucket)) {
          sliTrends.add(SLODashboardWidget.Point.builder()
                            .timestamp(currentBucketEndTime)
                            .value(currentSLIValue.sliPercentage())
                            .enabled(isPointEnabled)
                            .build());
          errorBudgetBurndowns.add(SLODashboardWidget.Point.builder()
                                       .timestamp(currentBucketEndTime)
                                       .value(getErrorBudgetValue(serviceLevelIndicator, totalErrorBudgetMinutes,
                                           currentSLIValue, serviceLevelObjective))
                                       .enabled(isPointEnabled)
                                       .build());
        }
      }
      totalErrorBudget =
          getTotalErrorBudget(serviceLevelIndicator, totalErrorBudgetMinutes, lastSLIValue, serviceLevelObjective);
      errorBudgetRemaining = totalErrorBudget - lastSLIValue.getBadCount();
      errorBudgetRemainingPercentage = errorBudgetBurndowns.get(errorBudgetBurndowns.size() - 1).getValue();
      sliStatusPercentage = sliTrends.get(sliTrends.size() - 1).getValue();
    } else if (Instant.ofEpochMilli(serviceLevelIndicator.getCreatedAt())
                   .isBefore(clock.instant().minus(Duration.ofMinutes(CALCULATING_STATE_SLI_MINUTES)))) {
      isCalculatingSLI = true;
    }
    return SLODashboardWidget.SLOGraphData
        .getSloGraphDataBuilder(errorBudgetRemainingPercentage, errorBudgetRemaining,
            filterWidgetPoints(errorBudgetBurndowns, filter), filterWidgetPoints(sliTrends, filter), false,
            isCalculatingSLI, totalErrorBudget)
        .sliStatusPercentage(sliStatusPercentage)
        .errorBudgetBurned(errorBudgetBurned)
        .evaluationType(serviceLevelIndicator.getSLIEvaluationType())
        .build();
  }

  private static boolean isBucketWithinFilterRange(TimeRangeParams filter, SLIRecordBucket currentSLIRecordBucket) {
    return !filter.getStartTime().isAfter(currentSLIRecordBucket.getBucketStartTime());
  }

  private long getBadCountForSelectedRangeStart(ServiceLevelIndicator serviceLevelIndicator,
      SLIMissingDataType sliMissingDataType, TimeRangeParams filter, long badCountTillRangeStartTime,
      SLIRecordBucket sliRecordBucket, SLIValue sliValue) {
    if (badCountTillRangeStartTime < 0
        && !sliRecordBucket.getBucketStartTime().isBefore(
            DateTimeUtils.roundDownTo1MinBoundary(filter.getStartTime()))) {
      Long previousBadRunningCount =
          sliRecordBucketService.getPreviousBucketRunningCount(sliRecordBucket, serviceLevelIndicator).getRight();
      badCountTillRangeStartTime = sliRecordBucketService.getBadCountTillRangeStartTime(
          serviceLevelIndicator, sliMissingDataType, sliValue, sliRecordBucket, previousBadRunningCount);
    }
    return badCountTillRangeStartTime;
  }

  private static TimeRangeParams validateAndgetTimeRangeParams(ServiceLevelIndicator serviceLevelIndicator,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter) {
    Preconditions.checkState(
        !(totalErrorBudgetMinutes == 0 && serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW),
        "Total error budget minutes should not be zero.");
    if (Objects.isNull(filter)) {
      filter = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    }
    return filter;
  }

  private static long getBadCountForSelectedRangeEnd(
      TimeRangeParams filter, SLIRecordBucket sliRecordBucket, long badCountTillRangeEndTime, SLIValue sliValue) {
    if (!sliRecordBucket.getBucketStartTime().isAfter(DateTimeUtils.roundDownTo1MinBoundary(filter.getEndTime()))) {
      badCountTillRangeEndTime = sliValue.getBadCount();
    }
    return badCountTillRangeEndTime;
  }

  private static SLODashboardWidget.SLOGraphData getSloGraphDataForVersionMismatch(
      ServiceLevelIndicator serviceLevelIndicator, long errorBudgetRemaining, long totalErrorBudget,
      double errorBudgetRemainingPercentage, List<SLODashboardWidget.Point> errorBudgetBurndowns,
      List<SLODashboardWidget.Point> sliTrends, double sliStatusPercentage, long errorBudgetBurned) {
    if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.REQUEST) {
      errorBudgetRemaining = 0;
      totalErrorBudget = 0;
    }
    return SLODashboardWidget.SLOGraphData
        .getSloGraphDataBuilder(errorBudgetRemainingPercentage, errorBudgetRemaining, errorBudgetBurndowns, sliTrends,
            true, false, totalErrorBudget)
        .sliStatusPercentage(sliStatusPercentage)
        .errorBudgetBurned(errorBudgetBurned)
        .evaluationType(serviceLevelIndicator.getSLIEvaluationType())
        .build();
  }

  private List<EntityDisableTime> getEntityDisableTimes(ServiceLevelIndicator serviceLevelIndicator) {
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(serviceLevelIndicator.getAccountId())
            .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
            .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
            .monitoredServiceIdentifier(serviceLevelIndicator.getMonitoredServiceIdentifier())
            .build();
    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(monitoredServiceParams);
    return entityDisabledTimeService.get(monitoredService.getUuid(), monitoredService.getAccountId());
  }
  private List<SLODashboardWidget.Point> filterWidgetPoints(
      List<SLODashboardWidget.Point> dataPoints, TimeRangeParams filter) {
    long startFilter = filter.getStartTime().toEpochMilli();
    long endFilter = filter.getEndTime().toEpochMilli();
    return dataPoints.stream()
        .filter(dataPoint -> dataPoint.getTimestamp() >= startFilter)
        .filter(dataPoint -> dataPoint.getTimestamp() <= endFilter)
        .collect(Collectors.toList());
  }

  public static double getErrorBudgetValue(ServiceLevelIndicator serviceLevelIndicator, long totalErrorBudgetMinutes,
      SLIValue sliValue, AbstractServiceLevelObjective serviceLevelObjective) {
    long totalErrorBudget =
        getTotalErrorBudget(serviceLevelIndicator, totalErrorBudgetMinutes, sliValue, serviceLevelObjective);
    if (totalErrorBudget == 0L) {
      return 100.0;
    }
    return ((totalErrorBudget - sliValue.getBadCount()) * 100.0) / totalErrorBudget;
  }

  public static long getTotalErrorBudget(ServiceLevelIndicator serviceLevelIndicator, long totalErrorBudgetMinutes,
      SLIValue sliValue, AbstractServiceLevelObjective serviceLevelObjective) {
    if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
      return totalErrorBudgetMinutes;
    } else {
      return (long) ((100.0 - serviceLevelObjective.getSloTargetPercentage()) * sliValue.getTotal()) / 100;
    }
  }
}
