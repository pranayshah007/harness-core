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
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLIValue;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Sort;

public class SLIRecordServiceImpl implements SLIRecordService {
  @VisibleForTesting static int MAX_NUMBER_OF_POINTS = 2000;
  private static final int RETRY_COUNT = 3;
  @Inject private HPersistence hPersistence;
  @Inject Clock clock;

  @Inject MonitoredServiceService monitoredServiceService;

  @Inject EntityDisabledTimeService entityDisabledTimeService;

  @Override
  public void create(List<SLIRecordParam> sliRecordParamList, String sliId, String verificationTaskId, int sliVersion) {
    if (isEmpty(sliRecordParamList)) {
      return;
    }
    SLIRecordParam firstSLIRecordParam = sliRecordParamList.get(0);
    SLIRecordParam lastSLIRecordParam = sliRecordParamList.get(sliRecordParamList.size() - 1);
    long runningGoodCount = 0L;
    long runningBadCount = 0L;
    List<SLIRecord> sliRecordList = new ArrayList<>();
    SLIRecord lastSLIRecord = getLastSLIRecord(sliId, firstSLIRecordParam.getTimeStamp());
    SLIRecord latestSLIRecord = getLatestSLIRecord(sliId);
    if (Objects.nonNull(lastSLIRecord)) {
      runningGoodCount = lastSLIRecord.getRunningGoodCount();
      runningBadCount = lastSLIRecord.getRunningBadCount();
    }
    if (Objects.nonNull(latestSLIRecord)
        && latestSLIRecord.getTimestamp().isAfter(firstSLIRecordParam.getTimeStamp())) {
      // Update flow: fetch SLI Records to be updated
      updateSLIRecords(sliRecordParamList, sliId, sliVersion, firstSLIRecordParam, lastSLIRecordParam, runningGoodCount,
          runningBadCount, verificationTaskId);
    } else {
      createSLIRecords(
          sliRecordParamList, sliId, verificationTaskId, sliVersion, runningGoodCount, runningBadCount, sliRecordList);
    }
  }

  private void createSLIRecords(List<SLIRecordParam> sliRecordParamList, String sliId, String verificationTaskId,
      int sliVersion, long runningGoodCount, long runningBadCount, List<SLIRecord> sliRecordList) {
    for (SLIRecordParam sliRecordParam : sliRecordParamList) {
      if (SLIState.GOOD.equals(sliRecordParam.getSliState())) {
        runningGoodCount++;
      } else if (SLIState.BAD.equals(sliRecordParam.getSliState())) {
        runningBadCount++;
      }
      SLIRecord sliRecord = SLIRecord.builder()
                                .runningBadCount(runningBadCount)
                                .runningGoodCount(runningGoodCount)
                                .sliId(sliId)
                                .sliVersion(sliVersion)
                                .verificationTaskId(verificationTaskId)
                                .timestamp(sliRecordParam.getTimeStamp())
                                .sliState(sliRecordParam.getSliState())
                                .build();
      sliRecordList.add(sliRecord);
    }
    hPersistence.save(sliRecordList);
  }

  @RetryOnException(retryCount = RETRY_COUNT, retryOn = ConcurrentModificationException.class)
  public void updateSLIRecords(List<SLIRecordParam> sliRecordParamList, String sliId, int sliVersion,
      SLIRecordParam firstSLIRecordParam, SLIRecordParam lastSLIRecordParam, long runningGoodCount,
      long runningBadCount, String verificationTaskId) {
    List<SLIRecord> toBeUpdatedSLIRecords = getSLIRecords(
        sliId, firstSLIRecordParam.getTimeStamp(), lastSLIRecordParam.getTimeStamp().plus(1, ChronoUnit.MINUTES));
    Map<Instant, SLIRecord> sliRecordMap =
        toBeUpdatedSLIRecords.stream().collect(Collectors.toMap(SLIRecord::getTimestamp, Function.identity()));
    List<SLIRecord> updateOrCreateSLIRecords = new ArrayList<>();
    for (SLIRecordParam sliRecordParam : sliRecordParamList) {
      SLIRecord sliRecord = sliRecordMap.get(sliRecordParam.getTimeStamp());
      if (SLIState.GOOD.equals(sliRecordParam.getSliState())) {
        runningGoodCount++;
      } else if (SLIState.BAD.equals(sliRecordParam.getSliState())) {
        runningBadCount++;
      }
      if (Objects.nonNull(sliRecord)) {
        sliRecord.setRunningGoodCount(runningGoodCount);
        sliRecord.setRunningBadCount(runningBadCount);
        sliRecord.setSliState(sliRecordParam.getSliState());
        sliRecord.setSliVersion(sliVersion);
        updateOrCreateSLIRecords.add(sliRecord);
      } else {
        SLIRecord newSLIRecord = SLIRecord.builder()
                                     .runningBadCount(runningBadCount)
                                     .runningGoodCount(runningGoodCount)
                                     .sliId(sliId)
                                     .sliVersion(sliVersion)
                                     .verificationTaskId(verificationTaskId)
                                     .timestamp(sliRecordParam.getTimeStamp())
                                     .sliState(sliRecordParam.getSliState())
                                     .build();
        updateOrCreateSLIRecords.add(newSLIRecord);
      }
    }
    hPersistence.save(updateOrCreateSLIRecords);
  }

