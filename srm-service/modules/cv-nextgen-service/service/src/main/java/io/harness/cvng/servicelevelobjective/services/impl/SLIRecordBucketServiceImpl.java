/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.core.services.CVNextGenConstants.SLI_RECORD_BUCKET_SIZE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.SRMPersistence;
import io.harness.annotations.retry.RetryOnException;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLIValue;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket.SLIRecordBucketKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.utils.SLOGraphUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class SLIRecordBucketServiceImpl implements SLIRecordBucketService {
  private static final int RETRY_COUNT = 3;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SRMPersistence hPersistence;
  @Override
  public void create(List<SLIRecordParam> sliRecordParamList, String sliId, int sliVersion) {
    if (isEmpty(sliRecordParamList)) {
      return;
    }
    Preconditions.checkArgument(sliRecordParamList.size() % 5 == 0,
        String.format("[SLI Record Bucketing Error] SLIRecord Param size is not multiple of 5 %s", sliRecordParamList));
    SLIRecordParam firstSLIRecordParam = sliRecordParamList.get(0);
    Preconditions.checkArgument(
        TimeUnit.SECONDS.toMinutes(firstSLIRecordParam.getTimeStamp().getEpochSecond()) % 5 == 0,
        String.format(
            "[SLI Record Bucketing Error] SLIRecord first Param is not multiple of 5 %s", firstSLIRecordParam));
    SLIRecordParam lastSLIRecordParam = sliRecordParamList.get(sliRecordParamList.size() - 1);
    long runningGoodCount = 0L;
    long runningBadCount = 0L;
    SLIRecordBucket lastSLIRecord = getLastSLIRecord(sliId, firstSLIRecordParam.getTimeStamp());
    SLIRecordBucket latestSLIRecord = getLatestSLIRecord(sliId);
    if (Objects.nonNull(lastSLIRecord)) {
      runningGoodCount = lastSLIRecord.getRunningGoodCount();
      runningBadCount = lastSLIRecord.getRunningBadCount();
    }
    if (Objects.nonNull(latestSLIRecord)
        && !latestSLIRecord.getBucketStartTime().isBefore(firstSLIRecordParam.getTimeStamp())) {
      // Update flow: fetch SLI Records to be updated
      updateSLIRecords(sliRecordParamList, sliId, sliVersion, firstSLIRecordParam, lastSLIRecordParam, runningGoodCount,
          runningBadCount);
    } else {
      createSLIRecords(sliRecordParamList, sliId, sliVersion, runningGoodCount, runningBadCount);
    }
  }

  private void createSLIRecords(List<SLIRecordParam> sliRecordParamList, String sliId, int sliVersion,
      long runningGoodCount, long runningBadCount) {
    // Need to remove this check later, adding that now just to get the idea if we have any such cases, which we
    // shouldn't have
    List<SLIRecordBucket> sliRecordBuckets = new ArrayList<>();
    for (int idx = 0; idx < sliRecordParamList.size(); idx += 5) {
      List<SLIState> sliStates = new ArrayList<>();
      for (int bucketIdx = idx; bucketIdx < idx + 5; bucketIdx++) {
        runningBadCount += sliRecordParamList.get(bucketIdx).getBadEventCount();
        runningGoodCount += sliRecordParamList.get(bucketIdx).getGoodEventCount();
        sliStates.add(sliRecordParamList.get(bucketIdx).getSliState());
      }
      sliRecordBuckets.add(SLIRecordBucket.builder()
                               .sliId(sliId)
                               .bucketStartTime(sliRecordParamList.get(idx).getTimeStamp())
                               .sliVersion(sliVersion)
                               .runningBadCount(runningBadCount)
                               .runningGoodCount(runningGoodCount)
                               .sliStates(sliStates)
                               .build());
    }
    hPersistence.saveBatch(sliRecordBuckets);
  }

  @RetryOnException(retryCount = RETRY_COUNT, retryOn = ConcurrentModificationException.class)
  public void updateSLIRecords(List<SLIRecordParam> sliRecordParamList, String sliId, int sliVersion,
      SLIRecordParam firstSLIRecordParam, SLIRecordParam lastSLIRecordParam, long runningGoodCount,
      long runningBadCount) {
    List<SLIRecordBucket> toBeUpdatedSLIRecords = getSLIRecords(
        sliId, firstSLIRecordParam.getTimeStamp(), lastSLIRecordParam.getTimeStamp().plus(1, ChronoUnit.MINUTES));
    Map<Instant, SLIRecordBucket> sliRecordMap = toBeUpdatedSLIRecords.stream().collect(Collectors.toMap(
        SLIRecordBucket::getBucketStartTime, Function.identity(),
        (sliRecord1,
            sliRecord2) -> sliRecord1.getLastUpdatedAt() > sliRecord2.getLastUpdatedAt() ? sliRecord1 : sliRecord2));
    List<SLIRecordBucket> updateOrCreateSLIRecords = new ArrayList<>();
    for (int idx = 0; idx < sliRecordParamList.size(); idx += 5) {
      List<SLIState> sliStates = new ArrayList<>();
      SLIRecordBucket sliRecord = sliRecordMap.get(sliRecordParamList.get(idx).getTimeStamp());
      for (int bucketIdx = idx; bucketIdx < idx + 5; bucketIdx++) {
        runningBadCount += sliRecordParamList.get(bucketIdx).getBadEventCount();
        runningGoodCount += sliRecordParamList.get(bucketIdx).getGoodEventCount();
        sliStates.add(sliRecordParamList.get(bucketIdx).getSliState());
      }
      if (Objects.nonNull(sliRecord)) {
        sliRecord.setRunningGoodCount(runningGoodCount);
        sliRecord.setRunningBadCount(runningBadCount);
        sliRecord.setSliStates(sliStates);
        sliRecord.setSliVersion(sliVersion);
        updateOrCreateSLIRecords.add(sliRecord);
      } else {
        updateOrCreateSLIRecords.add(SLIRecordBucket.builder()
                                         .sliId(sliId)
                                         .bucketStartTime(sliRecordParamList.get(idx).getTimeStamp())
                                         .sliVersion(sliVersion)
                                         .runningBadCount(runningBadCount)
                                         .runningGoodCount(runningGoodCount)
                                         .sliStates(sliStates)
                                         .build());
      }
    }
    try {
      hPersistence.upsertBatch(SLIRecordBucket.class, updateOrCreateSLIRecords, new ArrayList<>());
    } catch (IllegalAccessException exception) {
      log.error("[SLI Record Bucketing Error] SLI Records update failed through Bulk update {}",
          exception.getLocalizedMessage());
      hPersistence.save(updateOrCreateSLIRecords);
    }
  }

  @Override
  public SLIRecordBucket getLastSLIRecord(String sliId, Instant startTimeStamp) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .lessThan(startTimeStamp)
        .order(Sort.descending(SLIRecordBucketKeys.bucketStartTime))
        .get();
  }

  @Override
  public SLIRecordBucket getLatestSLIRecord(String sliId) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordBucketKeys.bucketStartTime))
        .get();
  }

  @Override
  public List<SLIRecordBucket> getSLIRecords(String sliId, Instant startTimeStamp, Instant endTimeStamp) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .greaterThanOrEq(startTimeStamp)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(SLIRecordBucketKeys.bucketStartTime))
        .asList();
  }

  @Override
  public SLIRecordBucket getFirstSLIRecord(String sliId, Instant timestampInclusive) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .greaterThanOrEq(timestampInclusive)
        .order(Sort.ascending(SLIRecordBucketKeys.bucketStartTime))
        .get();
  }
  @Override
  public List<SLIRecordBucket> getSLIRecordsOfMinutes(String sliId, List<Instant> minutes) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .in(minutes)
        .order(Sort.ascending(SLIRecordBucketKeys.bucketStartTime))
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }

  @Override
  public List<SLIRecordBucket> getSLIRecordsWithSLIVersion(
      String sliId, Instant startTimeStamp, Instant endTimeStamp, int sliVersion) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .greaterThanOrEq(startTimeStamp)
        .field(SLIRecordBucketKeys.sliVersion)
        .equal(sliVersion)
        .field(SLIRecordBucketKeys.bucketStartTime)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(SLIRecordBucketKeys.bucketStartTime))
        .asList();
  }

  @Override
  public List<SLIRecordBucket> getSLIRecordBucketsForFilterRange(
      String sliId, Instant startTime, Instant endTime, TimeRangeParams filter, long numOfPoints) {
    SLIRecordBucket firstRecord = getFirstSLIRecord(sliId, startTime);
    SLIRecordBucket lastRecord = getLastSLIRecord(sliId, endTime);
    SLIRecordBucket firstRecordInRange = firstRecord;
    SLIRecordBucket lastRecordInRange = lastRecord;
    if (filter.getStartTime() != startTime) {
      firstRecordInRange = getFirstSLIRecord(sliId, filter.getStartTime());
    }
    if (filter.getEndTime() != endTime) {
      lastRecordInRange = getLastSLIRecord(sliId, filter.getEndTime());
    }
    if (firstRecordInRange == null || lastRecordInRange == null) {
      return Collections.emptyList();
    } else {
      endTime = lastRecordInRange.getBucketStartTime();
    }
    List<Instant> minutes = SLOGraphUtils.getBucketMinutesExclusiveOfStartAndEndTime(
        firstRecordInRange.getBucketStartTime(), endTime, numOfPoints, SLI_RECORD_BUCKET_SIZE);
    List<SLIRecordBucket> sliRecordBuckets = new ArrayList<>();
    sliRecordBuckets.add(firstRecord);
    if (!firstRecordInRange.getBucketStartTime().equals(firstRecord.getBucketStartTime())) {
      sliRecordBuckets.add(firstRecordInRange);
    }
    if (!minutes.isEmpty()) {
      sliRecordBuckets.addAll(getSLIRecordsOfMinutes(sliId, minutes));
    }
    if (!lastRecordInRange.getBucketStartTime().equals(
            lastRecord.getBucketStartTime())) { // handle edge cases of adding the last record
      sliRecordBuckets.add(lastRecordInRange);
    }
    sliRecordBuckets.add(lastRecord); // last record from single query
    // Map with Timestamp as key and SLIRecord as value with merge function to take the latest -> all this just to sort
    // and I guess remove duplicates too, may be use a sorted Set TODO
    return sliRecordBuckets.stream()
        .collect(Collectors.toMap(SLIRecordBucket::getBucketStartTime, Function.identity(),
            (sliRecordBucket1, sliRecordBucket2)
                -> sliRecordBucket1.getLastUpdatedAt() > sliRecordBucket2.getLastUpdatedAt() ? sliRecordBucket1
                                                                                             : sliRecordBucket2))
        .values()
        .stream()
        .sorted(Comparator.comparing(SLIRecordBucket::getBucketStartTime))
        .collect(Collectors.toList());
  }

  @Override
  public long getBadCountTillRangeStartTime(ServiceLevelIndicator serviceLevelIndicator,
      SLIMissingDataType sliMissingDataType, SLIValue sliValue, SLIRecordBucket sliRecordBucket,
      long previousRunningCount) {
    long badCountTillRangeStartTime;
    badCountTillRangeStartTime = sliValue.getBadCount();
    for (SLIState sliState : sliRecordBucket.getSliStates()) {
      if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
        if (sliState.equals(SLIState.BAD)
            || (sliState.equals(SLIState.NO_DATA) && sliMissingDataType == SLIMissingDataType.BAD)) {
          badCountTillRangeStartTime--;
        }
      } else {
        badCountTillRangeStartTime -= sliRecordBucket.getRunningBadCount() - previousRunningCount;
      }
    }
    return badCountTillRangeStartTime;
  }

  @Override
  public SLIValue calculateSLIValue(SLIEvaluationType sliEvaluationType, SLIMissingDataType sliMissingDataType,
      SLIRecordBucket sliRecordBucket, Pair<Long, Long> baselineRunningCountPair, long beginningMinute,
      long skipRecordCount, long disabledMinutesFromStart) {
    long lastRecordBeforeStartGoodCount = baselineRunningCountPair.getLeft();
    long lastRecordBeforeStartBadCount = baselineRunningCountPair.getRight();
    long goodCountFromStart = sliRecordBucket.getRunningGoodCount() - lastRecordBeforeStartGoodCount;
    long badCountFromStart = sliRecordBucket.getRunningBadCount() - lastRecordBeforeStartBadCount;
    SLIValue sliValue;
    if (sliEvaluationType == SLIEvaluationType.WINDOW) {
      long bucketEndTime = (sliRecordBucket.getBucketStartTime().getEpochSecond() / 60) + SLI_RECORD_BUCKET_SIZE;
      long minutesFromStart = bucketEndTime - beginningMinute; // TODO we removed a + 1 think about it.
      sliValue = sliMissingDataType.calculateSLIValue(
          goodCountFromStart + skipRecordCount, badCountFromStart, minutesFromStart, disabledMinutesFromStart);
    } else {
      sliValue = SLIValue.builder()
                     .goodCount(goodCountFromStart)
                     .badCount(badCountFromStart)
                     .total(goodCountFromStart + badCountFromStart)
                     .build();
    }
    return sliValue;
  }

  @Override
  public Pair<Long, Long> getPreviousBucketRunningCount(
      SLIRecordBucket sliRecordBucket, ServiceLevelIndicator serviceLevelIndicator) {
    if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
      long runningBadCount = sliRecordBucket.getRunningBadCount();
      long runningGoodCount = sliRecordBucket.getRunningGoodCount();
      for (SLIState sliState : sliRecordBucket.getSliStates()) {
        if (sliState == SLIState.GOOD) {
          runningGoodCount--;
        } else if (sliState == SLIState.BAD) {
          runningBadCount--;
        }
      }
      return Pair.of(runningGoodCount, runningBadCount);
    } else {
      SLIRecordBucket baselineSLIRecordBucket =
          getLastSLIRecord(serviceLevelIndicator.getUuid(), sliRecordBucket.getBucketStartTime());
      if (Objects.isNull(baselineSLIRecordBucket)) {
        return Pair.of(0L, 0L);
      }
      return Pair.of(baselineSLIRecordBucket.getRunningGoodCount(), baselineSLIRecordBucket.getRunningBadCount());
    }
  }

  @Override
  public Pair<Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>>
  getSLODetailsSLIRecordsAndSLIMissingDataType(
      List<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList,
      Instant startTime, Instant endTime) {
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
        serviceLevelObjectivesDetailSLIRecordMap = new HashMap<>();
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
        objectivesDetailSLIMissingDataTypeMap = new HashMap<>();
    for (CompositeServiceLevelObjective.ServiceLevelObjectivesDetail objectivesDetail :
        serviceLevelObjectivesDetailList) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .projectIdentifier(objectivesDetail.getProjectIdentifier())
                                        .orgIdentifier(objectivesDetail.getOrgIdentifier())
                                        .accountIdentifier(objectivesDetail.getAccountId())
                                        .build();
      SimpleServiceLevelObjective simpleServiceLevelObjective =
          (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
              projectParams, objectivesDetail.getServiceLevelObjectiveRef());
      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          ProjectParams.builder()
              .accountIdentifier(simpleServiceLevelObjective.getAccountId())
              .orgIdentifier(simpleServiceLevelObjective.getOrgIdentifier())
              .projectIdentifier(simpleServiceLevelObjective.getProjectIdentifier())
              .build(),
          simpleServiceLevelObjective.getServiceLevelIndicators().get(0));
      String sliId = serviceLevelIndicator.getUuid();
      int sliVersion = serviceLevelIndicator.getVersion();
      if (serviceLevelIndicator.getSLIEvaluationType().equals(SLIEvaluationType.WINDOW)
          && serviceLevelIndicator.getConsiderConsecutiveMinutes() != null
          && serviceLevelIndicator.getConsiderConsecutiveMinutes() > 1) {
        SLIRecordBucket lastSLIRecordBucket = getLatestSLIRecordSLIVersion(sliId, sliVersion);
        if (lastSLIRecordBucket != null) {
          Instant timeOfLastRecordWhichIsFixed =
              DateTimeUtils.roundDownTo5MinBoundary(lastSLIRecordBucket.getBucketStartTime().minus(
                  serviceLevelIndicator.getConsiderConsecutiveMinutes() - 2, ChronoUnit.MINUTES));
          endTime = timeOfLastRecordWhichIsFixed.isBefore(endTime) ? timeOfLastRecordWhichIsFixed : endTime;
        }
      }
      List<SLIRecordBucket> sliRecordBuckets = getSLIRecordsWithSLIVersion(sliId, startTime, endTime, sliVersion);
      if (!sliRecordBuckets.isEmpty()) {
        serviceLevelObjectivesDetailSLIRecordMap.put(objectivesDetail, sliRecordBuckets);
        objectivesDetailSLIMissingDataTypeMap.put(objectivesDetail, serviceLevelIndicator.getSliMissingDataType());
      }
    }
    return Pair.of(serviceLevelObjectivesDetailSLIRecordMap, objectivesDetailSLIMissingDataTypeMap);
  }

  private SLIRecordBucket getLatestSLIRecordSLIVersion(String sliId, int sliVersion) {
    return hPersistence.createQuery(SLIRecordBucket.class, excludeAuthorityCount)
        .filter(SLIRecordBucketKeys.sliId, sliId)
        .field(SLIRecordBucketKeys.sliVersion)
        .equal(sliVersion)
        .order(Sort.descending(SLIRecordBucketKeys.bucketStartTime))
        .get();
  }
}
