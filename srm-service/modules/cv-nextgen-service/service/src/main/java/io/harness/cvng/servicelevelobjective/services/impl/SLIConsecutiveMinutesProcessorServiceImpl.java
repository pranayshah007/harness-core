/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIConsecutiveMinutesProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SLIConsecutiveMinutesProcessorServiceImpl implements SLIConsecutiveMinutesProcessorService {
  @Inject SLIRecordService sliRecordService;
  public List<SLIRecordParam> process(
      List<SLIRecordParam> sliRecordParams, ServiceLevelIndicator serviceLevelIndicator) {
    if (sliRecordParams.isEmpty() || serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.REQUEST) {
      return sliRecordParams;
    }
    Integer considerConsecutiveMinutes = serviceLevelIndicator.getConsiderConsecutiveMinutes();
    Boolean considerAllConsecutiveMinutesFromStartAsBad =
        serviceLevelIndicator.getConsiderAllConsecutiveMinutesFromStartAsBad();

    if (considerConsecutiveMinutes != null && considerAllConsecutiveMinutesFromStartAsBad != null
        && considerConsecutiveMinutes > 1) {
      return evaluate(sliRecordParams, serviceLevelIndicator, considerConsecutiveMinutes,
          considerAllConsecutiveMinutesFromStartAsBad);
    }
    return sliRecordParams;
  }

  private List<SLIRecordParam> evaluate(List<SLIRecordParam> sliRecordParams,
      ServiceLevelIndicator serviceLevelIndicator, Integer considerConsecutiveMinutes,
      Boolean considerAllConsecutiveMinutesFromStartAsBad) {
    sliRecordParams = sliRecordParams.stream()
                          .sorted(Comparator.comparing(SLIRecordParam::getTimeStamp))
                          .collect(Collectors.toList());
    Instant startTime = sliRecordParams.get(0).getTimeStamp();
    Instant evaluationTimeStartForConsecutiveErrorBudget =
        startTime.minus(considerConsecutiveMinutes, ChronoUnit.MINUTES);
    evaluationTimeStartForConsecutiveErrorBudget =
        DateTimeUtils.roundDownTo5MinBoundary(evaluationTimeStartForConsecutiveErrorBudget);
    List<SLIRecord> prevSliRecords = sliRecordService.getSLIRecordsWithSLIVersion(serviceLevelIndicator.getUuid(),
        evaluationTimeStartForConsecutiveErrorBudget.minus(1, ChronoUnit.MINUTES), startTime,
        serviceLevelIndicator.getVersion());
    Map<Instant, SLIRecordParam> instantSLIRecordMap = getInstantSLIRecordMap(prevSliRecords);

    for (SLIRecordParam sliRecordParam : sliRecordParams) {
      SLIState currentSLIState = sliRecordParam.getSliState();
      if (isSLIStateBad(currentSLIState, serviceLevelIndicator)) {
        boolean considerMinuteAsBad = true;
        for (int i = 1; i < considerConsecutiveMinutes; i++) {
          SLIRecordParam sliRecord =
              instantSLIRecordMap.get(sliRecordParam.getTimeStamp().minus(i, ChronoUnit.MINUTES));
          if (sliRecord == null || !isSLIStateBad(sliRecord.getSliState(), serviceLevelIndicator)) {
            considerMinuteAsBad = false;
          }
        }
        if (considerMinuteAsBad) {
          sliRecordParam.setGoodEventCount(0L);
          sliRecordParam.setBadEventCount(1L);
          instantSLIRecordMap = handlePreviousConsecutiveMinutes(sliRecordParam, instantSLIRecordMap,
              considerConsecutiveMinutes, considerAllConsecutiveMinutesFromStartAsBad);
        } else {
          sliRecordParam.setGoodEventCount(1L);
          sliRecordParam.setBadEventCount(0L);
        }
      }
      instantSLIRecordMap = handleFirstMinuteOfConsecutiveMinutesWhichIsFixedNow(
          sliRecordParam.getTimeStamp(), serviceLevelIndicator, instantSLIRecordMap, considerConsecutiveMinutes);
      instantSLIRecordMap.put(sliRecordParam.getTimeStamp(), sliRecordParam);
    }
    return instantSLIRecordMap.values()
        .stream()
        .sorted(Comparator.comparing(SLIRecordParam::getTimeStamp))
        .collect(Collectors.toList());
  }

  private Map<Instant, SLIRecordParam> handleFirstMinuteOfConsecutiveMinutesWhichIsFixedNow(Instant currentTime,
      ServiceLevelIndicator serviceLevelIndicator, Map<Instant, SLIRecordParam> instantSLIRecordMap,
      Integer considerConsecutiveMinutes) {
    SLIRecordParam firstSliRecordParam =
        instantSLIRecordMap.get(currentTime.minus(considerConsecutiveMinutes - 1, ChronoUnit.MINUTES));
    if (firstSliRecordParam != null && firstSliRecordParam.getGoodEventCount() == 1
        && !isSLIStateGood(firstSliRecordParam.getSliState(), serviceLevelIndicator)) {
      firstSliRecordParam.setSliState(SLIState.GOOD);
    }
    return instantSLIRecordMap;
  }

  private Map<Instant, SLIRecordParam> handlePreviousConsecutiveMinutes(SLIRecordParam currentSLIRecordParam,
      Map<Instant, SLIRecordParam> instantSLIRecordMap, Integer considerConsecutiveMinutes,
      Boolean considerAllConsecutiveMinutesFromStartAsBad) {
    if (Boolean.TRUE.equals(considerAllConsecutiveMinutesFromStartAsBad)) {
      for (int i = 1; i < considerConsecutiveMinutes; i++) {
        SLIRecordParam sliRecord =
            instantSLIRecordMap.get(currentSLIRecordParam.getTimeStamp().minus(i, ChronoUnit.MINUTES));
        if (sliRecord.getSliState() == SLIState.BAD) {
          sliRecord.setBadEventCount(1L);
        } else {
          sliRecord.setBadEventCount(0L);
        }
        sliRecord.setGoodEventCount(0L);
        instantSLIRecordMap.replace(currentSLIRecordParam.getTimeStamp().minus(i, ChronoUnit.MINUTES), sliRecord);
      }
    }
    return instantSLIRecordMap;
  }

  private boolean isSLIStateBad(SLIState sliState, ServiceLevelIndicator serviceLevelIndicator) {
    return sliState.equals(SLIState.BAD)
        || (sliState.equals(SLIState.NO_DATA)
            && serviceLevelIndicator.getSliMissingDataType().equals(SLIMissingDataType.BAD));
  }

  private boolean isSLIStateGood(SLIState sliState, ServiceLevelIndicator serviceLevelIndicator) {
    return sliState.equals(SLIState.GOOD) || sliState.equals(SLIState.SKIP_DATA)
        || (sliState.equals(SLIState.NO_DATA)
            && serviceLevelIndicator.getSliMissingDataType().equals(SLIMissingDataType.GOOD));
  }

  private Map<Instant, SLIRecordParam> getInstantSLIRecordMap(List<SLIRecord> sliRecords) {
    Map<Instant, SLIRecordParam> instantSLIRecordMap = new HashMap<>();
    if (sliRecords.isEmpty()) {
      return instantSLIRecordMap;
    }
    long baseGoodCount = sliRecords.get(0).getRunningGoodCount();
    long baseBadCount = sliRecords.get(0).getRunningBadCount();
    int sliRecordSize = sliRecords.size();
    for (int idx = 1; idx < sliRecordSize; idx++) {
      SLIRecord currentSLIRecord = sliRecords.get(idx);
      long currentGoodCount = currentSLIRecord.getRunningGoodCount() - baseGoodCount;
      long currentBadCount = currentSLIRecord.getRunningBadCount() - baseBadCount;
      instantSLIRecordMap.put(currentSLIRecord.getTimestamp(),
          SLIRecordParam.builder()
              .timeStamp(currentSLIRecord.getTimestamp())
              .sliState(currentSLIRecord.getSliState())
              .badEventCount(currentBadCount)
              .goodEventCount(currentGoodCount)
              .build());
      baseGoodCount = currentSLIRecord.getRunningGoodCount();
      baseBadCount = currentSLIRecord.getRunningBadCount();
    }
    return instantSLIRecordMap;
  }
}
