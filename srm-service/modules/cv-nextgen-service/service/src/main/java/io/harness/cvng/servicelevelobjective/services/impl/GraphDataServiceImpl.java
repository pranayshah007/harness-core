/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLIValue;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLOValue;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord.CompositeSLORecordKeys;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.Sort;

public class GraphDataServiceImpl implements GraphDataService {
  @Inject SLIRecordService sliRecordService;
  @Inject CompositeSLORecordService compositeSLORecordService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject EntityDisabledTimeService entityDisabledTimeService;
  @Inject HPersistence hPersistence;
  @VisibleForTesting static int MAX_NUMBER_OF_POINTS = 2000;

  @Override
  public SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes) {
    return getGraphData(serviceLevelObjective, startTime, endTime, totalErrorBudgetMinutes, null);
  }

  @Override
  public SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter) {
    if (serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE)) {
      CompositeServiceLevelObjective compositeServiceLevelObjective =
          (CompositeServiceLevelObjective) serviceLevelObjective;
      return getGraphData(compositeServiceLevelObjective, startTime, endTime, totalErrorBudgetMinutes,
          compositeServiceLevelObjective.getVersion(), filter);
    } else {
      SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(simpleServiceLevelObjective.getAccountId())
                                        .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
                                        .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
                                        .build();

      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          projectParams, simpleServiceLevelObjective.getServiceLevelIndicators().get(0));
      return getGraphData(serviceLevelIndicator, startTime, endTime, totalErrorBudgetMinutes,
          serviceLevelIndicator.getSliMissingDataType(), serviceLevelIndicator.getVersion(), filter);
    }
  }

  public SLODashboardWidget.SLOGraphData getGraphData(CompositeServiceLevelObjective compositeServiceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, int sloVersion, TimeRangeParams filter) {
    Preconditions.checkState(totalErrorBudgetMinutes != 0, "Total error budget minutes should not be zero.");
    if (Objects.isNull(filter)) {
      filter = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    }
    List<CompositeSLORecord> sloRecords =
        compositeSLORecords(compositeServiceLevelObjective.getUuid(), startTime, endTime, filter);

    List<SLODashboardWidget.Point> sloTrend = new ArrayList<>();
    List<SLODashboardWidget.Point> errorBudgetBurndown = new ArrayList<>();
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
          compositeSLORecordService.getLastCompositeSLORecord(compositeServiceLevelObjective.getUuid(), startTime);
      if (Objects.nonNull(lastCompositeSLORecord)) {
        prevRecordGoodCount = lastCompositeSLORecord.getRunningGoodCount();
        prevRecordBadCount = lastCompositeSLORecord.getRunningBadCount();
      }
      for (CompositeSLORecord sloRecord : sloRecords) {
        double goodCountFromStart = sloRecord.getRunningGoodCount() - prevRecordGoodCount;
        double badCountFromStart = sloRecord.getRunningBadCount() - prevRecordBadCount;
        if (sloRecord.getSloVersion() != sloVersion) {
          isReCalculatingSLI = true;
          return SLODashboardWidget.SLOGraphData.builder()
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

    return SLODashboardWidget.SLOGraphData.builder()
        .errorBudgetBurndown(errorBudgetBurndown)
        .errorBudgetRemaining(errorBudgetRemaining)
        .sloPerformanceTrend(sloTrend)
        .isRecalculatingSLI(isReCalculatingSLI)
        .isCalculatingSLI(isCalculatingSLI)
        .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
        .build();
  }

  public SLODashboardWidget.SLOGraphData getGraphData(ServiceLevelIndicator serviceLevelIndicator, Instant startTime,
      Instant endTime, int totalErrorBudgetMinutes, SLIMissingDataType sliMissingDataType, int sliVersion,
      TimeRangeParams filter) {
    Preconditions.checkState(totalErrorBudgetMinutes != 0, "Total error budget minutes should not be zero.");
    if (Objects.isNull(filter)) {
      filter = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    }
    List<SLIRecord> sliRecords = sliRecords(serviceLevelIndicator.getUuid(), startTime, endTime, filter);
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(serviceLevelIndicator.getAccountId())
            .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
            .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
            .monitoredServiceIdentifier(serviceLevelIndicator.getMonitoredServiceIdentifier())
            .build();

    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(monitoredServiceParams);
    List<EntityDisableTime> disableTimes =
        entityDisabledTimeService.get(monitoredService.getUuid(), monitoredService.getAccountId());
    int currentDisabledRange = 0;
    long disabledMinutesFromStart = 0;
    int currentSLIRecord = 0;

    List<SLODashboardWidget.Point> sliTread = new ArrayList<>();
    List<SLODashboardWidget.Point> errorBudgetBurndown = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    double sliStatusPercentage = 0;
    int errorBudgetRemaining = totalErrorBudgetMinutes;
    int badCountTillRangeEndTime = 0;
    int badCountTillRangeStartTime = 0;
    boolean getBadCountTillRangeStartTime = true;
    boolean isReCalculatingSLI = false;
    boolean isCalculatingSLI = false;
    if (!sliRecords.isEmpty()) {
      SLIValue sliValue = null;
      long beginningMinute = sliRecords.get(0).getEpochMinute();
      SLIRecord firstRecord = sliRecords.get(0);
      long prevRecordGoodCount =
          firstRecord.getRunningGoodCount() - (firstRecord.getSliState() == SLIRecord.SLIState.GOOD ? 1 : 0);
      long prevRecordBadCount =
          firstRecord.getRunningBadCount() - (firstRecord.getSliState() == SLIRecord.SLIState.BAD ? 1 : 0);
      for (SLIRecord sliRecord : sliRecords) {
        long goodCountFromStart = sliRecord.getRunningGoodCount() - prevRecordGoodCount;
        long badCountFromStart = sliRecord.getRunningBadCount() - prevRecordBadCount;
        long minutesFromStart = sliRecord.getEpochMinute() - beginningMinute + 1;
        boolean enabled = true;
        if (!disableTimes.isEmpty() && currentDisabledRange <= disableTimes.size() && currentSLIRecord != 0) {
          Pair<Long, Long> disabledMinData =
              getDisabledMinBetweenRecords(sliRecords.get(currentSLIRecord - 1).getTimestamp().toEpochMilli(),
                  sliRecord.getTimestamp().toEpochMilli(), currentDisabledRange, disableTimes);
          disabledMinutesFromStart += disabledMinData.getLeft();
          currentDisabledRange = Math.toIntExact(disabledMinData.getRight());
        }
        if (currentDisabledRange < disableTimes.size()) {
          enabled = !disableTimes.get(currentDisabledRange).contains(sliRecord.getTimestamp().toEpochMilli());
        }
        if (currentDisabledRange > 0) {
          enabled =
              enabled && !disableTimes.get(currentDisabledRange - 1).contains(sliRecord.getTimestamp().toEpochMilli());
        }
        if (!isCalculatingSLI && sliRecord.getSliVersion() != sliVersion) {
          isReCalculatingSLI = true;
          return SLODashboardWidget.SLOGraphData.builder()
              .errorBudgetBurndown(errorBudgetBurndown)
              .errorBudgetRemaining(errorBudgetRemaining)
              .sloPerformanceTrend(sliTread)
              .isRecalculatingSLI(isReCalculatingSLI)
              .isCalculatingSLI(isCalculatingSLI)
              .sliStatusPercentage(sliStatusPercentage)
              .errorBudgetBurned(Math.max(badCountTillRangeEndTime - badCountTillRangeStartTime, 0))
              .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
              .build();
        }
        sliValue = sliMissingDataType.calculateSLIValue(
            goodCountFromStart, badCountFromStart, minutesFromStart, disabledMinutesFromStart);

        if (getBadCountTillRangeStartTime
            && !sliRecord.getTimestamp().isBefore(DateTimeUtils.roundDownTo1MinBoundary(filter.getStartTime()))) {
          badCountTillRangeStartTime = sliValue.getBadCount();
          if (sliRecord.getSliState().equals(SLIRecord.SLIState.BAD)) {
            badCountTillRangeStartTime--;
          }
          getBadCountTillRangeStartTime = false;
        }
        if (!sliRecord.getTimestamp().isAfter(DateTimeUtils.roundDownTo1MinBoundary(filter.getEndTime()))) {
          badCountTillRangeEndTime = sliValue.getBadCount();
        }

        sliTread.add(SLODashboardWidget.Point.builder()
                         .timestamp(sliRecord.getTimestamp().toEpochMilli())
                         .value(sliValue.sliPercentage())
                         .enabled(enabled)
                         .build());
        errorBudgetBurndown.add(
            SLODashboardWidget.Point.builder()
                .timestamp(sliRecord.getTimestamp().toEpochMilli())
                .value(((totalErrorBudgetMinutes - sliValue.getBadCount()) * 100.0) / totalErrorBudgetMinutes)
                .enabled(enabled)
                .build());
        currentSLIRecord++;
      }
      errorBudgetRemainingPercentage = errorBudgetBurndown.get(errorBudgetBurndown.size() - 1).getValue();
      sliStatusPercentage = sliTread.get(sliTread.size() - 1).getValue();
      errorBudgetRemaining = totalErrorBudgetMinutes - sliValue.getBadCount();
    } else {
      isCalculatingSLI = true;
    }

    long startFilter = filter.getStartTime().toEpochMilli();
    long endFilter = filter.getEndTime().toEpochMilli();

    sliTread = sliTread.stream()
                   .filter(sli -> sli.getTimestamp() >= startFilter)
                   .filter(sli -> sli.getTimestamp() <= endFilter)
                   .collect(Collectors.toList());
    errorBudgetBurndown = errorBudgetBurndown.stream()
                              .filter(e -> e.getTimestamp() >= startFilter)
                              .filter(e -> e.getTimestamp() <= endFilter)
                              .collect(Collectors.toList());

    return SLODashboardWidget.SLOGraphData.builder()
        .errorBudgetBurndown(errorBudgetBurndown)
        .errorBudgetRemaining(errorBudgetRemaining)
        .sloPerformanceTrend(sliTread)
        .isRecalculatingSLI(isReCalculatingSLI)
        .isCalculatingSLI(isCalculatingSLI)
        .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
        .errorBudgetBurned(Math.max(badCountTillRangeEndTime - badCountTillRangeStartTime, 0))
        .sliStatusPercentage(sliStatusPercentage)
        .build();
  }

  @VisibleForTesting
  public Pair<Long, Long> getDisabledMinBetweenRecords(
      long startTime, long endTime, int currentRange, List<EntityDisableTime> disableTimes) {
    long extra = 0;
    while (currentRange < disableTimes.size() && disableTimes.get(currentRange).getStartTime() <= endTime) {
      long x = disableTimes.get(currentRange).getStartTime();
      long y = disableTimes.get(currentRange).getEndTime();

      x = (long) (Math.ceil(x / 60000) * 60000);
      y = (long) (Math.floor(y / 60000) * 60000);

      if (y <= startTime) {
        currentRange++;
      } else if (x > startTime && y <= endTime) {
        extra += y - x + 60000;
        currentRange++;
      } else if (x == startTime && y <= endTime) {
        extra += y - x;
        currentRange++;
      } else if (x < startTime && y <= endTime) {
        extra += y - startTime;
        currentRange++;
      } else if (x > startTime && y > endTime) {
        extra += endTime - x + 60000;
        break;
      } else if (x == startTime && y > endTime) {
        extra += endTime - x;
        break;
      } else if (x < startTime && y > endTime) {
        extra += endTime - startTime;
        break;
      }
    }

    return Pair.of(extra / 60000, (long) currentRange);
  }

  private List<CompositeSLORecord> compositeSLORecords(
      String sloId, Instant startTime, Instant endTime, TimeRangeParams filter) {
    CompositeSLORecord firstRecord = compositeSLORecordService.getFirstCompositeSLORecord(sloId, startTime);
    CompositeSLORecord lastRecord = compositeSLORecordService.getLastCompositeSLORecord(sloId, endTime);
    CompositeSLORecord firstRecordInRange =
        compositeSLORecordService.getFirstCompositeSLORecord(sloId, filter.getStartTime());
    CompositeSLORecord lastRecordInRange =
        compositeSLORecordService.getLastCompositeSLORecord(sloId, filter.getEndTime());
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

  private List<SLIRecord> sliRecords(String sliId, Instant startTime, Instant endTime, TimeRangeParams filter) {
    SLIRecord firstRecord = sliRecordService.getFirstSLIRecord(sliId, startTime);
    SLIRecord lastRecord = sliRecordService.getLastSLIRecord(sliId, endTime);
    SLIRecord firstRecordInRange = sliRecordService.getFirstSLIRecord(sliId, filter.getStartTime());
    SLIRecord lastRecordInRange = sliRecordService.getLastSLIRecord(sliId, filter.getEndTime());
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
    // long reminder = totalMinutes % maxNumberOfPoints;
    minutes.add(firstRecord.getTimestamp());
    minutes.add(startTime);
    Duration diffDuration = Duration.ofMinutes(diff);
    for (Instant current = startTime.plus(Duration.ofMinutes(diff)); current.isBefore(endTime);
         current = current.plus(diffDuration)) {
      minutes.add(current);
    }
    minutes.add(endTime.minus(Duration.ofMinutes(1)));
    minutes.add(lastRecord.getTimestamp()); // always include start and end minute.
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .in(minutes)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .asList();
  }
}
