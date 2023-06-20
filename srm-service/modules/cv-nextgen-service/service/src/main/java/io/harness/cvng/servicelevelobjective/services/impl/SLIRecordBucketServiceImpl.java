/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.SRMPersistence;
import io.harness.annotations.retry.RetryOnException;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket.SLIRecordBucketKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIRecordBucketServiceImpl implements SLIRecordBucketService {
  private static final int RETRY_COUNT = 3;

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
}
