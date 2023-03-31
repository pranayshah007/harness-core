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
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.time.Clock;
import java.time.Duration;
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
import org.apache.commons.lang3.tuple.Pair;

public class SLIRecordServiceImpl implements SLIRecordService {
  @VisibleForTesting static int MAX_NUMBER_OF_POINTS = 2000;
  private static final int RETRY_COUNT = 3;
  @Inject private HPersistence hPersistence;
  @Inject Clock clock;

  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

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
    hPersistence.save(updateOrCreateSLIRecords);
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
    return getSLIRecordsOfMinutes(sliId, minutes);
  }

  @Override
  public double getErrorBudgetBurnRate(String sliId, long lookBackDuration, int totalErrorBudgetMinutes) {
    List<SLIRecord> sliRecords = getSLIRecordsForLookBackDuration(sliId, lookBackDuration);
    return sliRecords.size() < 2
        ? 0
        : (((double) (sliRecords.get(1).getRunningBadCount() - sliRecords.get(0).getRunningBadCount()) * 100)
            / totalErrorBudgetMinutes);
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
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .greaterThanOrEq(startTimeStamp)
        .field(SLIRecordKeys.sliVersion)
        .equal(sliVersion)
        .field(SLIRecordKeys.timestamp)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .asList();
  }
  @Override
  public void delete(List<String> sliIds) {
    hPersistence.delete(hPersistence.createQuery(SLIRecord.class).field(SLIRecordKeys.sliId).in(sliIds));
  }

  @Override
  public List<SLIRecord> getSLIRecordsOfMinutes(String sliId, List<Instant> minutes) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .in(minutes)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }
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
      List<SLIRecord> sliRecords = getSLIRecordsWithSLIVersion(sliId, startTime, endTime, sliVersion);
      if (!sliRecords.isEmpty()) {
        serviceLevelObjectivesDetailSLIRecordMap.put(objectivesDetail, sliRecords);
        objectivesDetailSLIMissingDataTypeMap.put(objectivesDetail, serviceLevelIndicator.getSliMissingDataType());
      }
    }
    return Pair.of(serviceLevelObjectivesDetailSLIRecordMap, objectivesDetailSLIMissingDataTypeMap);
  }

  @Override
  public SLIRecord getLastSLIRecord(String sliId, Instant startTimeStamp) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .lessThan(startTimeStamp)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }
  @Override
  public SLIRecord getFirstSLIRecord(String sliId, Instant timestampInclusive) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .greaterThanOrEq(timestampInclusive)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .get();
  }

  @Override
  public SLIRecord getLatestSLIRecord(String sliId) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthorityCount)
        .filter(SLIRecordKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }
}
