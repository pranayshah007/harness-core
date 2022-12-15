/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns.TimeSeriesAnomalies;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TimeSeriesAnomalousPatternsTest extends CategoryTest {
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void convertFromMap() {
    List<TimeSeriesAnomalies> anomalies = TimeSeriesAnomalousPatterns.convertFromMap(buildAnomMap());
    assertThat(anomalies).isNotNull();
    assertThat(anomalies.size()).isEqualTo(9);

    Set<String> txnMetricsPresent = new HashSet<>();

    anomalies.forEach(anomaly -> {
      txnMetricsPresent.add(anomaly.getTransactionName() + "," + anomaly.getMetricIdentifier());
      assertThat(anomaly.getTestData().containsAll(Arrays.asList(0.1, 0.2, 0.3, 0.4))).isTrue();
      assertThat(anomaly.getAnomalousTimestamps().containsAll(Arrays.asList(12345l, 12346l, 12347l))).isTrue();
    });
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");
    transactions.forEach(
        txn -> { metricList.forEach(metric -> assertThat(txnMetricsPresent.contains(txn + "," + metric)).isTrue()); });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void convertFromMap_null() {
    List<TimeSeriesAnomalies> anomalies = TimeSeriesAnomalousPatterns.convertFromMap(null);
    assertThat(anomalies).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void convertToMap_null() {
    TimeSeriesAnomalousPatterns anomalousPatterns = TimeSeriesAnomalousPatterns.builder().anomalies(null).build();

    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> anomMap = anomalousPatterns.convertToMap();
    assertThat(anomMap.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void convertToMap() {
    List<TimeSeriesAnomalies> timeSeriesAnomaliesList =
        buildAnomList().stream().map(TimeSeriesAnomaliesDTO::toTimeSeriesAnomalies).collect(Collectors.toList());
    TimeSeriesAnomalousPatterns anomalousPatterns =
        TimeSeriesAnomalousPatterns.builder().anomalies(timeSeriesAnomaliesList).build();

    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> anomMap = anomalousPatterns.convertToMap();
    assertThat(anomMap.size()).isEqualTo(3);
    assertThat(anomMap.containsKey("txn1")).isTrue();
    assertThat(anomMap.containsKey("txn2")).isTrue();
    assertThat(anomMap.containsKey("txn3")).isTrue();

    for (String txn : anomMap.keySet()) {
      Map<String, List<TimeSeriesAnomaliesDTO>> metricSumsMap = anomMap.get(txn);
      assertThat(metricSumsMap.size()).isEqualTo(3);
      assertThat(metricSumsMap.containsKey("metric1")).isTrue();
      assertThat(metricSumsMap.containsKey("metric2")).isTrue();
      assertThat(metricSumsMap.containsKey("metric3")).isTrue();
      List<TimeSeriesAnomaliesDTO> anom = metricSumsMap.get("metric1");
      assertThat(anom.size()).isEqualTo(1);
    }
  }

  private List<TimeSeriesAnomaliesDTO> buildAnomList() {
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");
    List<TimeSeriesAnomaliesDTO> anomList = new ArrayList<>();
    transactions.forEach(txn -> {
      metricList.forEach(metric -> {
        TimeSeriesAnomaliesDTO anomalies = TimeSeriesAnomaliesDTO.builder()
                                               .transactionName(txn)
                                               .metricIdentifier(metric)
                                               .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                               .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                               .build();
        anomList.add(anomalies);
      });
    });
    return anomList;
  }

  private Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> buildAnomMap() {
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");
    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> anomMap = new HashMap<>();

    transactions.forEach(txn -> {
      anomMap.put(txn, new HashMap<>());
      metricList.forEach(metric -> {
        Map<String, List<TimeSeriesAnomaliesDTO>> metricMap = anomMap.get(txn);
        metricMap.put(metric,
            Arrays.asList(TimeSeriesAnomaliesDTO.builder()
                              .transactionName(txn)
                              .metricIdentifier(metric)
                              .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                              .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                              .build()));
      });
    });
    return anomMap;
  }
}
