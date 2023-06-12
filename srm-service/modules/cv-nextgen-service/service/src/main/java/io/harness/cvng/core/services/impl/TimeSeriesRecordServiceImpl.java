/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_WINDOW_SIZE_NEW;
import static io.harness.cvng.core.services.CVNextGenConstants.CV_ANALYSIS_WINDOW_MINUTES;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.util.Comparator.comparingLong;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.beans.TimeSeriesTestDataDTO;
import io.harness.cvng.analysis.beans.TimeSeriesTestDataDTO.MetricData;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.beans.HostRecordDTO;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord.TimeSeriesDataRecordGroupValue;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord.TimeSeriesDataRecordMetricValue;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.beans.demo.DemoMetricParams;
import io.harness.cvng.core.beans.demo.DemoTemplate;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.entities.TimeSeriesRecord.TimeSeriesGroupValue;
import io.harness.cvng.core.entities.TimeSeriesRecord.TimeSeriesRecordKeys;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.demo.CVNGDemoDataIndexService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.persistence.HPersistence;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.UpdateOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class TimeSeriesRecordServiceImpl implements TimeSeriesRecordService {
  private static final List<Integer> DEMO_DATA =
      Arrays.asList(30, 81, 70, 43, 20, 20, 41, 51, 10, 80, 50, 40, 30, 70, 80);
  @Inject private HPersistence hPersistence;
  @Inject private CVConfigService cvConfigService;
  @Inject private MetricPackService metricPackService;
  @Inject private HostRecordService hostRecordService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private CVNGDemoDataIndexService cvngDemoDataIndexService;

  @Override
  public boolean save(List<TimeSeriesDataCollectionRecord> dataRecords) {
    List<TimeSeriesDataCollectionRecord> validDataRecords = filterValidDataRecords(dataRecords);
    if (CollectionUtils.isEmpty(validDataRecords)) {
      return true;
    }
    log.info("Saving {} data records", validDataRecords.size());
    log.info("Records being saved: {}", StringUtils.join(validDataRecords, ","));
    UpdateOptions options = new UpdateOptions();
    options.upsert(true);
    Map<TimeSeriesRecordBucketKey, TimeSeriesRecord> timeSeriesRecordMap = bucketTimeSeriesRecords(validDataRecords);
    timeSeriesRecordMap.forEach((timeSeriesRecordBucketKey, timeSeriesRecord) -> {
      List<TimeSeriesMetricDefinition> metricTemplates =
          timeSeriesAnalysisService.getMetricTemplate(timeSeriesRecord.getVerificationTaskId());
      TimeSeriesMetricDefinition timeSeriesMetricDefinition =
          metricTemplates.stream()
              .filter(metricTemplate -> metricTemplate.getMetricName().equals(timeSeriesRecord.getMetricName()))
              .findFirst()
              .orElse(null);
      TimeSeriesMetricType metricType =
          timeSeriesMetricDefinition != null ? timeSeriesMetricDefinition.getMetricType() : null;
      Query<TimeSeriesRecord> query =
          hPersistence.createQuery(TimeSeriesRecord.class)
              .filter(
                  TimeSeriesRecordKeys.bucketStartTime, Instant.ofEpochMilli(timeSeriesRecordBucketKey.getTimestamp()))
              .filter(TimeSeriesRecordKeys.metricName, timeSeriesRecordBucketKey.getMetricName())
              .filter(TimeSeriesRecordKeys.verificationTaskId, timeSeriesRecord.getVerificationTaskId());
      if (timeSeriesRecord.getHost() != null) {
        query = query.filter(TimeSeriesRecordKeys.host, timeSeriesRecord.getHost());
      }

      UpdateOperations<TimeSeriesRecord> updateOperations =
          hPersistence.createUpdateOperations(TimeSeriesRecord.class)
              .setOnInsert(TimeSeriesRecordKeys.uuid, generateUuid())
              .setOnInsert(VerificationTaskBaseKeys.createdAt, Instant.now().toEpochMilli())
              .setOnInsert(TimeSeriesRecordKeys.validUntil, TimeSeriesRecord.builder().build().getValidUntil())
              .set(TimeSeriesRecordKeys.accountId, timeSeriesRecord.getAccountId())
              .addToSet(TimeSeriesRecordKeys.timeSeriesGroupValues,
                  Lists.newArrayList(timeSeriesRecord.getTimeSeriesGroupValues()));
      if (Objects.nonNull(metricType)) {
        updateOperations.setOnInsert(TimeSeriesRecordKeys.metricType, metricType);
      }
      if (Objects.nonNull(timeSeriesRecord.getMetricIdentifier())) {
        updateOperations.set(TimeSeriesRecordKeys.metricIdentifier, timeSeriesRecord.getMetricIdentifier());
      }
      hPersistence.getDatastore(TimeSeriesRecord.class).update(query, updateOperations, options);
    });
    saveHosts(validDataRecords);
    return true;
  }

  private List<TimeSeriesDataCollectionRecord> filterValidDataRecords(
      List<TimeSeriesDataCollectionRecord> dataRecords) {
    return CollectionUtils.emptyIfNull(dataRecords)
        .stream()
        .map(dataRecord
            -> dataRecord.toBuilder()
                   .metricValues(
                       dataRecord.getMetricValues()
                           .stream()
                           .map(metricValue
                               -> metricValue.toBuilder()
                                      .timeSeriesValues(
                                          metricValue.getTimeSeriesValues()
                                              .stream()
                                              .filter(groupValue -> Double.isFinite(groupValue.getValue()))
                                              .filter(groupValue
                                                  -> groupValue.getPercent() == null
                                                      || Double.isFinite(groupValue.getPercent()))
                                              .collect(Collectors.toSet()))
                                      .build())
                           .filter(metricValue -> CollectionUtils.isNotEmpty(metricValue.getTimeSeriesValues()))
                           .collect(Collectors.toSet()))
                   .build())
        .filter(dataRecord -> CollectionUtils.isNotEmpty(dataRecord.getMetricValues()))
        .collect(Collectors.toList());
  }

  @Value
  @Builder
  private static class TimeSeriesRecordBucketKey {
    String host;
    long timestamp;
    String metricName;
  }

  private void saveHosts(List<TimeSeriesDataCollectionRecord> dataRecords) {
    if (isNotEmpty(dataRecords)) {
      Preconditions.checkState(
          dataRecords.stream().map(dataRecord -> dataRecord.getVerificationTaskId()).distinct().count() == 1,
          "All the verificationIds should be same");
      String verificationTaskId = dataRecords.get(0).getVerificationTaskId();
      long minTimestamp = dataRecords.stream()
                              .map(dataRecord -> dataRecord.getTimeStamp())
                              .min(comparingLong(timestamp -> timestamp))
                              .get();
      long maxTimeStamp = dataRecords.stream()
                              .map(dataRecord -> dataRecord.getTimeStamp())
                              .max(comparingLong(timestamp -> timestamp))
                              .get();
      Set<String> hosts = dataRecords.stream()
                              .map(dataRecord -> dataRecord.getHost())
                              .filter(host -> host != null)
                              .collect(Collectors.toSet());
      if (isNotEmpty(hosts)) {
        HostRecordDTO hostRecordDTO = HostRecordDTO.builder()
                                          .verificationTaskId(verificationTaskId)
                                          .startTime(Instant.ofEpochMilli(minTimestamp))
                                          .endTime(Instant.ofEpochMilli(maxTimeStamp))
                                          .hosts(hosts)
                                          .build();
        hostRecordService.save(hostRecordDTO);
      }
    }
  }

  private Map<TimeSeriesRecordBucketKey, TimeSeriesRecord> bucketTimeSeriesRecords(
      List<TimeSeriesDataCollectionRecord> dataRecords) {
    Map<String, TimeSeriesMetricType> metricTypeMap = new HashMap<>();
    List<TimeSeriesMetricDefinition> metricDefinitions =
        timeSeriesAnalysisService.getMetricTemplate(dataRecords.get(0).getVerificationTaskId());
    metricDefinitions.forEach(timeSeriesMetricDefinition
        -> metricTypeMap.put(timeSeriesMetricDefinition.getMetricName(), timeSeriesMetricDefinition.getMetricType()));
    populatePercent(metricTypeMap, dataRecords);
    Map<TimeSeriesRecordBucketKey, TimeSeriesRecord> rv = new HashMap<>();
    dataRecords.forEach(dataRecord -> {
      long bucketBoundary = dataRecord.getTimeStamp()
          - Math.floorMod(dataRecord.getTimeStamp(), TimeUnit.MINUTES.toMillis(CV_ANALYSIS_WINDOW_MINUTES));
      dataRecord.getMetricValues().forEach(timeSeriesDataRecordMetricValue -> {
        String metricName = timeSeriesDataRecordMetricValue.getMetricName();
        String metricIdentifier = timeSeriesDataRecordMetricValue.getMetricIdentifier();
        TimeSeriesRecordBucketKey timeSeriesRecordBucketKey = TimeSeriesRecordBucketKey.builder()
                                                                  .host(dataRecord.getHost())
                                                                  .metricName(metricName)
                                                                  .timestamp(bucketBoundary)
                                                                  .build();
        if (!rv.containsKey(timeSeriesRecordBucketKey)) {
          rv.put(timeSeriesRecordBucketKey,
              TimeSeriesRecord.builder()
                  .accountId(dataRecord.getAccountId())
                  .verificationTaskId(dataRecord.getVerificationTaskId())
                  .host(dataRecord.getHost())
                  .accountId(dataRecord.getAccountId())
                  .bucketStartTime(Instant.ofEpochMilli(bucketBoundary))
                  .metricName(metricName)
                  .metricIdentifier(metricIdentifier)
                  .build());
        }

        timeSeriesDataRecordMetricValue.getTimeSeriesValues().forEach(timeSeriesDataRecordGroupValue
            -> rv.get(timeSeriesRecordBucketKey)
                   .getTimeSeriesGroupValues()
                   .add(TimeSeriesGroupValue.builder()
                            .groupName(timeSeriesDataRecordGroupValue.getGroupName())
                            .timeStamp(Instant.ofEpochMilli(dataRecord.getTimeStamp()))
                            .metricValue(timeSeriesDataRecordGroupValue.getValue())
                            .percentValue(TimeSeriesMetricType.ERROR.equals(metricTypeMap.get(metricName))
                                        && timeSeriesDataRecordGroupValue.getPercent() != null
                                    ? timeSeriesDataRecordGroupValue.getPercent()
                                    : null)
                            .build()));
      });
    });
    return rv;
  }

  private void populatePercent(
      Map<String, TimeSeriesMetricType> metricTypeMap, List<TimeSeriesDataCollectionRecord> dataRecords) {
    dataRecords.forEach(dataRecord -> {
      TimeSeriesDataRecordMetricValue throughput =
          dataRecord.getMetricValues()
              .stream()
              .filter(
                  metricValue -> TimeSeriesMetricType.THROUGHPUT.equals(metricTypeMap.get(metricValue.getMetricName())))
              .findFirst()
              .orElse(null);

      for (TimeSeriesDataRecordMetricValue metricValue : dataRecord.getMetricValues()) {
        if (!TimeSeriesMetricType.ERROR.equals(metricTypeMap.get(metricValue.getMetricName()))) {
          continue;
        }

        // if no throughput is configured then percent value is same as value
        if (throughput == null) {
          metricValue.getTimeSeriesValues().forEach(
              errorMetricValue -> errorMetricValue.setPercent(errorMetricValue.getValue()));
          continue;
        }

        for (TimeSeriesDataRecordGroupValue throughputValue : throughput.getTimeSeriesValues()) {
          if (throughputValue.getValue() <= 0.0) {
            continue;
          }

          for (TimeSeriesDataRecordGroupValue errorMetricValue : metricValue.getTimeSeriesValues()) {
            if (!errorMetricValue.getGroupName().equals(throughputValue.getGroupName())) {
              continue;
            }

            errorMetricValue.setPercent((errorMetricValue.getValue() * 100) / throughputValue.getValue());
          }
        }
      }
    });
  }

  @Override
  public boolean updateRiskScores(String verificationTaskId, TimeSeriesRiskSummary riskSummary) {
    Set<String> metricIdentifiers = riskSummary.getTransactionMetricRiskList()
                                        .stream()
                                        .map(TimeSeriesRiskSummary.TransactionMetricRisk::getMetricIdentifier)
                                        .collect(Collectors.toSet());
    List<TimeSeriesRecord> records =
        hPersistence.createQuery(TimeSeriesRecord.class, excludeAuthority)
            .filter(TimeSeriesRecordKeys.verificationTaskId, riskSummary.getVerificationTaskId())
            .field(TimeSeriesRecordKeys.bucketStartTime)
            .greaterThanOrEq(
                riskSummary.getAnalysisEndTime().minus(TIMESERIES_SERVICE_GUARD_WINDOW_SIZE_NEW, ChronoUnit.MINUTES))
            .field(TimeSeriesRecordKeys.bucketStartTime)
            .lessThan(riskSummary.getAnalysisEndTime())
            .field(TimeSeriesRecordKeys.metricIdentifier)
            .in(metricIdentifiers)
            .asList();

    Map<String, List<TimeSeriesRecord>> metricNameRecordMap =
        records.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getMetricIdentifier));

    riskSummary.getTransactionMetricRiskList().forEach(metricRisk -> {
      List<TimeSeriesRecord> timeSeriesRecords = metricNameRecordMap.get(metricRisk.getMetricIdentifier());
      if (isNotEmpty(timeSeriesRecords)) {
        timeSeriesRecords.forEach(record -> {
          String groupName = metricRisk.getTransactionName();
          record.getTimeSeriesGroupValues().forEach(groupValue -> {
            if (groupName.equals(groupValue.getGroupName())) {
              groupValue.setRiskScore(Math.max(metricRisk.getMetricRisk().getValue(), groupValue.getRiskScore()));
            }
          });
        });
      }
    });
    log.info("Updating the risk in {} timeseries records", records.size());
    hPersistence.save(records);

    return false;
  }

  @Override
  public List<TimeSeriesMetricDefinition> getTimeSeriesMetricDefinitions(String cvConfigId) {
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    Preconditions.checkNotNull(cvConfig, "could not find datasource with id ", cvConfigId);

    MetricCVConfig metricCVConfig = (MetricCVConfig) cvConfig;
    return getTimeSeriesMetricDefinitions(metricCVConfig);
  }

  @Override
  public List<TimeSeriesMetricDefinition> getTimeSeriesMetricDefinitions(MetricCVConfig metricCVConfig) {
    List<TimeSeriesMetricDefinition> timeSeriesMetricDefinitions = new ArrayList<>();
    // add project level thresholds
    List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
        metricCVConfig.getAccountId(), metricCVConfig.getOrgIdentifier(), metricCVConfig.getProjectIdentifier(),
        metricCVConfig.getMetricPack().getIdentifier(), metricCVConfig.getType());

    // For backward compatibility
    metricPackThresholds.forEach(metricPackThreshold
        -> metricPackThreshold.setDeviationType(metricPackThreshold.getMetricType().getDeviationType()));
    Set<String> includedMetrics = metricCVConfig.getMetricPack()
                                      .getMetrics()
                                      .stream()
                                      .filter(MetricDefinition::isIncluded)
                                      .map(MetricPack.MetricDefinition::getIdentifier)
                                      .collect(Collectors.toSet());

    metricPackThresholds = metricPackThresholds.stream()
                               .filter(mpt -> includedMetrics.contains(mpt.getMetricIdentifier()))
                               .collect(Collectors.toList());

    if (!Objects.isNull(metricCVConfig.getMetricPack()) && isNotEmpty(metricCVConfig.getMetricPack().getMetrics())) {
      for (MetricDefinition metricDefinition : metricCVConfig.getMetricPack().getMetrics()) {
        if (isNotEmpty(metricDefinition.getThresholds())) {
          for (TimeSeriesThreshold timeSeriesThreshold : metricDefinition.getThresholds()) {
            if (ThresholdConfigType.USER_DEFINED.equals(timeSeriesThreshold.getThresholdConfigType())) {
              if (Objects.isNull(timeSeriesThreshold.getDeviationType())) {
                timeSeriesThreshold.setDeviationType(timeSeriesThreshold.getMetricType().getDeviationType());
              }
              metricPackThresholds.add(timeSeriesThreshold);
            }
          }
        }
      }
    }

    metricPackThresholds.forEach(timeSeriesThreshold
        -> timeSeriesMetricDefinitions.add(TimeSeriesMetricDefinition.builder()
                                               .id(timeSeriesThreshold.getUuid())
                                               .metricName(timeSeriesThreshold.getMetricName())
                                               .metricIdentifier(timeSeriesThreshold.getMetricIdentifier())
                                               .metricType(timeSeriesThreshold.getMetricType())
                                               .metricGroupName(timeSeriesThreshold.getMetricGroupName())
                                               .actionType(timeSeriesThreshold.getAction())
                                               .comparisonType(timeSeriesThreshold.getCriteria().getType())
                                               .action(timeSeriesThreshold.getCriteria().getAction())
                                               .occurrenceCount(timeSeriesThreshold.getCriteria().getOccurrenceCount())
                                               .thresholdType(timeSeriesThreshold.getCriteria().getThresholdType())
                                               .value(timeSeriesThreshold.getCriteria().getValue())
                                               .thresholdConfigType(timeSeriesThreshold.getThresholdConfigType())
                                               .deviationType(timeSeriesThreshold.getDeviationType())
                                               .build()));

    // add data source level thresholds
    metricCVConfig.getMetricPack()
        .getMetrics()
        .stream()
        .filter(MetricDefinition::isIncluded)
        .forEach(metricDefinition -> {
          if (isNotEmpty(metricDefinition.getThresholds())) {
            metricDefinition.getThresholds().forEach(timeSeriesThreshold -> {
              if (!ThresholdConfigType.USER_DEFINED.equals(timeSeriesThreshold.getThresholdConfigType())) {
                timeSeriesMetricDefinitions.add(
                    TimeSeriesMetricDefinition.builder()
                        .id(timeSeriesThreshold.getUuid())
                        .metricName(metricDefinition.getName())
                        .metricIdentifier(metricDefinition.getIdentifier())
                        .metricType(metricDefinition.getType())
                        .metricGroupName(timeSeriesThreshold.getMetricGroupName())
                        .actionType(timeSeriesThreshold.getAction())
                        .comparisonType(timeSeriesThreshold.getCriteria().getType())
                        .action(timeSeriesThreshold.getCriteria().getAction())
                        .occurrenceCount(timeSeriesThreshold.getCriteria().getOccurrenceCount())
                        .thresholdType(timeSeriesThreshold.getCriteria().getThresholdType())
                        .value(timeSeriesThreshold.getCriteria().getValue())
                        .thresholdConfigType(timeSeriesThreshold.getThresholdConfigType())
                        .deviationType(timeSeriesThreshold.getDeviationType() == null
                                ? metricDefinition.getType().getDeviationType()
                                : timeSeriesThreshold.getDeviationType())
                        .build());
              }
            });
          }
        });
    return timeSeriesMetricDefinitions;
  }

  @Override
  public TimeSeriesTestDataDTO getMetricGroupDataForRange(
      String cvConfigId, Instant startTime, Instant endTime, String metricName, List<String> groupNames) {
    Preconditions.checkNotNull(cvConfigId, "cvConfigId is null in getTimeseriesDataForRange");

    TimeSeriesTestDataDTO timeseriesData = getTxnMetricDataForRange(cvConfigId, startTime, endTime, metricName, null);

    Map<String, Map<String, List<MetricData>>> metricNameGroupNameValMap = new HashMap<>();
    if (timeseriesData != null) {
      timeseriesData.getMetricGroupValues().forEach((groupName, metricValueMap) -> {
        if (!metricNameGroupNameValMap.containsKey(metricName)) {
          metricNameGroupNameValMap.put(metricName, new HashMap<>());
        }

        if (isNotEmpty(groupNames) && groupNames.contains(groupName)) {
          List<MetricData> values = metricValueMap.get(metricName);
          if (!metricNameGroupNameValMap.containsKey(metricName)) {
            metricNameGroupNameValMap.put(metricName, new HashMap<>());
          }

          metricNameGroupNameValMap.get(metricName).put(groupName, values);
        } else if (isEmpty(groupNames)) {
          // we need to add for all transactions without filtering
          metricNameGroupNameValMap.get(metricName).put(groupName, metricValueMap.get(metricName));
        }
      });

      return TimeSeriesTestDataDTO.builder()
          .cvConfigId(cvConfigId)
          .metricGroupValues(metricNameGroupNameValMap)
          .build();
    }
    return null;
  }

  // TODO: create a overridden method without metric and txnName
  @Override
  public TimeSeriesTestDataDTO getTxnMetricDataForRange(
      String verificationTaskId, Instant startTime, Instant endTime, String metricName, String txnName) {
    List<TimeSeriesRecord> records = getTimeSeriesRecords(verificationTaskId, startTime, endTime, metricName, null);

    Map<String, List<TimeSeriesGroupValue>> metricValueList = new HashMap<>();
    records.forEach(record -> {
      if (!metricValueList.containsKey(record.getMetricName())) {
        metricValueList.put(record.getMetricName(), new ArrayList<>());
      }

      List<TimeSeriesRecord.TimeSeriesGroupValue> valueList = metricValueList.get(record.getMetricName());
      List<TimeSeriesRecord.TimeSeriesGroupValue> curValueList = new ArrayList<>();
      // if txnName filter is present, filter by that name
      if (isNotEmpty(txnName)) {
        record.getTimeSeriesGroupValues().forEach(timeSeriesGroupValue -> {
          if (timeSeriesGroupValue.getGroupName().equals(txnName)) {
            curValueList.add(timeSeriesGroupValue);
          }
        });
      } else {
        curValueList.addAll(record.getTimeSeriesGroupValues());
      }

      // filter for those timestamps that fall within the start and endTime
      curValueList.forEach(groupValue -> {
        boolean timestampInWindow =
            !(groupValue.getTimeStamp().isBefore(startTime) || groupValue.getTimeStamp().isAfter(endTime));
        if (timestampInWindow) {
          valueList.add(groupValue);
        }
      });

      metricValueList.put(record.getMetricName(), valueList);
    });

    return getSortedListOfTimeSeriesRecords(verificationTaskId, metricValueList);
  }
  // TODO: use accountId
  private List<TimeSeriesRecord> getTimeSeriesRecords(
      String verificationTaskId, Instant startTime, Instant endTime, Set<String> hosts) {
    return getTimeSeriesRecords(verificationTaskId, startTime, endTime, null, hosts);
  }

  private List<TimeSeriesRecord> getTimeSeriesRecords(
      String verificationTaskId, Instant startTime, Instant endTime, String metricName, Set<String> hosts) {
    startTime = DateTimeUtils.roundDownToMinBoundary(startTime, (int) CV_ANALYSIS_WINDOW_MINUTES);
    Instant queryStartTime = startTime.truncatedTo(ChronoUnit.SECONDS);
    Instant queryEndTime = endTime.truncatedTo(ChronoUnit.SECONDS);
    Query<TimeSeriesRecord> timeSeriesRecordsQuery =
        hPersistence.createQuery(TimeSeriesRecord.class, excludeAuthority)
            .filter(TimeSeriesRecordKeys.verificationTaskId, verificationTaskId)
            .field(TimeSeriesRecordKeys.bucketStartTime)
            .greaterThanOrEq(queryStartTime)
            .field(TimeSeriesRecordKeys.bucketStartTime)
            .lessThan(queryEndTime);
    if (isNotEmpty(hosts)) {
      timeSeriesRecordsQuery.field(TimeSeriesRecordKeys.host).hasAnyOf(hosts);
    }
    if (isNotEmpty(metricName)) {
      timeSeriesRecordsQuery = timeSeriesRecordsQuery.filter(TimeSeriesRecordKeys.metricName, metricName);
    }
    // TODO: filter values that are outside of given time range.
    return timeSeriesRecordsQuery.asList();
  }

  @Override
  public List<TimeSeriesRecordDTO> getTimeSeriesRecordDTOs(
      String verificationTaskId, Instant startTime, Instant endTime) {
    List<TimeSeriesRecord> timeSeriesRecords = getTimeSeriesRecords(verificationTaskId, startTime, endTime, null);
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOS = new ArrayList<>();
    timeSeriesRecords.forEach(timeSeriesRecord -> {
      for (TimeSeriesRecord.TimeSeriesGroupValue record : timeSeriesRecord.getTimeSeriesGroupValues()) {
        if (record.getTimeStamp().compareTo(startTime) >= 0 && record.getTimeStamp().compareTo(endTime) < 0) {
          TimeSeriesRecordDTO timeSeriesRecordDTO =
              TimeSeriesRecordDTO.builder()
                  .groupName(record.getGroupName())
                  .host(timeSeriesRecord.getHost())
                  .metricName(timeSeriesRecord.getMetricName())
                  .metricIdentifier(timeSeriesRecord.getMetricIdentifier())
                  .epochMinute(TimeUnit.MILLISECONDS.toMinutes(record.getTimeStamp().toEpochMilli()))
                  .verificationTaskId(timeSeriesRecord.getVerificationTaskId())
                  .metricValue(record.getMetricValue())
                  .build();
          timeSeriesRecordDTOS.add(timeSeriesRecordDTO);
        }
      }
    });
    return timeSeriesRecordDTOS;
  }

  @Override
  public List<TimeSeriesRecordDTO> getDeploymentMetricTimeSeriesRecordDTOs(
      String verificationTaskId, Instant startTime, Instant endTime, Set<String> hosts) {
    List<TimeSeriesRecord> timeSeriesRecords = getTimeSeriesRecords(verificationTaskId, startTime, endTime, hosts);
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOS = new ArrayList<>();
    timeSeriesRecords.forEach(timeSeriesRecord -> {
      for (TimeSeriesRecord.TimeSeriesGroupValue record : timeSeriesRecord.getTimeSeriesGroupValues()) {
        if (record.getTimeStamp().compareTo(startTime) >= 0 && record.getTimeStamp().compareTo(endTime) < 0) {
          TimeSeriesRecordDTO timeSeriesRecordDTO =
              TimeSeriesRecordDTO.builder()
                  .groupName(record.getGroupName())
                  .host(timeSeriesRecord.getHost())
                  .metricName(timeSeriesRecord.getMetricName())
                  .metricIdentifier(timeSeriesRecord.getMetricIdentifier())
                  .epochMinute(TimeUnit.MILLISECONDS.toMinutes(record.getTimeStamp().toEpochMilli()))
                  .verificationTaskId(timeSeriesRecord.getVerificationTaskId())
                  .metricValue(record.getMetricValue())
                  .build();
          timeSeriesRecordDTOS.add(timeSeriesRecordDTO);
        }
      }
    });
    return timeSeriesRecordDTOS;
  }

  private TimeSeriesTestDataDTO getSortedListOfTimeSeriesRecords(
      String cvConfigId, Map<String, List<TimeSeriesRecord.TimeSeriesGroupValue>> unsortedTimeseries) {
    if (isNotEmpty(unsortedTimeseries)) {
      Map<String, Map<String, List<TimeSeriesRecord.TimeSeriesGroupValue>>> txnMetricMap = new HashMap<>();

      // first build the txn -> metric -> TimeSeriesGroupValue object
      unsortedTimeseries.forEach((metricName, txnValList) -> {
        txnValList.forEach(txnValue -> {
          String txnName = txnValue.getGroupName();
          if (!txnMetricMap.containsKey(txnName)) {
            txnMetricMap.put(txnName, new HashMap<>());
          }
          if (!txnMetricMap.get(txnName).containsKey(metricName)) {
            txnMetricMap.get(txnName).put(metricName, new ArrayList<>());
          }

          txnMetricMap.get(txnName).get(metricName).add(txnValue);
        });
      });

      // next sort the list under each txn->metric
      Map<String, Map<String, List<Double>>> txnMetricValueMap = new HashMap<>();
      Map<String, Map<String, List<MetricData>>> metricGroupValueMap = new HashMap<>();
      for (String txnName : txnMetricMap.keySet()) {
        Map<String, List<TimeSeriesRecord.TimeSeriesGroupValue>> metricValueMap = txnMetricMap.get(txnName);
        txnMetricValueMap.put(txnName, new HashMap<>());
        metricGroupValueMap.put(txnName, new HashMap<>());
        for (String metricName : metricValueMap.keySet()) {
          List<TimeSeriesRecord.TimeSeriesGroupValue> valueList = metricValueMap.get(metricName);
          Collections.sort(valueList);
          txnMetricValueMap.get(txnName).put(metricName, new ArrayList<>());
          metricGroupValueMap.get(txnName).put(metricName, new ArrayList<>());
          valueList.forEach(value -> { txnMetricValueMap.get(txnName).get(metricName).add(value.getMetricValue()); });
          valueList.forEach(value -> {
            metricGroupValueMap.get(txnName)
                .get(metricName)
                .add(MetricData.builder()
                         .value(value.getMetricValue())
                         .timestamp(value.getTimeStamp().toEpochMilli())
                         .build());
          });
        }
      }

      return TimeSeriesTestDataDTO.builder()
          .cvConfigId(cvConfigId)
          .transactionMetricValues(txnMetricValueMap)
          .metricGroupValues(metricGroupValueMap)
          .build();
    }
    return null;
  }

  @Override
  public List<TimeSeriesRecord> getTimeSeriesRecordsForConfigs(
      List<String> verificationTaskIds, Instant startTime, Instant endTime, boolean anomalousOnly) {
    Query<TimeSeriesRecord> timeSeriesRecordQuery = hPersistence.createQuery(TimeSeriesRecord.class, excludeAuthority)
                                                        .field(TimeSeriesRecordKeys.verificationTaskId)
                                                        .in(verificationTaskIds)
                                                        .field(TimeSeriesRecordKeys.bucketStartTime)
                                                        .greaterThanOrEq(startTime)
                                                        .field(TimeSeriesRecordKeys.bucketStartTime)
                                                        .lessThan(endTime);

    if (anomalousOnly) {
      timeSeriesRecordQuery = timeSeriesRecordQuery
                                  .field(TimeSeriesRecordKeys.timeSeriesGroupValues + "."
                                      + TimeSeriesGroupValue.TimeSeriesValueKeys.riskScore)
                                  .greaterThan(0);
    }
    return timeSeriesRecordQuery.asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }

  @Override
  public void createDemoAnalysisData(String accountId, String verificationTaskId, String dataCollectionWorkerId,
      Instant startTime, Instant endTime, DemoMetricParams demoMetricParams) throws IOException {
    Instant time = startTime;

    String demoTemplatePath = getDemoTemplate(demoMetricParams.getDemoTemplate().getDemoTemplateIdentifier());
    Map<String, ArrayList<Long>> metricToRiskScore =
        getDemoRiskScoreForAllTheMetrics(demoMetricParams.getDemoTemplate().getDemoTemplateIdentifier());
    // todo: check the metrics have the same size
    int index = cvngDemoDataIndexService.readIndexForDemoData(accountId, dataCollectionWorkerId, verificationTaskId);
    List<TimeSeriesDataCollectionRecord> timeSeriesDataCollectionRecords = new ArrayList<>();
    while (time.compareTo(endTime) < 0) {
      TimeSeriesDataCollectionRecord timeSeriesDataCollectionRecord =
          JsonUtils.asObject(demoTemplatePath, new TypeReference<TimeSeriesDataCollectionRecord>() {});
      timeSeriesDataCollectionRecord.setAccountId(accountId);
      timeSeriesDataCollectionRecord.setVerificationTaskId(verificationTaskId);
      timeSeriesDataCollectionRecord.setTimeStamp(time.toEpochMilli());

      for (TimeSeriesDataRecordMetricValue timeSeriesDataRecordMetricValue :
          timeSeriesDataCollectionRecord.getMetricValues()) {
        String metricName = timeSeriesDataRecordMetricValue.getMetricName();
        for (TimeSeriesDataRecordGroupValue timeSeriesDataRecordGroupValue :
            timeSeriesDataRecordMetricValue.getTimeSeriesValues()) {
          String fileName =
              metricName.replace(' ', '_') + "_" + timeSeriesDataRecordGroupValue.getGroupName().replace(' ', '_');
          if (metricToRiskScore.containsKey(fileName)) {
            if (index >= metricToRiskScore.get(fileName).size()) {
              index = index % metricToRiskScore.get(fileName).size();
            }
            timeSeriesDataRecordGroupValue.setValue(
                metricValue(metricToRiskScore.get(fileName).get(index), demoMetricParams.getDemoTemplate()));
          }
        }
      }
      if (demoMetricParams.isCustomMetric()) {
        for (AnalysisInfo analysisInfo : demoMetricParams.getAnalysisInfos()) {
          timeSeriesDataCollectionRecord.getMetricValues().add(
              TimeSeriesDataRecordMetricValue.builder()
                  .metricName(analysisInfo.getMetricName())
                  .metricIdentifier(analysisInfo.getIdentifier())
                  .timeSeriesValues(Collections.singleton(
                      TimeSeriesDataRecordGroupValue.builder()
                          .groupName(demoMetricParams.getGroupName())
                          .value(
                              metricValue(DEMO_DATA.get(index % DEMO_DATA.size()), demoMetricParams.getDemoTemplate()))
                          .build()))
                  .build());
        }
        Set<String> selectedMetricIdentifiers =
            demoMetricParams.getAnalysisInfos().stream().map(AnalysisInfo::getIdentifier).collect(Collectors.toSet());
        timeSeriesDataCollectionRecord.setMetricValues(
            timeSeriesDataCollectionRecord.getMetricValues()
                .stream()
                .filter(metricValue -> selectedMetricIdentifiers.contains(metricValue.getMetricIdentifier()))
                .collect(Collectors.toSet()));
      }
      timeSeriesDataCollectionRecords.add(timeSeriesDataCollectionRecord);
      index++;
      time = time.plus(1, ChronoUnit.MINUTES);
    }
    cvngDemoDataIndexService.saveIndexForDemoData(accountId, dataCollectionWorkerId, verificationTaskId, index);
    save(timeSeriesDataCollectionRecords);
  }

  private double metricValue(long value, DemoTemplate demoTemplate) {
    return demoTemplate.isHighRisk() ? (value + 1) * (new Random().nextInt(20) + 11) : value;
  }

  private String getDemoTemplate(String templateIdentifier) throws IOException {
    log.info("Template identifier: {}", templateIdentifier);
    String path = "/io/harness/cvng/analysis/liveMonitoring/timeSeries/$template/time_series_metrics_def.json";
    path = path.replace("$template", templateIdentifier);
    return Resources.toString(this.getClass().getResource(path), Charsets.UTF_8);
  }

  public Map<String, ArrayList<Long>> getDemoRiskScoreForAllTheMetrics(String templateIdentifier) throws IOException {
    Map<String, ArrayList<Long>> metricToRiskScore = new HashMap<>();
    String folder = "io.harness.cvng.analysis.liveMonitoring.timeSeries." + templateIdentifier + ".riskScore";
    Reflections reflections = new Reflections(folder, new ResourcesScanner());
    Set<String> riskFileNames = reflections.getResources(Pattern.compile(".*\\.json"));
    for (String file : riskFileNames) {
      String riskTemplate = Resources.toString(this.getClass().getClassLoader().getResource(file), Charsets.UTF_8);
      metricToRiskScore.put(file.substring(file.lastIndexOf('/') + 1, file.lastIndexOf('.')),
          JsonUtils.asObject(riskTemplate, new TypeReference<ArrayList<Long>>() {}));
    }
    return metricToRiskScore;
  }
}
