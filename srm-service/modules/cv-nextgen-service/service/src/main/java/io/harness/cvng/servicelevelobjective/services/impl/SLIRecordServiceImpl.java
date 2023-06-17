/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.SRMPersistence;
import io.harness.annotations.retry.RetryOnException;
import io.harness.beans.FeatureName;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

@Slf4j
public class SLIRecordServiceImpl implements SLIRecordService {
  private static final int RETRY_COUNT = 3;

  @Inject private SRMPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject private SLIRecordBucketService sliRecordBucketService;

  @Inject private FeatureFlagService featureFlagService;

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

    try {
      sliRecordBucketService.create(sliRecordParamList, sliId, sliVersion);
    } catch (Exception exception) {
      log.error(String.format("[SLI Record Bucketing Error] sliId: %s ", sliId), exception);
    }
  }

  private void createSLIRecords(List<SLIRecordParam> sliRecordParamList, String sliId, String verificationTaskId,
      int sliVersion, long runningGoodCount, long runningBadCount, List<SLIRecord> sliRecordList) {
    for (SLIRecordParam sliRecordParam : sliRecordParamList) {
      runningBadCount += sliRecordParam.getBadEventCount();
      runningGoodCount += sliRecordParam.getGoodEventCount();
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
    hPersistence.saveBatch(sliRecordList);
  }

  @RetryOnException(retryCount = RETRY_COUNT, retryOn = ConcurrentModificationException.class)
  public void updateSLIRecords(List<SLIRecordParam> sliRecordParamList, String sliId, int sliVersion,
      SLIRecordParam firstSLIRecordParam, SLIRecordParam lastSLIRecordParam, long runningGoodCount,
      long runningBadCount, String verificationTaskId) {
    List<SLIRecord> toBeUpdatedSLIRecords = getSLIRecords(
        sliId, firstSLIRecordParam.getTimeStamp(), lastSLIRecordParam.getTimeStamp().plus(1, ChronoUnit.MINUTES));
    Map<Instant, SLIRecord> sliRecordMap = toBeUpdatedSLIRecords.stream().collect(
        Collectors.toMap(SLIRecord::getTimestamp, Function.identity(), (sliRecord1, sliRecord2) -> {
          log.info("Duplicate SLI Key detected sliId: {}, timeStamp: {}", sliId, sliRecord1.getTimestamp());
          return sliRecord1.getLastUpdatedAt() > sliRecord2.getLastUpdatedAt() ? sliRecord1 : sliRecord2;
        }));

    List<SLIRecord> updateOrCreateSLIRecords = new ArrayList<>();
    for (SLIRecordParam sliRecordParam : sliRecordParamList) {
      SLIRecord sliRecord = sliRecordMap.get(sliRecordParam.getTimeStamp());
      runningBadCount += sliRecordParam.getBadEventCount();
      runningGoodCount += sliRecordParam.getGoodEventCount();
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
    try {
      hPersistence.upsertBatch(SLIRecord.class, updateOrCreateSLIRecords, new ArrayList<>());
    } catch (IllegalAccessException exception) {
      log.error("SLI Records update failed through Bulk update {}", exception.getLocalizedMessage());
      hPersistence.save(updateOrCreateSLIRecords);
    }
  }

  @Override
  public List<SLIRecord> getLatestCountSLIRecords(String sliId, int count) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .asList(new FindOptions().limit(count));
  }

  @Override
  public List<SLIRecord> getSLIRecords(String sliId, Instant startTimeStamp, Instant endTimeStamp) {
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
  public List<SLIRecord> getSLIRecordsWithSLIVersion(
      String sliId, Instant startTimeStamp, Instant endTimeStamp, int sliVersion) {
    List<SLIRecord> sliRecords = hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
                                     .filter(SLIRecordKeys.sliId, sliId)
                                     .field(SLIRecordKeys.timestamp)
                                     .greaterThanOrEq(startTimeStamp)
                                     .field(SLIRecordKeys.sliVersion)
                                     .equal(sliVersion)
                                     .field(SLIRecordKeys.timestamp)
                                     .lessThan(endTimeStamp)
                                     .order(Sort.ascending(SLIRecordKeys.timestamp))
                                     .asList();
    if (featureFlagService.isGlobalFlagEnabled(FeatureName.SRM_ENABLE_SLI_BUCKET.toString())) {
      List<SLIRecordBucket> sliRecordBuckets =
          sliRecordBucketService.getSLIRecordsWithSLIVersion(sliId, startTimeStamp, endTimeStamp, sliVersion);
      validateListOfSLIRecordsWithBuckets(sliId, sliRecords, sliRecordBuckets);
    }
    return sliRecords;
  }

  @Override
  public List<SLIRecord> getSLIRecordsOfMinutes(String sliId, List<Instant> minutes) {
    List<SLIRecord> sliRecords = hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
                                     .filter(SLIRecordKeys.sliId, sliId)
                                     .field(SLIRecordKeys.timestamp)
                                     .in(minutes)
                                     .order(Sort.ascending(SLIRecordKeys.timestamp))
                                     .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
    if (featureFlagService.isGlobalFlagEnabled(FeatureName.SRM_ENABLE_SLI_BUCKET.toString())) {
      List<SLIRecordBucket> sliRecordBuckets = sliRecordBucketService.getSLIRecordsOfMinutes(sliId, minutes);
      validateListOfSLIRecordsWithBuckets(sliId, sliRecords, sliRecordBuckets);
    }
    return sliRecords;
  }
  @Override
  public Pair<Map<ServiceLevelObjectivesDetail, List<SLIRecord>>, Map<ServiceLevelObjectivesDetail, SLIMissingDataType>>
  getSLODetailsSLIRecordsAndSLIMissingDataType(
      List<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList,
      Instant startTime, Instant endTime) {
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecord>>
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
        SLIRecord lastSLIRecord = getLatestSLIRecordSLIVersion(sliId, sliVersion);
        if (lastSLIRecord != null) {
          Instant timeOfLastRecordWhichIsFixed = lastSLIRecord.getTimestamp().minus(
              serviceLevelIndicator.getConsiderConsecutiveMinutes() - 2, ChronoUnit.MINUTES);
          endTime = timeOfLastRecordWhichIsFixed.isBefore(endTime) ? timeOfLastRecordWhichIsFixed : endTime;
        }
      }
      List<SLIRecord> sliRecords = getSLIRecordsWithSLIVersion(sliId, startTime, endTime, sliVersion);
      if (!sliRecords.isEmpty()) {
        serviceLevelObjectivesDetailSLIRecordMap.put(objectivesDetail, sliRecords);
        objectivesDetailSLIMissingDataTypeMap.put(objectivesDetail, serviceLevelIndicator.getSliMissingDataType());
      }
    }
    return Pair.create(serviceLevelObjectivesDetailSLIRecordMap, objectivesDetailSLIMissingDataTypeMap);
  }

  private SLIRecord getLatestSLIRecordSLIVersion(String sliId, int sliVersion) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.sliVersion)
        .equal(sliVersion)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }

  @Override
  public SLIRecord getLastSLIRecord(String sliId, Instant startTimeStamp) {
    SLIRecord lastSLIRecord = hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
                                  .filter(SLIRecordKeys.sliId, sliId)
                                  .field(SLIRecordKeys.timestamp)
                                  .lessThan(startTimeStamp)
                                  .order(Sort.descending(SLIRecordKeys.timestamp))
                                  .get();
    if (featureFlagService.isGlobalFlagEnabled(FeatureName.SRM_ENABLE_SLI_BUCKET.toString())) {
      SLIRecordBucket lastSLIRecordBucket = sliRecordBucketService.getLastSLIRecord(sliId, startTimeStamp);
      if (lastSLIRecord != null && lastSLIRecordBucket != null
          && !DateTimeUtils.roundDownTo5MinBoundary(lastSLIRecord.getTimestamp())
                  .equals(lastSLIRecordBucket.getBucketStartTime())) {
        log.error(String.format(
            "[SLI Record Bucketing Error] Last SLI Record timestamp before %s doesn't match for sliRecord and Bucket, sliId: %s, lastSLIRecord timestamp: %s, lastSLIRecordBucket timestamp: %s",
            startTimeStamp, sliId, lastSLIRecord.getTimestamp(), lastSLIRecordBucket.getBucketStartTime()));
      }
    }
    return lastSLIRecord;
  }
  @Override
  public SLIRecord getFirstSLIRecord(String sliId, Instant timestampInclusive) {
    SLIRecord firstRecord = hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
                                .filter(SLIRecordKeys.sliId, sliId)
                                .field(SLIRecordKeys.timestamp)
                                .greaterThanOrEq(timestampInclusive)
                                .order(Sort.ascending(SLIRecordKeys.timestamp))
                                .get();
    if (featureFlagService.isGlobalFlagEnabled(FeatureName.SRM_ENABLE_SLI_BUCKET.toString())) {
      SLIRecordBucket firstRecordBucket = sliRecordBucketService.getFirstSLIRecord(sliId, timestampInclusive);
      if (firstRecord != null && firstRecordBucket != null
          && !DateTimeUtils.roundUpTo5MinBoundary(firstRecord.getTimestamp())
                  .equals(firstRecordBucket.getBucketStartTime())) {
        log.error(String.format(
            "[SLI Record Bucketing Error] First SLI Record timestamp after %s doesn't match for sliRecord and Bucket, sliId: %s, firstSLIRecord timestamp: %s, firstSLIRecordBucket timestamp: %s",
            timestampInclusive, sliId, firstRecord.getTimestamp(), firstRecordBucket.getBucketStartTime()));
      }
    }
    return firstRecord;
  }

  @Override
  public SLIRecord getLatestSLIRecord(String sliId) {
    SLIRecord latestSLIRecord = hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
                                    .filter(SLIRecordKeys.sliId, sliId)
                                    .order(Sort.descending(SLIRecordKeys.timestamp))
                                    .get();
    SLIRecordBucket latestSLIRecordBucket = sliRecordBucketService.getLatestSLIRecord(sliId);
    if (featureFlagService.isGlobalFlagEnabled(FeatureName.SRM_ENABLE_SLI_BUCKET.toString())) {
      if (latestSLIRecord != null && latestSLIRecordBucket != null
          && !DateTimeUtils.roundDownTo5MinBoundary(latestSLIRecord.getTimestamp())
                  .equals(latestSLIRecordBucket.getBucketStartTime())) {
        log.error(String.format(
            "[SLI Record Bucketing Error] Latest SLI Record timestamp doesn't match for sliRecord and Bucket, sliId: %s, latestSLIRecord timestamp: %s, latestSLIRecordBucket timestamp: %s",
            sliId, latestSLIRecord.getTimestamp(), latestSLIRecordBucket.getBucketStartTime()));
      }
    }
    return latestSLIRecord;
  }

  private void validateListOfSLIRecordsWithBuckets(
      String sliId, List<SLIRecord> sliRecords, List<SLIRecordBucket> sliRecordBuckets) {
    Map<Instant, SLIRecord> sliRecordMap = sliRecords.stream().collect(Collectors.toMap(SLIRecord::getTimestamp,
        sliRecord
        -> sliRecord,
        (record1, record2) -> record1.getLastUpdatedAt() > record2.getLastUpdatedAt() ? record1 : record2));
    Map<Instant, SLIRecordBucket> sliRecordBucketMap =
        sliRecordBuckets.stream().collect(Collectors.toMap(SLIRecordBucket::getBucketStartTime,
            sliRecord
            -> sliRecord,
            (record1, record2) -> record1.getLastUpdatedAt() > record2.getLastUpdatedAt() ? record1 : record2));
    for (Instant instant : sliRecordBucketMap.keySet()) {
      if (!sliRecordMap.containsKey(instant)) {
        log.error(String.format(
            "[SLI Record Bucketing Error] SLI record doesn't contain this minute data, while sli bucket does. sliId: %s,timestamp: %s",
            sliId, instant));
      } else {
        SLIRecord sliRecord = sliRecordMap.get(instant);
        SLIRecordBucket sliRecordBucket = sliRecordBucketMap.get(instant);
        if (sliRecord.getRunningGoodCount() != sliRecordBucket.getRunningGoodCount()) {
          log.error(String.format(
              "[SLI Record Bucketing Error] SLI record and SLI Record Bucket good count doesn't match. sliId: %s,timestamp: %s",
              sliId, instant));
        }
        if (sliRecord.getRunningBadCount() != sliRecordBucket.getRunningBadCount()) {
          log.error(String.format(
              "[SLI Record Bucketing Error] SLI record and SLI Record Bucket bad count doesn't match. sliId: %s, timestamp: %s",
              sliId, instant));
        }
      }
    }
  }
}