  @Override
  public SLOGraphData getGraphData(ServiceLevelIndicator serviceLevelIndicator, Instant startTime, Instant endTime,
      int totalErrorBudgetMinutes, SLIMissingDataType sliMissingDataType, int sliVersion) {
    return getGraphData(
        serviceLevelIndicator, startTime, endTime, totalErrorBudgetMinutes, sliMissingDataType, sliVersion, null);
  }

  @Override
  public SLOGraphData getGraphData(ServiceLevelIndicator serviceLevelIndicator, Instant startTime, Instant endTime,
      int totalErrorBudgetMinutes, SLIMissingDataType sliMissingDataType, int sliVersion, TimeRangeParams filter) {
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

    List<Point> sliTread = new ArrayList<>();
    List<Point> errorBudgetBurndown = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    int errorBudgetRemaining = totalErrorBudgetMinutes;
    boolean isReCalculatingSLI = false;
    boolean isCalculatingSLI = false;
    if (!sliRecords.isEmpty()) {
      SLIValue sliValue = null;
      long beginningMinute = sliRecords.get(0).getEpochMinute();
      SLIRecord firstRecord = sliRecords.get(0);
      long prevRecordGoodCount =
          firstRecord.getRunningGoodCount() - (firstRecord.getSliState() == SLIState.GOOD ? 1 : 0);
      long prevRecordBadCount = firstRecord.getRunningBadCount() - (firstRecord.getSliState() == SLIState.BAD ? 1 : 0);
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
        }
        sliValue = sliMissingDataType.calculateSLIValue(
            goodCountFromStart, badCountFromStart, minutesFromStart, disabledMinutesFromStart);
        sliTread.add(Point.builder()
                         .timestamp(sliRecord.getTimestamp().toEpochMilli())
                         .value(sliValue.sliPercentage())
                         .enabled(enabled)
                         .build());
        errorBudgetBurndown.add(
            Point.builder()
                .timestamp(sliRecord.getTimestamp().toEpochMilli())
                .value(((totalErrorBudgetMinutes - sliValue.getBadCount()) * 100.0) / totalErrorBudgetMinutes)
                .enabled(enabled)
                .build());
        currentSLIRecord++;
      }
      errorBudgetRemainingPercentage = errorBudgetBurndown.get(errorBudgetBurndown.size() - 1).getValue();
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

    return SLOGraphData.builder()
        .errorBudgetBurndown(errorBudgetBurndown)
        .errorBudgetRemaining(errorBudgetRemaining)
        .sloPerformanceTrend(sliTread)
        .isRecalculatingSLI(isReCalculatingSLI)
        .isCalculatingSLI(isCalculatingSLI)
        .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
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

  @Override
  public List<SLIRecord> getLatestCountSLIRecords(String sliId, int count) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .asList(new FindOptions().limit(count));
  }

  @Override
  public List<SLIRecord> getSLIRecordsForLookBackDuration(String sliId, long lookBackDuration) {
    Instant startTime = clock.instant().minusMillis(lookBackDuration);
    Instant endTime = clock.instant().minusMillis(Duration.ofMinutes(1).toMillis());
    List<Instant> minutes = new ArrayList<>();
    minutes.add(startTime);
    minutes.add(endTime);
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .in(minutes)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .asList();
  }

  @Override
  public double getErrorBudgetBurnRate(String sliId, long lookBackDuration, int totalErrorBudgetMinutes) {
    List<SLIRecord> sliRecords = getSLIRecordsForLookBackDuration(sliId, lookBackDuration);
    return sliRecords.size() < 2
        ? 0
        : (((double) (sliRecords.get(1).getRunningBadCount() - sliRecords.get(0).getRunningBadCount()) * 100)
            / totalErrorBudgetMinutes);
  }

  private List<SLIRecord> sliRecords(String sliId, Instant startTime, Instant endTime, TimeRangeParams filter) {
    SLIRecord firstRecord = getFirstSLIRecord(sliId, startTime);
    SLIRecord lastRecord = getLastSLIRecord(sliId, endTime);
    SLIRecord firstRecordInRange = getFirstSLIRecord(sliId, filter.getStartTime());
    SLIRecord lastRecordInRange = getLastSLIRecord(sliId, filter.getEndTime());
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

  @VisibleForTesting
  List<SLIRecord> getSLIRecords(String sliId, Instant startTimeStamp, Instant endTimeStamp) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .greaterThanOrEq(startTimeStamp)
        .field(SLIRecordKeys.timestamp)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .asList();
  }

  @Override
  public void delete(List<String> sliIds) {
    hPersistence.delete(hPersistence.createQuery(SLIRecord.class).field(SLIRecordKeys.sliId).in(sliIds));
  }

  private SLIRecord getLastSLIRecord(String sliId, Instant startTimeStamp) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .lessThan(startTimeStamp)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }
  private SLIRecord getFirstSLIRecord(String sliId, Instant timestampInclusive) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .greaterThanOrEq(timestampInclusive)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .get();
  }

  private SLIRecord getLatestSLIRecord(String sliId) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }
}
