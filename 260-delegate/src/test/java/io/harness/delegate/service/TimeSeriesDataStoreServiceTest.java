/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class TimeSeriesDataStoreServiceTest extends CategoryTest {
  @Spy TimeSeriesDataStoreService timeSeriesDataStoreService;

  String accountId = generateUuid();
  String verificationTaskId = generateUuid();
  int numOfMins = 10;
  int numOfMetrics = 5;
  int numOfTxns = 15;
  int numberOfHosts = 10;

  AutoCloseable mocks;
  @Before
  public void before() {
    mocks = MockitoAnnotations.openMocks(this);
    Mockito.doReturn(true).when(timeSeriesDataStoreService).saveTimeSeriesToCVNG(Mockito.any(), Mockito.any());
  }

  @Test
  @Owner(developers = OwnerRule.RAGHU)
  @Category(UnitTests.class)
  public void testConvertToCollectionRecords() {
    numberOfHosts = 0;
    List<TimeSeriesRecord> timeSeriesRecords = getSampleTimeSeriesRecords();
    Set<String> metrics = getMetricNames(timeSeriesRecords);
    Set<String> txns = getTransactionNames(timeSeriesRecords);
    List<TimeSeriesDataCollectionRecord> dataCollectionRecords =
        timeSeriesDataStoreService.convertToCollectionRecords(accountId, verificationTaskId, timeSeriesRecords);
    System.out.println(dataCollectionRecords);
    assertThat(dataCollectionRecords.size()).isEqualTo(numOfMins);
    Collections.sort(dataCollectionRecords, Comparator.comparingLong(TimeSeriesDataCollectionRecord::getTimeStamp));
    for (int i = 0; i < numOfMins; i++) {
      TimeSeriesDataCollectionRecord dataCollectionRecord = dataCollectionRecords.get(i);
      assertThat(dataCollectionRecord.getTimeStamp()).isEqualTo(TimeUnit.MINUTES.toMillis(i));
      assertThat(dataCollectionRecord.getMetricValues().size()).isEqualTo(numOfMetrics);
      dataCollectionRecord.getMetricValues().forEach(recordMetricValue -> {
        assertThat(metrics).contains(recordMetricValue.getMetricName());
        assertThat(recordMetricValue.getTimeSeriesValues().size()).isEqualTo(numOfTxns);
        recordMetricValue.getTimeSeriesValues().forEach(groupValue -> {
          assertThat(txns).contains(groupValue.getGroupName());
          assertThat(groupValue.getValue()).isEqualTo(10.0, offset(0.001));
        });
      });
    }
  }

  @Test
  @Owner(developers = OwnerRule.ABHIJITH)
  @Category(UnitTests.class)
  public void testTimeSeriesRecordsPartitionedCall() {
    numberOfHosts = 10;
    List<TimeSeriesRecord> timeSeriesRecords = getSampleTimeSeriesRecords();
    ArgumentCaptor<List<TimeSeriesDataCollectionRecord>> dataCollectionCapture = ArgumentCaptor.forClass(List.class);
    timeSeriesDataStoreService.saveTimeSeriesDataRecords(accountId, verificationTaskId, timeSeriesRecords);
    Mockito.verify(timeSeriesDataStoreService, Mockito.times(20))
        .saveTimeSeriesToCVNG(Mockito.any(), dataCollectionCapture.capture());
    List<List<TimeSeriesDataCollectionRecord>> partitionedData = dataCollectionCapture.getAllValues();
    assertThat(partitionedData.get(0).get(0).getHost()).isEqualTo("host-0");
    assertThat(partitionedData.get(0).get(0).getTimeStamp()).isEqualTo(0L);
    assertThat(partitionedData.get(19).get(4).getHost()).isEqualTo("host-9");
    assertThat(partitionedData.get(19).get(4).getTimeStamp()).isEqualTo(540000);
  }

  private List<TimeSeriesRecord> getSampleTimeSeriesRecords() {
    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    for (int i = 0; i < numOfMins; i++) {
      for (int j = 0; j < numOfMetrics; j++) {
        for (int k = 0; k < numOfTxns; k++) {
          if (numberOfHosts <= 0) {
            timeSeriesRecords.add(TimeSeriesRecord.builder()
                                      .txnName("txn-" + k)
                                      .metricName("metric-" + j)
                                      .metricValue(10.0)
                                      .timestamp(TimeUnit.MINUTES.toMillis(i))
                                      .build());
          } else {
            for (int l = 0; l < numberOfHosts; l++) {
              timeSeriesRecords.add(TimeSeriesRecord.builder()
                                        .txnName("txn-" + k)
                                        .metricName("metric-" + j)
                                        .hostname("host-" + l)
                                        .metricValue(10.0)
                                        .timestamp(TimeUnit.MINUTES.toMillis(i))
                                        .build());
            }
          }
        }
      }
    }
    return timeSeriesRecords;
  }

  private Set<String> getMetricNames(List<TimeSeriesRecord> timeSeriesRecords) {
    return timeSeriesRecords.stream()
        .map(timeSeriesRecord -> timeSeriesRecord.getMetricName())
        .collect(Collectors.toSet());
  }

  private Set<String> getTransactionNames(List<TimeSeriesRecord> timeSeriesRecords) {
    return timeSeriesRecords.stream()
        .map(timeSeriesRecord -> timeSeriesRecord.getTxnName())
        .collect(Collectors.toSet());
  }
}
