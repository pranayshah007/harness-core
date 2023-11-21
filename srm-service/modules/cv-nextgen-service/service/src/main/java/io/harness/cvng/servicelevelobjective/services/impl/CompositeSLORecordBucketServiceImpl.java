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
import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeSLOEvaluator;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecordBucket;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecordBucket.CompositeSLORecordBucketKeys;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordBucketService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.InvalidArgumentsException;

import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
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
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class CompositeSLORecordBucketServiceImpl implements CompositeSLORecordBucketService {
  private static final int RETRY_COUNT = 3;
  @Inject private SRMPersistence hPersistence;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject SLIRecordBucketService sliRecordBucketService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;

  @Inject Map<CompositeSLOFormulaType, CompositeSLOEvaluator> formulaTypeCompositeSLOEvaluatorMap;
  @Override
  public void create(CompositeServiceLevelObjective compositeServiceLevelObjective, Instant startTime, Instant endTime,
      String verificationTaskId) {
    Pair<Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>,
        Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>>
        sloDetailsSLIRecordsAndSLIMissingDataType = sliRecordBucketService.getSLODetailsSLIRecordsAndSLIMissingDataType(
            compositeServiceLevelObjective.getServiceLevelObjectivesDetails(), startTime, endTime);
    if (isDataForAllSLIsPresent(compositeServiceLevelObjective, sloDetailsSLIRecordsAndSLIMissingDataType)) {
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap = sloDetailsSLIRecordsAndSLIMissingDataType.getKey();
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
          objectivesDetailSLIMissingDataTypeMap = sloDetailsSLIRecordsAndSLIMissingDataType.getValue();
      String compositeSLOId = compositeServiceLevelObjective.getUuid();
      CompositeSLORecordBucket lastCompositeSLORecord = getLastCompositeSLORecordBucket(compositeSLOId, startTime);
      CompositeSLORecordBucket latestCompositeSLORecord = getLatestCompositeSLORecordBucket(compositeSLOId);
      double runningGoodCount =
          Objects.nonNull(lastCompositeSLORecord) ? lastCompositeSLORecord.getRunningGoodCount() : 0;
      double runningBadCount =
          Objects.nonNull(lastCompositeSLORecord) ? lastCompositeSLORecord.getRunningBadCount() : 0;
      if (isWindowProcessedAfterLatestRecord(startTime, latestCompositeSLORecord)) {
        updateCompositeSLORecords(serviceLevelObjectivesDetailCompositeSLORecordMap,
            objectivesDetailSLIMissingDataTypeMap, compositeServiceLevelObjective, runningGoodCount, runningBadCount,
            startTime, endTime);
      } else {
        List<CompositeSLORecordBucket> compositeSLORecords = getCompositeSLORecordsFromSLIsDetails(
            serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap, runningGoodCount,
            runningBadCount, compositeServiceLevelObjective);
        hPersistence.saveBatch(compositeSLORecords);
      }
      sloHealthIndicatorService.upsert(compositeServiceLevelObjective);
    }
  }

  private static boolean isWindowProcessedAfterLatestRecord(
      Instant startTime, CompositeSLORecordBucket latestCompositeSLORecord) {
    return Objects.nonNull(latestCompositeSLORecord)
        && latestCompositeSLORecord.getBucketStartTime()
               .plus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES)
               .isAfter(startTime);
  }

  private static boolean isDataForAllSLIsPresent(CompositeServiceLevelObjective compositeServiceLevelObjective,
      Pair<Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>,
          Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>>
          sloDetailsSLIRecordsAndSLIMissingDataType) {
    return !isEmpty(sloDetailsSLIRecordsAndSLIMissingDataType.getKey())
        && sloDetailsSLIRecordsAndSLIMissingDataType.getKey().size()
        == compositeServiceLevelObjective.getServiceLevelObjectivesDetails().size();
  }

  @Override
  public CompositeSLORecordBucket getLatestCompositeSLORecordBucket(String verificationTaskId) {
    return hPersistence.createQuery(CompositeSLORecordBucket.class, excludeAuthorityCount)
        .filter(CompositeSLORecordBucketKeys.verificationTaskId, verificationTaskId)
        .order(Sort.descending(CompositeSLORecordBucketKeys.bucketStartTime))
        .get();
  }

  @Override
  public CompositeSLORecordBucket getLastCompositeSLORecordBucket(String verificationTaskId, Instant startTimeStamp) {
    return hPersistence.createQuery(CompositeSLORecordBucket.class, excludeAuthorityCount)
        .filter(CompositeSLORecordBucketKeys.verificationTaskId, verificationTaskId)
        .field(CompositeSLORecordBucketKeys.bucketStartTime)
        .lessThan(startTimeStamp)
        .order(Sort.descending(CompositeSLORecordBucketKeys.bucketStartTime))
        .get();
  }

  @Override
  public List<CompositeSLORecordBucket> getSLORecordBuckets(
      String verificationTaskId, Instant startTimeStamp, Instant endTimeStamp) {
    return hPersistence.createQuery(CompositeSLORecordBucket.class, excludeAuthorityCount)
        .filter(CompositeSLORecordBucketKeys.verificationTaskId, verificationTaskId)
        .field(CompositeSLORecordBucketKeys.bucketStartTime)
        .greaterThanOrEq(startTimeStamp)
        .field(CompositeSLORecordBucketKeys.bucketStartTime)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(CompositeSLORecordBucketKeys.bucketStartTime))
        .asList();
  }

  @Override
  public List<CompositeSLORecordBucket> getLatestCountSLORecords(String verificationTaskId, int count) {
    return hPersistence.createQuery(CompositeSLORecordBucket.class, excludeAuthorityCount)
        .filter(CompositeSLORecordBucketKeys.verificationTaskId, verificationTaskId)
        .order(Sort.descending(CompositeSLORecordBucketKeys.bucketStartTime))
        .asList(new FindOptions().limit(count));
  }

  @RetryOnException(retryCount = RETRY_COUNT, retryOn = ConcurrentModificationException.class)
  public void updateCompositeSLORecords(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
          objectivesDetailSLIMissingDataTypeMap,
      CompositeServiceLevelObjective serviceLevelObjective, double runningGoodCount, double runningBadCount,
      Instant startTime, Instant endTime) {
    String verificationTaskId = serviceLevelObjective.getUuid();
    SLIEvaluationType sliEvaluationType = serviceLevelObjective.getSliEvaluationType();
    List<CompositeSLORecordBucket> toBeUpdatedSLORecordBuckets =
        getSLORecordBuckets(verificationTaskId, startTime, endTime.plus(1, ChronoUnit.MINUTES));
    Map<Instant, CompositeSLORecordBucket> sloRecordBucketMap =
        toBeUpdatedSLORecordBuckets.stream().collect(Collectors.toMap(
            CompositeSLORecordBucket::getBucketStartTime, Function.identity(), (sloRecordBucket1, sloRecordBucket2) -> {
              log.info("Duplicate SLO Key detected sloId: {}, timeStamp: {}", serviceLevelObjective.getUuid(),
                  sloRecordBucket1.getBucketStartTime());
              return sloRecordBucket1.getLastUpdatedAt() > sloRecordBucket2.getLastUpdatedAt() ? sloRecordBucket1
                                                                                               : sloRecordBucket2;
            }));
    List<CompositeSLORecordBucket> updateOrCreateSLORecordBuckets;
    if (sliEvaluationType == SLIEvaluationType.WINDOW) {
      updateOrCreateSLORecordBuckets = updateWindowCompositeSLORecordBuckets(
          serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap, runningGoodCount,
          runningBadCount, sloRecordBucketMap, serviceLevelObjective);
    } else if (sliEvaluationType == SLIEvaluationType.REQUEST) {
      updateOrCreateSLORecordBuckets = updateRequestCompositeSLORecordBuckets(
          serviceLevelObjectivesDetailCompositeSLORecordMap, serviceLevelObjective.getVersion(), runningGoodCount,
          runningBadCount, verificationTaskId, sloRecordBucketMap);
    } else {
      throw new InvalidArgumentsException("Invalid Evaluation Type");
    }

    try {
      hPersistence.upsertBatch(CompositeSLORecordBucket.class, updateOrCreateSLORecordBuckets, new ArrayList<>());
    } catch (IllegalAccessException exception) {
      log.error("SLO Records update failed through Bulk update {}", exception.getLocalizedMessage());
      hPersistence.save(updateOrCreateSLORecordBuckets);
    }
  }

  public List<CompositeSLORecordBucket> getCompositeSLORecordsFromSLIsDetails(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
          objectivesDetailSLIMissingDataTypeMap,
      double runningGoodCount, double runningBadCount, CompositeServiceLevelObjective compositeServiceLevelObjective) {
    SLIEvaluationType sliEvaluationType = compositeServiceLevelObjective.getSliEvaluationType();
    if (sliEvaluationType == SLIEvaluationType.REQUEST) {
      return getRequestCompositeSLORecordsFromSLIsDetails(serviceLevelObjectivesDetailCompositeSLORecordMap,
          runningGoodCount, runningBadCount, compositeServiceLevelObjective);
    } else {
      return getWindowCompositeSLORecordsFromSLIsDetails(serviceLevelObjectivesDetailCompositeSLORecordMap,
          objectivesDetailSLIMissingDataTypeMap, runningGoodCount, runningBadCount, compositeServiceLevelObjective);
    }
  }

  private List<CompositeSLORecordBucket> getWindowCompositeSLORecordsFromSLIsDetails(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
          objectivesDetailSLIMissingDataTypeMap,
      double runningGoodCount, double runningBadCount, CompositeServiceLevelObjective compositeServiceLevelObjective) {
    int sloVersion = compositeServiceLevelObjective.getVersion();
    String verificationTaskId = compositeServiceLevelObjective.getUuid();
    CompositeSLOFormulaType sloFormulaType = compositeServiceLevelObjective.getCompositeSLOFormulaType();
    Map<Instant, Pair<List<Double>, List<Integer>>> timeStampToBadValue = new HashMap<>();
    Map<Instant, Pair<List<Double>, List<Integer>>> timeStampToGoodValue = new HashMap<>();
    Map<Instant, Integer> timeStampTotalWeightage = new HashMap<>();
    getTimeStampMapsForGoodBadTotal(serviceLevelObjectivesDetailCompositeSLORecordMap,
        objectivesDetailSLIMissingDataTypeMap, timeStampToBadValue, timeStampToGoodValue, timeStampTotalWeightage);
    List<CompositeSLORecordBucket> sloRecordList = new ArrayList<>();
    int minute = 0;
    for (Instant instant : ImmutableSortedSet.copyOf(timeStampTotalWeightage.keySet())) {
      if (timeStampTotalWeightage.get(instant).equals(serviceLevelObjectivesDetailCompositeSLORecordMap.size())) {
        org.apache.commons.math3.util.Pair<Double, Double> currentCount =
            formulaTypeCompositeSLOEvaluatorMap.get(sloFormulaType)
                .evaluate(timeStampToGoodValue.get(instant).getLeft(), timeStampToGoodValue.get(instant).getRight(),
                    timeStampToBadValue.get(instant).getRight());

        double currentGoodCount = currentCount.getFirst();
        double currentBadCount = currentCount.getSecond();
        runningGoodCount += currentGoodCount;
        runningBadCount += currentBadCount;
        if ((minute + 1) % SLI_RECORD_BUCKET_SIZE == 0) {
          CompositeSLORecordBucket sloRecordBucket =
              CompositeSLORecordBucket.builder()
                  .runningBadCount(runningBadCount)
                  .runningGoodCount(runningGoodCount)
                  .sloVersion(sloVersion)
                  .verificationTaskId(verificationTaskId)
                  .bucketStartTime(instant.minus(SLI_RECORD_BUCKET_SIZE - 1, ChronoUnit.MINUTES)) // TODO date check
                  .build();
          sloRecordList.add(sloRecordBucket);
        }
      }
      minute++;
    }
    return sloRecordList;
  }

  private List<CompositeSLORecordBucket> getRequestCompositeSLORecordsFromSLIsDetails(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      double runningGoodCount, double runningBadCount, CompositeServiceLevelObjective compositeServiceLevelObjective) {
    String verificationTaskId = compositeServiceLevelObjective.getUuid();
    int sloVersion = compositeServiceLevelObjective.getVersion();
    List<CompositeSLORecordBucket> sloRecordList = new ArrayList<>();
    Map<Instant, Map<String, SLIRecordBucket>> timeStampToSLIRecordBucketMap =
        getTimeStampToSLIRecordBucketMap(serviceLevelObjectivesDetailCompositeSLORecordMap);
    for (Instant instant : timeStampToSLIRecordBucketMap.keySet()) {
      if (timeStampToSLIRecordBucketMap.get(instant).size()
          == serviceLevelObjectivesDetailCompositeSLORecordMap.size()) {
        CompositeSLORecordBucket sloRecord =
            CompositeSLORecordBucket.builder()
                .runningBadCount(runningBadCount)
                .runningGoodCount(runningGoodCount)
                .sloVersion(sloVersion)
                .verificationTaskId(verificationTaskId)
                .bucketStartTime(instant)
                .scopedIdentifierSLIRecordBucketMap(timeStampToSLIRecordBucketMap.get(instant))
                .build();
        sloRecordList.add(sloRecord);
      }
    }
    return sloRecordList;
  }

  private void getTimeStampMapsForGoodBadTotal(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
          objectivesDetailSLIMissingDataTypeMap, // we are updating the three maps
      Map<Instant, Pair<List<Double>, List<Integer>>> timeStampToBadValue, // status of SLO in that min
      Map<Instant, Pair<List<Double>, List<Integer>>> timeStampToGoodValue, // status of SLO in that min
      Map<Instant, Integer> timeStampToTotalValue) {
    for (Map.Entry<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
             objectivesDetailListEntry : serviceLevelObjectivesDetailCompositeSLORecordMap.entrySet()) {
      CompositeServiceLevelObjective.ServiceLevelObjectivesDetail objectivesDetail = objectivesDetailListEntry.getKey();
      for (SLIRecordBucket sliRecordBucket :
          objectivesDetailListEntry.getValue()) { // Iterate for the particular simple SLO
        for (int i = 0; i < sliRecordBucket.getSliStates().size(); i++) {
          SLIState sliState = sliRecordBucket.getSliStates().get(i);
          Instant currentTime = sliRecordBucket.getBucketStartTime().plus(i, ChronoUnit.MINUTES);
          Pair<List<Double>, List<Integer>> badCountPair = timeStampToBadValue.getOrDefault(currentTime,
              Pair.of(new ArrayList<>(), new ArrayList<>())); // list of weights + is it of good/bad for simple SLO
          Pair<List<Double>, List<Integer>> goodCountPair =
              timeStampToGoodValue.getOrDefault(currentTime, Pair.of(new ArrayList<>(), new ArrayList<>()));
          timeStampToBadValue.put(currentTime, badCountPair);
          timeStampToGoodValue.put(currentTime, goodCountPair);
          timeStampToTotalValue.put(currentTime, timeStampToTotalValue.getOrDefault(currentTime, 0) + 1);
          if (SLIState.GOOD.equals(sliState)
              || (SLIState.NO_DATA.equals(sliState)
                  && objectivesDetailSLIMissingDataTypeMap.get(objectivesDetail).equals(SLIMissingDataType.GOOD))) {
            badCountPair.getLeft().add(objectivesDetail.getWeightagePercentage());
            badCountPair.getRight().add(0);
            goodCountPair.getLeft().add(objectivesDetail.getWeightagePercentage());
            goodCountPair.getRight().add(1);
          } else if (SLIState.BAD.equals(sliState)
              || (SLIState.NO_DATA.equals(sliState)
                  && objectivesDetailSLIMissingDataTypeMap.get(objectivesDetail).equals(SLIMissingDataType.BAD))) {
            badCountPair.getLeft().add(objectivesDetail.getWeightagePercentage());
            badCountPair.getRight().add(1);
            goodCountPair.getLeft().add(objectivesDetail.getWeightagePercentage());
            goodCountPair.getRight().add(0);
          } else {
            badCountPair.getLeft().add(objectivesDetail.getWeightagePercentage());
            badCountPair.getRight().add(-1);
            goodCountPair.getLeft().add(objectivesDetail.getWeightagePercentage());
            goodCountPair.getRight().add(-1);
          }
        }
      }
    }
  }

  private Map<Instant, Map<String, SLIRecordBucket>> getTimeStampToSLIRecordBucketMap(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap) {
    Map<Instant, Map<String, SLIRecordBucket>> timeStampToSLIRecordsMap = new HashMap<>();
    for (CompositeServiceLevelObjective.ServiceLevelObjectivesDetail objectivesDetail :
        serviceLevelObjectivesDetailCompositeSLORecordMap.keySet()) { // iterate for each of simple SLO
      for (SLIRecordBucket sliRecordBucket :
          serviceLevelObjectivesDetailCompositeSLORecordMap.get(objectivesDetail)) { // iterate for each of the buckets
        // TODO check if skip data handling is correct.
        Map<String, SLIRecordBucket> serviceLevelObjectivesDetailSLIRecordMap = timeStampToSLIRecordsMap.getOrDefault(
            sliRecordBucket.getBucketStartTime().plus(SLI_RECORD_BUCKET_SIZE - 1, ChronoUnit.MINUTES), new HashMap<>());
        serviceLevelObjectivesDetailSLIRecordMap.put(
            serviceLevelObjectiveV2Service.getScopedIdentifier(objectivesDetail), sliRecordBucket);
        timeStampToSLIRecordsMap.put(
            sliRecordBucket.getBucketStartTime().plus(SLI_RECORD_BUCKET_SIZE - 1, ChronoUnit.MINUTES),
            serviceLevelObjectivesDetailSLIRecordMap);
      }
    }
    return timeStampToSLIRecordsMap;
  }

  private List<CompositeSLORecordBucket> updateWindowCompositeSLORecordBuckets(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
          objectivesDetailSLIMissingDataTypeMap,
      double previousRunningGoodCount, double previousRunningBadCount,
      Map<Instant, CompositeSLORecordBucket> sloRecordBucketMap,
      CompositeServiceLevelObjective compositeServiceLevelObjective) {
    int sloVersion = compositeServiceLevelObjective.getVersion();
    String verificationTaskId = compositeServiceLevelObjective.getUuid();
    CompositeSLOFormulaType sloFormulaType = compositeServiceLevelObjective.getCompositeSLOFormulaType();
    List<CompositeSLORecordBucket> updateOrCreateSLORecords = new ArrayList<>();
    Map<Instant, Pair<List<Double>, List<Integer>>> timeStampToBadValue = new HashMap<>(); // weight with current total
    Map<Instant, Pair<List<Double>, List<Integer>>> timeStampToGoodValue = new HashMap<>();
    Map<Instant, Integer> timeStampToTotalValue = new HashMap<>();
    getTimeStampMapsForGoodBadTotal(serviceLevelObjectivesDetailCompositeSLORecordMap,
        objectivesDetailSLIMissingDataTypeMap, timeStampToBadValue, timeStampToGoodValue, timeStampToTotalValue);
    int minute = 0;
    for (Instant instant :
        ImmutableSortedSet.copyOf(timeStampToTotalValue.keySet())) { // maybe we need a better iteration
      if (timeStampToTotalValue.get(instant).equals(serviceLevelObjectivesDetailCompositeSLORecordMap.size())) {
        org.apache.commons.math3.util.Pair<Double, Double> currentCount =
            formulaTypeCompositeSLOEvaluatorMap.get(sloFormulaType)
                .evaluate(timeStampToGoodValue.get(instant).getLeft(), timeStampToGoodValue.get(instant).getRight(),
                    timeStampToBadValue.get(instant).getRight());
        double currentGoodCount = currentCount.getFirst();
        double currentBadCount = currentCount.getSecond();
        previousRunningGoodCount += currentGoodCount;
        previousRunningBadCount += currentBadCount;
      }
      if ((minute + 1) % SLI_RECORD_BUCKET_SIZE == 0) {
        CompositeSLORecordBucket sloRecordBucket = sloRecordBucketMap.get(
            instant.minus(SLI_RECORD_BUCKET_SIZE - 1, ChronoUnit.MINUTES)); // map is of bucket startTime
        if (Objects.nonNull(sloRecordBucket)) {
          sloRecordBucket.setRunningGoodCount(previousRunningGoodCount);
          sloRecordBucket.setRunningBadCount(previousRunningBadCount);
          sloRecordBucket.setSloVersion(sloVersion);
        } else {
          sloRecordBucket = CompositeSLORecordBucket.builder()
                                .runningBadCount(previousRunningBadCount)
                                .runningGoodCount(previousRunningGoodCount)
                                .sloVersion(sloVersion)
                                .verificationTaskId(verificationTaskId)
                                .bucketStartTime(instant)
                                .build();
        }
        updateOrCreateSLORecords.add(sloRecordBucket);
      }
      minute++;
    }
    return updateOrCreateSLORecords;
  }

  private List<CompositeSLORecordBucket> updateRequestCompositeSLORecordBuckets(
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecordBucket>>
          serviceLevelObjectivesDetailCompositeSLORecordMap,
      int sloVersion, double runningGoodCount, double runningBadCount, String verificationTaskId,
      Map<Instant, CompositeSLORecordBucket> sloRecordMap) { // sloRecordMap is the current value and we are updating
                                                             // based on new entries, it can be a version upgrade
    List<CompositeSLORecordBucket> updateOrCreateSLORecords = new ArrayList<>();
    // A Map of  instant and simple slo id  and its bucket
    Map<Instant, Map<String, SLIRecordBucket>> timeStampToSLIRecordBucketsMap =
        getTimeStampToSLIRecordBucketMap(serviceLevelObjectivesDetailCompositeSLORecordMap);
    CompositeSLORecordBucket compositeSLORecordBucket = null;
    for (Instant instant : timeStampToSLIRecordBucketsMap.keySet()) {
      if (timeStampToSLIRecordBucketsMap.get(instant).size()
          == serviceLevelObjectivesDetailCompositeSLORecordMap.size()) {
        compositeSLORecordBucket = sloRecordMap.get(instant);
      }
      if (Objects.nonNull(compositeSLORecordBucket)) {
        compositeSLORecordBucket.setRunningGoodCount(runningGoodCount);
        compositeSLORecordBucket.setRunningBadCount(runningBadCount);
        compositeSLORecordBucket.setSloVersion(sloVersion);
        compositeSLORecordBucket.setScopedIdentifierSLIRecordBucketMap(timeStampToSLIRecordBucketsMap.get(instant));
      } else {
        compositeSLORecordBucket = CompositeSLORecordBucket.builder()
                                       .runningBadCount(runningBadCount)
                                       .runningGoodCount(runningGoodCount)
                                       .sloVersion(sloVersion)
                                       .verificationTaskId(verificationTaskId)
                                       .bucketStartTime(instant)
                                       .scopedIdentifierSLIRecordBucketMap(timeStampToSLIRecordBucketsMap.get(instant))
                                       .build();
      }
      updateOrCreateSLORecords.add(compositeSLORecordBucket);
    }
    return updateOrCreateSLORecords;
  }
}
