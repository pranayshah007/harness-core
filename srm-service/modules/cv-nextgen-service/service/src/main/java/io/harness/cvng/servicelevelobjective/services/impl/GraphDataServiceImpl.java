/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

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
import io.harness.cvng.servicelevelobjective.beans.SLOValue;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class GraphDataServiceImpl implements GraphDataService {
  @Inject SLIRecordService sliRecordService;
  @Inject CompositeSLORecordService compositeSLORecordService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject EntityDisabledTimeService entityDisabledTimeService;
  @Inject HPersistence hPersistence;
  @Inject private Clock clock;
  @VisibleForTesting static int MAX_NUMBER_OF_POINTS = 2000;

  @Override
  public SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, long numOfDataPointsInBetween) {
    return getGraphData(
        serviceLevelObjective, startTime, endTime, totalErrorBudgetMinutes, null, numOfDataPointsInBetween);
  }

  @Override
  public SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter) {
    return getGraphData(
        serviceLevelObjective, startTime, endTime, totalErrorBudgetMinutes, filter, MAX_NUMBER_OF_POINTS);
  }

  private SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter,
      long numOfDataPointsInBetween) {
    if (serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE)) {
      CompositeServiceLevelObjective compositeServiceLevelObjective =
          (CompositeServiceLevelObjective) serviceLevelObjective;
      return getGraphDataForCompositeSLO(compositeServiceLevelObjective, startTime, endTime, totalErrorBudgetMinutes,
          compositeServiceLevelObjective.getVersion(), filter, numOfDataPointsInBetween);
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
      return getGraphDataForSimpleSLO(serviceLevelIndicator, startTime, endTime, totalErrorBudgetMinutes,
          serviceLevelIndicator.getSliMissingDataType(), serviceLevelIndicator.getVersion(), filter,
          serviceLevelObjective, numOfDataPointsInBetween);
    }
  }

  public SLODashboardWidget.SLOGraphData getGraphDataForCompositeSLO(
      CompositeServiceLevelObjective compositeServiceLevelObjective, Instant startTime, Instant endTime,
      int totalErrorBudgetMinutes, int sloVersion, TimeRangeParams filter, long numOfDataPointsInBetween) {
    Preconditions.checkState(totalErrorBudgetMinutes != 0, "Total error budget minutes should not be zero.");
    if (Objects.isNull(filter)) {
      filter = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    }
    List<CompositeSLORecord> sloRecords = getCompositeSLORecords(
        compositeServiceLevelObjective.getUuid(), startTime, endTime, filter, numOfDataPointsInBetween);
    SLIEvaluationType evaluationType =
        serviceLevelObjectiveV2Service
            .getEvaluationType(ProjectParams.builder()
                                   .accountIdentifier(compositeServiceLevelObjective.getAccountId())
                                   .orgIdentifier(compositeServiceLevelObjective.getOrgIdentifier())
                                   .projectIdentifier(compositeServiceLevelObjective.getProjectIdentifier())
                                   .build(),
                Collections.singletonList(compositeServiceLevelObjective))
            .get(compositeServiceLevelObjective);
    List<SLODashboardWidget.Point> sloTrend = new ArrayList<>();
    List<SLODashboardWidget.Point> errorBudgetBurndown = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    int errorBudgetRemaining = totalErrorBudgetMinutes;
    boolean isCalculatingSLI = false;
    boolean enabled = true;
    if (!sloRecords.isEmpty()) {
      SLOValue sloValue = null;
      double prevRecordGoodCount = 0;
      double prevRecordBadCount = 0;
      CompositeSLORecord lastCompositeSLORecord =
          compositeSLORecordService.getLastCompositeSLORecord(compositeServiceLevelObjective.getUuid(), startTime);
      if (Objects.isNull(lastCompositeSLORecord)) {
        Map<String, SLIRecord> scopedIdentifierSLIRecordMap = sliRecordService.getLastCompositeSLOsSLIRecord(
            compositeServiceLevelObjective.getServiceLevelObjectivesDetails(), startTime);
        lastCompositeSLORecord = CompositeSLORecord.builder()
                                     .runningGoodCount(0)
                                     .runningBadCount(0)
                                     .sloId(compositeServiceLevelObjective.getUuid())
                                     .scopedIdentifierSLIRecordMap(scopedIdentifierSLIRecordMap)
                                     .timestamp(startTime.minus(Duration.ofMinutes(1)))
                                     .sloVersion(compositeServiceLevelObjective.getVersion())
                                     .build();
      }
      prevRecordGoodCount = lastCompositeSLORecord.getRunningGoodCount();
      prevRecordBadCount = lastCompositeSLORecord.getRunningBadCount();

      for (CompositeSLORecord sloRecord : sloRecords) {
        double goodCountFromStart = sloRecord.getRunningGoodCount() - prevRecordGoodCount;
        double badCountFromStart = sloRecord.getRunningBadCount() - prevRecordBadCount;
        if (sloRecord.getSloVersion() != sloVersion) {
          return SLODashboardWidget.SLOGraphData
              .getSloGraphDataBuilder(errorBudgetRemainingPercentage, errorBudgetRemaining, errorBudgetBurndown,
                  sloTrend, true, isCalculatingSLI, totalErrorBudgetMinutes)
              .build();
        }
        sloValue = SLOValue.builder().goodCount(goodCountFromStart).badCount(badCountFromStart).build();
        sloTrend.add(SLODashboardWidget.Point.builder()
                         .timestamp(sloRecord.getTimestamp().toEpochMilli())
                         .value(getSLOValue(sloValue, compositeServiceLevelObjective, sloRecord, lastCompositeSLORecord,
                             evaluationType))
                         .enabled(enabled)
                         .build());

        errorBudgetBurndown.add(
            SLODashboardWidget.Point.builder()
                .timestamp(sloRecord.getTimestamp().toEpochMilli())
                .value(getBudgetBurnDown(sloValue, totalErrorBudgetMinutes, compositeServiceLevelObjective, sloRecord,
                    lastCompositeSLORecord, evaluationType))
                .enabled(enabled)
                .build());
      }
      errorBudgetRemainingPercentage = errorBudgetBurndown.get(errorBudgetBurndown.size() - 1).getValue();
      errorBudgetRemaining =
          evaluationType == SLIEvaluationType.WINDOW ? totalErrorBudgetMinutes - (int) sloValue.getBadCount() : 0;
    } else if (Instant.ofEpochMilli(compositeServiceLevelObjective.getStartedAt())
                   .isBefore(clock.instant().minus(Duration.ofMinutes(10)))) {
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

    int totalErrorBudget = evaluationType == SLIEvaluationType.WINDOW ? totalErrorBudgetMinutes : 0;

    return SLODashboardWidget.SLOGraphData
        .getSloGraphDataBuilder(errorBudgetRemainingPercentage, errorBudgetRemaining, errorBudgetBurndown, sloTrend,
            false, isCalculatingSLI, totalErrorBudget)
        .evaluationType(evaluationType)
        .build();
  }

  private double getSLOValue(SLOValue sloValue, CompositeServiceLevelObjective compositeServiceLevelObjective,
      CompositeSLORecord sloRecord, CompositeSLORecord prevSLORecord, SLIEvaluationType evaluationType) {
    if (evaluationType == SLIEvaluationType.WINDOW) {
      return sloValue.sloPercentage();
    }
    double sloPercentage = 0.0;
    for (CompositeServiceLevelObjective.ServiceLevelObjectivesDetail serviceLevelObjectivesDetail :
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails()) {
      Double weightage = serviceLevelObjectivesDetail.getWeightagePercentage() / 100;
      SLIRecord sliRecord = sloRecord.getScopedIdentifierSLIRecordMap().get(
          serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjectivesDetail));
      SLIRecord prevSLIRecord = prevSLORecord.getScopedIdentifierSLIRecordMap().get(
          serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjectivesDetail));
      sloPercentage += weightage * (SLIValue.getRunningCountDifference(sliRecord, prevSLIRecord).sliPercentage());
    }
    return sloPercentage;
  }

  private double getBudgetBurnDown(SLOValue sloValue, int totalErrorBudgetMinutes,
      CompositeServiceLevelObjective compositeServiceLevelObjective, CompositeSLORecord sloRecord,
      CompositeSLORecord prevSLORecord, SLIEvaluationType evaluationType) {
    if (evaluationType == SLIEvaluationType.WINDOW) {
      return ((totalErrorBudgetMinutes - sloValue.getBadCount()) * 100.0) / totalErrorBudgetMinutes;
    }
    double sloErrorBudgetBurnDown = 0.0;
    for (CompositeServiceLevelObjective.ServiceLevelObjectivesDetail serviceLevelObjectivesDetail :
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails()) {
      Double weightage = serviceLevelObjectivesDetail.getWeightagePercentage() / 100;
      SLIRecord sliRecord = sloRecord.getScopedIdentifierSLIRecordMap().get(
          serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjectivesDetail));
      SLIRecord prevSLIRecord = prevSLORecord.getScopedIdentifierSLIRecordMap().get(
          serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjectivesDetail));
      SLIValue sliValue = SLIValue.getRunningCountDifference(sliRecord, prevSLIRecord);
      double totalErrorBudget =
          (sliValue.getTotal() * (100 - compositeServiceLevelObjective.getSloTargetPercentage())) / 100;
      sloErrorBudgetBurnDown += weightage * ((totalErrorBudget - sliValue.getBadCount()) * 100) / totalErrorBudget;
    }
    return sloErrorBudgetBurnDown;
  }

  public SLODashboardWidget.SLOGraphData getGraphDataForSimpleSLO(ServiceLevelIndicator serviceLevelIndicator,
      Instant startTime, Instant endTime, int totalErrorBudgetMinutes, SLIMissingDataType sliMissingDataType,
      int sliVersion, TimeRangeParams filter, AbstractServiceLevelObjective serviceLevelObjective,
      long numOfDataPointsInBetween) {
    Preconditions.checkState(
        !(totalErrorBudgetMinutes == 0 && serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW),
        "Total error budget minutes should not be zero.");
    if (Objects.isNull(filter)) {
      filter = TimeRangeParams.builder().startTime(startTime).endTime(endTime).build();
    }
    List<SLIRecord> sliRecords =
        getSLIRecords(serviceLevelIndicator.getUuid(), startTime, endTime, filter, numOfDataPointsInBetween);
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(serviceLevelIndicator.getAccountId())
            .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
            .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
            .monitoredServiceIdentifier(serviceLevelIndicator.getMonitoredServiceIdentifier())
            .build();

    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(monitoredServiceParams);
    List<EntityDisableTime> disableTimes = entityDisabledTimeService.get(
        monitoredService.getUuid(), monitoredService.getAccountId()); // Not Need... for Request
    int currentDisabledRange = 0;
    long disabledMinutesFromStart = 0;
    int skipRecordCount = 0;
    int currentSLIRecord = 0;

    List<SLODashboardWidget.Point> sliTrend = new ArrayList<>();
    List<SLODashboardWidget.Point> errorBudgetBurndown = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    double sliStatusPercentage = 0;
    long initialErrorBudget =
        serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW ? totalErrorBudgetMinutes : 0;
    long totalErrorBudget = initialErrorBudget;
    long errorBudgetRemaining = initialErrorBudget;
    long badCountTillRangeEndTime = 0;
    long badCountTillRangeStartTime = 0;
    boolean getBadCountTillRangeStartTime = true;
    boolean isCalculatingSLI = false;
    if (!sliRecords.isEmpty()) {
      SLIValue sliValue = null;
      long beginningMinute = sliRecords.get(0).getEpochMinute();
      SLIRecord firstRecord = sliRecords.get(0);
      Pair<Long, Long> previousRunningCount = getPreviousRunningCount(firstRecord, serviceLevelIndicator);
      long lastRecordBeforeStartGoodCount = previousRunningCount.getLeft();
      long lastRecordBeforeStartBadCount = previousRunningCount.getRight();

      for (SLIRecord sliRecord : sliRecords) {
        long goodCountFromStart = sliRecord.getRunningGoodCount() - lastRecordBeforeStartGoodCount;
        long badCountFromStart = sliRecord.getRunningBadCount() - lastRecordBeforeStartBadCount;
        if (sliRecord.getSliState().equals(SLIRecord.SLIState.SKIP_DATA)) {
          skipRecordCount += 1;
        }
        long minutesFromStart = sliRecord.getEpochMinute() - beginningMinute + 1;

        if (!disableTimes.isEmpty() && currentDisabledRange <= disableTimes.size() && currentSLIRecord != 0) {
          Pair<Long, Long> disabledMinData =
              getDisabledMinBetweenRecords(sliRecords.get(currentSLIRecord - 1).getTimestamp().toEpochMilli(),
                  sliRecord.getTimestamp().toEpochMilli(), currentDisabledRange, disableTimes);
          disabledMinutesFromStart += disabledMinData.getLeft();
          currentDisabledRange = Math.toIntExact(disabledMinData.getRight());
        }

        boolean enabled = isMinuteEnabled(disableTimes, currentDisabledRange, sliRecord);

        if (sliRecord.getSliVersion() != sliVersion) {
          if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.REQUEST) {
            errorBudgetRemaining = 0;
            totalErrorBudget = 0;
          }
          return SLODashboardWidget.SLOGraphData
              .getSloGraphDataBuilder(errorBudgetRemainingPercentage, errorBudgetRemaining, errorBudgetBurndown,
                  sliTrend, true, false, totalErrorBudget)
              .sliStatusPercentage(sliStatusPercentage)
              .errorBudgetBurned(Math.max(badCountTillRangeEndTime - badCountTillRangeStartTime, 0))
              .evaluationType(serviceLevelIndicator.getSLIEvaluationType())
              .build();
        }

        if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
          sliValue = sliMissingDataType.calculateSLIValue(
              goodCountFromStart + skipRecordCount, badCountFromStart, minutesFromStart, disabledMinutesFromStart);
        } else {
          sliValue = SLIValue.builder()
                         .goodCount(goodCountFromStart)
                         .badCount(badCountFromStart)
                         .total(goodCountFromStart + badCountFromStart)
                         .build();
        }

        if (getBadCountTillRangeStartTime
            && !sliRecord.getTimestamp().isBefore(DateTimeUtils.roundDownTo1MinBoundary(filter.getStartTime()))) {
          badCountTillRangeStartTime = sliValue.getBadCount();
          if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
            if (sliRecord.getSliState().equals(SLIRecord.SLIState.BAD)
                || (sliRecord.getSliState().equals(SLIRecord.SLIState.NO_DATA)
                    && sliMissingDataType == SLIMissingDataType.BAD)) {
              badCountTillRangeStartTime--;
            }
          } else {
            badCountTillRangeStartTime -=
                sliRecord.getRunningBadCount() - getPreviousRunningCount(sliRecord, serviceLevelIndicator).getRight();
          }
          getBadCountTillRangeStartTime = false;
        }
        if (!sliRecord.getTimestamp().isAfter(DateTimeUtils.roundDownTo1MinBoundary(filter.getEndTime()))) {
          badCountTillRangeEndTime = sliValue.getBadCount();
        }

        sliTrend.add(SLODashboardWidget.Point.builder()
                         .timestamp(sliRecord.getTimestamp().toEpochMilli())
                         .value(sliValue.sliPercentage())
                         .enabled(enabled)
                         .build());
        errorBudgetBurndown.add(SLODashboardWidget.Point.builder()
                                    .timestamp(sliRecord.getTimestamp().toEpochMilli())
                                    .value(getErrorBudgetValue(serviceLevelIndicator, totalErrorBudgetMinutes, sliValue,
                                        serviceLevelObjective))
                                    .enabled(enabled)
                                    .build());
        currentSLIRecord++;
      }

      errorBudgetRemainingPercentage = errorBudgetBurndown.get(errorBudgetBurndown.size() - 1).getValue();
      sliStatusPercentage = sliTrend.get(sliTrend.size() - 1).getValue();
      totalErrorBudget =
          getTotalErrorBudget(serviceLevelIndicator, totalErrorBudgetMinutes, sliValue, serviceLevelObjective);
      errorBudgetRemaining = totalErrorBudget - sliValue.getBadCount();
    } else if (Instant.ofEpochMilli(serviceLevelIndicator.getCreatedAt())
                   .isBefore(clock.instant().minus(Duration.ofMinutes(10)))) {
      isCalculatingSLI = true;
    }

    sliTrend = filterWidgetPoints(sliTrend, filter);
    errorBudgetBurndown = filterWidgetPoints(errorBudgetBurndown, filter);

    return SLODashboardWidget.SLOGraphData
        .getSloGraphDataBuilder(errorBudgetRemainingPercentage, errorBudgetRemaining, errorBudgetBurndown, sliTrend,
            false, isCalculatingSLI, totalErrorBudget)
        .sliStatusPercentage(sliStatusPercentage)
        .errorBudgetBurned(Math.max(badCountTillRangeEndTime - badCountTillRangeStartTime, 0))
        .evaluationType(serviceLevelIndicator.getSLIEvaluationType())
        .build();
  }

  private double getErrorBudgetValue(ServiceLevelIndicator serviceLevelIndicator, long totalErrorBudgetMinutes,
      SLIValue sliValue, AbstractServiceLevelObjective serviceLevelObjective) {
    long totalErrorBudget =
        getTotalErrorBudget(serviceLevelIndicator, totalErrorBudgetMinutes, sliValue, serviceLevelObjective);
    if (totalErrorBudget == 0l) {
      return 100.0;
    }
    return ((totalErrorBudget - sliValue.getBadCount()) * 100.0) / totalErrorBudget;
  }

  private long getTotalErrorBudget(ServiceLevelIndicator serviceLevelIndicator, long totalErrorBudgetMinutes,
      SLIValue sliValue, AbstractServiceLevelObjective serviceLevelObjective) {
    if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
      return totalErrorBudgetMinutes;
    } else {
      return (long) ((100.0 - serviceLevelObjective.getSloTargetPercentage()) * sliValue.getTotal()) / 100;
    }
  }

  private Pair<Long, Long> getPreviousRunningCount(SLIRecord sliRecord, ServiceLevelIndicator serviceLevelIndicator) {
    if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
      return Pair.of(sliRecord.getRunningGoodCount() - (sliRecord.getSliState() == SLIRecord.SLIState.GOOD ? 1 : 0),
          sliRecord.getRunningBadCount() - (sliRecord.getSliState() == SLIRecord.SLIState.BAD ? 1 : 0));
    } else {
      SLIRecord previousRecord =
          sliRecordService.getLastSLIRecord(serviceLevelIndicator.getUuid(), sliRecord.getTimestamp());
      if (Objects.isNull(previousRecord)) {
        return Pair.of(0l, 0l);
      }
      return Pair.of(previousRecord.getRunningGoodCount(), previousRecord.getRunningBadCount());
    }
  }

  private boolean isMinuteEnabled(List<EntityDisableTime> disableTimes, int currentDisabledRange, SLIRecord sliRecord) {
    boolean enabled = true;
    if (currentDisabledRange < disableTimes.size()) {
      enabled = !disableTimes.get(currentDisabledRange).contains(sliRecord.getTimestamp().toEpochMilli());
    }
    if (currentDisabledRange > 0) {
      enabled =
          enabled && !disableTimes.get(currentDisabledRange - 1).contains(sliRecord.getTimestamp().toEpochMilli());
    }
    return enabled;
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

  private List<CompositeSLORecord> getCompositeSLORecords(
      String sloId, Instant startTime, Instant endTime, TimeRangeParams filter, long numOfPoints) {
    CompositeSLORecord firstRecord = compositeSLORecordService.getFirstCompositeSLORecord(sloId, startTime);
    CompositeSLORecord lastRecord = compositeSLORecordService.getLastCompositeSLORecord(sloId, endTime);
    CompositeSLORecord firstRecordInRange = firstRecord;
    CompositeSLORecord lastRecordInRange = lastRecord;
    if (filter.getStartTime() != startTime) {
      firstRecordInRange = compositeSLORecordService.getFirstCompositeSLORecord(sloId, filter.getStartTime());
    }
    if (filter.getEndTime() != endTime) {
      lastRecordInRange = compositeSLORecordService.getLastCompositeSLORecord(sloId, filter.getEndTime());
    }
    if (firstRecordInRange == null || lastRecordInRange == null) {
      return Collections.emptyList();
    } else {
      startTime = firstRecordInRange.getTimestamp();
      endTime = lastRecordInRange.getTimestamp();
    }
    List<Instant> minutes = getMinutes(startTime, endTime, numOfPoints);
    minutes.add(firstRecord.getTimestamp());
    minutes.add(lastRecord.getTimestamp());
    return compositeSLORecordService.getSLORecordsOfMinutes(sloId, minutes);
  }

  private List<SLIRecord> getSLIRecords(
      String sliId, Instant startTime, Instant endTime, TimeRangeParams filter, long numOfPoints) {
    SLIRecord firstRecord = sliRecordService.getFirstSLIRecord(sliId, startTime);
    SLIRecord lastRecord = sliRecordService.getLastSLIRecord(sliId, endTime);
    SLIRecord firstRecordInRange = firstRecord;
    SLIRecord lastRecordInRange = lastRecord;
    if (filter.getStartTime() != startTime) {
      firstRecordInRange = sliRecordService.getFirstSLIRecord(sliId, filter.getStartTime());
    }
    if (filter.getEndTime() != endTime) {
      lastRecordInRange = sliRecordService.getLastSLIRecord(sliId, filter.getEndTime());
    }
    if (firstRecordInRange == null || lastRecordInRange == null) {
      return Collections.emptyList();
    } else {
      startTime = firstRecordInRange.getTimestamp();
      endTime = lastRecordInRange.getTimestamp();
    }
    List<Instant> minutes = getMinutes(startTime, endTime, numOfPoints);
    minutes.add(firstRecord.getTimestamp());
    minutes.add(lastRecord.getTimestamp()); // always include start and end minute.
    return sliRecordService.getSLIRecordsOfMinutes(sliId, minutes);
  }

  @VisibleForTesting
  List<Instant> getMinutes(Instant startTime, Instant endTime, long numOfPointsInBetween) {
    List<Instant> minutes = new ArrayList<>();
    long totalMinutes = Duration.between(startTime, endTime).toMinutes();
    long diff = totalMinutes;
    if (numOfPointsInBetween > 0) {
      diff = totalMinutes / numOfPointsInBetween;
    }
    if (diff == 0) {
      diff = 1L;
    }
    // long reminder = totalMinutes % maxNumberOfPoints;
    Duration diffDuration = Duration.ofMinutes(diff);
    for (Instant current = startTime; current.isBefore(endTime); current = current.plus(diffDuration)) {
      minutes.add(current);
    }
    minutes.add(endTime);
    return minutes;
  }
}
