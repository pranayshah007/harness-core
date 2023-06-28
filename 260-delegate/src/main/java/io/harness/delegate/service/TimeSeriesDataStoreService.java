/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord.TimeSeriesDataRecordGroupValue;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord.TimeSeriesDataRecordMetricValue;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.verificationclient.CVNextGenServiceClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class TimeSeriesDataStoreService {
  @Inject private CVNextGenServiceClient cvNextGenServiceClient;
  @Inject private CVNGRequestExecutor cvngRequestExecutor;

  @VisibleForTesting
  List<TimeSeriesDataCollectionRecord> convertToCollectionRecords(
      String accountId, String verificationTaskId, List<TimeSeriesRecord> timeSeriesRecords) {
    Map<TimeSeriesRecordBucketKey, List<TimeSeriesRecord>> groupByHostAndTimestamp =
        timeSeriesRecords.stream().collect(groupingBy(timeSeriesRecord
            -> TimeSeriesRecordBucketKey.builder()
                   .host(timeSeriesRecord.getHostname())
                   .timestamp(timeSeriesRecord.getTimestamp())
                   .build()));

    List<TimeSeriesDataCollectionRecord> dataCollectionRecords = new ArrayList<>();
    groupByHostAndTimestamp.forEach((timeSeriesRecordBucketKey, timeSeriesRecordsGroup) -> {
      TimeSeriesDataCollectionRecord timeSeriesDataCollectionRecord =
          TimeSeriesDataCollectionRecord.builder()
              .accountId(accountId)
              .verificationTaskId(verificationTaskId)
              .timeStamp(timeSeriesRecordBucketKey.getTimestamp())
              .host(timeSeriesRecordBucketKey.getHost())
              .metricValues(new HashSet<>())
              .build();
      timeSeriesRecordsGroup.stream()
          .collect(groupingBy(TimeSeriesRecord::getMetricName))
          .forEach((metricName, timeSeriesRecordsGroupByMetricName) -> {
            String metricIdentifier = timeSeriesRecordsGroupByMetricName.size() > 0
                    && StringUtils.isNotEmpty(timeSeriesRecordsGroupByMetricName.get(0).getMetricIdentifier())
                ? timeSeriesRecordsGroupByMetricName.get(0).getMetricIdentifier()
                : metricName;
            TimeSeriesDataRecordMetricValue timeSeriesDataRecordMetricValue = TimeSeriesDataRecordMetricValue.builder()
                                                                                  .metricIdentifier(metricIdentifier)
                                                                                  .metricName(metricName)
                                                                                  .timeSeriesValues(new HashSet<>())
                                                                                  .build();
            timeSeriesRecordsGroupByMetricName.forEach(timeSeriesRecord
                -> timeSeriesDataRecordMetricValue.getTimeSeriesValues().add(
                    TimeSeriesDataRecordGroupValue.builder()
                        .groupName(timeSeriesRecord.getTxnName())
                        .value(timeSeriesRecord.getMetricValue())
                        .build()));
            timeSeriesDataCollectionRecord.getMetricValues().add(timeSeriesDataRecordMetricValue);
          });
      dataCollectionRecords.add(timeSeriesDataCollectionRecord);
    });
    return dataCollectionRecords;
  }
  @Value
  @Builder
  private static class TimeSeriesRecordBucketKey {
    String host;
    long timestamp;
  }

  public boolean saveTimeSeriesDataRecords(
      String accountId, String verificationTaskId, List<TimeSeriesRecord> timeSeriesRecords) {
    if (timeSeriesRecords.isEmpty()) {
      log.info(
          "TimeseriesRecords is empty. So we will not be saving anything from the delegate for {}", verificationTaskId);
      return true;
    }
    // adding filter
    List<TimeSeriesRecord> filteredTimeseriesRecords =
        timeSeriesRecords.stream()
            .filter(timeSeriesRecord
                -> timeSeriesRecord.getTimestamp() != null && timeSeriesRecord.getMetricValue() != null)
            .collect(Collectors.toList());
    List<TimeSeriesDataCollectionRecord> dataCollectionRecords =
        convertToCollectionRecords(accountId, verificationTaskId, filteredTimeseriesRecords);
    // Sorting them so that DB Save can be optimised on CVNG Side
    dataCollectionRecords.sort(
        Comparator
            .comparing((TimeSeriesDataCollectionRecord record)
                           -> StringUtils.isNotEmpty(record.getHost()) ? record.getHost() : "host")
            .thenComparing(record -> record.getTimeStamp()));
    // Keeping partition size to be 5 so that most of the calls like for SLI will be single call.
    List<List<TimeSeriesDataCollectionRecord>> partitionedDataCollectionRecordsForSave =
        Lists.partition(dataCollectionRecords, 5);
    return partitionedDataCollectionRecordsForSave.stream()
        .map(timeSeriesDataCollectionRecords -> saveTimeSeriesToCVNG(accountId, timeSeriesDataCollectionRecords))
        .noneMatch(result -> !result);
  }

  @VisibleForTesting
  boolean saveTimeSeriesToCVNG(String accountId, List<TimeSeriesDataCollectionRecord> dataCollectionRecords) {
    return cvngRequestExecutor
        .executeWithRetry(cvNextGenServiceClient.saveTimeSeriesMetrics(accountId, dataCollectionRecords))
        .getResource();
  }
}
