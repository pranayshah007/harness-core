/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory.MetricHistory;
import static io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory.TransactionMetricHistory;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TimeSeriesShortTermHistoryTest extends CategoryTest {
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConvertFromMap() {
    List<TransactionMetricHistory> transactionMetricHistoryList =
        TimeSeriesShortTermHistory.convertFromMap(buildShortTermHistoryMap());

    assertThat(transactionMetricHistoryList).isNotNull();
    assertThat(transactionMetricHistoryList.size()).isEqualTo(3);

    transactionMetricHistoryList.forEach(transactionMetricSum -> {
      assertThat(Arrays.asList("txn1", "txn2", "txn3").contains(transactionMetricSum.getTransactionName())).isTrue();
      List<MetricHistory> metricSumsList = transactionMetricSum.getMetricHistoryList();
      assertThat(metricSumsList.size()).isEqualTo(3);
      metricSumsList.forEach(metricSums -> {
        assertThat(Arrays.asList("metric1", "metric2", "metric3").contains(metricSums.getMetricIdentifier())).isTrue();
        List<Double> sums = metricSums.getValue();
        assertThat(sums.containsAll(Arrays.asList(0.1, 0.2, 0.3, 0.4))).isTrue();
      });
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConvertFromMap_null() {
    List<TransactionMetricHistory> transactionMetricHistoryList = TimeSeriesShortTermHistory.convertFromMap(null);

    assertThat(transactionMetricHistoryList).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConvertToMap_null() {
    TimeSeriesShortTermHistory shortTermHistory = TimeSeriesShortTermHistory.builder()
                                                      .verificationTaskId(generateUuid())
                                                      .transactionMetricHistories(null)
                                                      .build();

    Map<String, Map<String, List<Double>>> map = shortTermHistory.convertToMap();
    assertThat(map.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConvertToMap() {
    TimeSeriesShortTermHistory shortTermHistory = TimeSeriesShortTermHistory.builder()
                                                      .verificationTaskId(generateUuid())
                                                      .transactionMetricHistories(buildShortTermHistory())
                                                      .build();

    Map<String, Map<String, List<Double>>> map = shortTermHistory.convertToMap();

    assertThat(map.size()).isEqualTo(3);
    assertThat(map.containsKey("txn1")).isTrue();
    assertThat(map.containsKey("txn2")).isTrue();
    assertThat(map.containsKey("txn3")).isTrue();

    for (String txn : map.keySet()) {
      Map<String, List<Double>> metricSumsMap = map.get(txn);
      assertThat(metricSumsMap.size()).isEqualTo(3);
      assertThat(metricSumsMap.containsKey("metric1")).isTrue();
      assertThat(metricSumsMap.containsKey("metric2")).isTrue();
      assertThat(metricSumsMap.containsKey("metric3")).isTrue();
      List<Double> sums = metricSumsMap.get("metric1");
      assertThat(sums.containsAll(Arrays.asList(0.1, 0.2, 0.3, 0.4))).isTrue();
    }
  }

  private List<TransactionMetricHistory> buildShortTermHistory() {
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");

    List<TransactionMetricHistory> shortTermHistoryList = new ArrayList<>();

    transactions.forEach(txn -> {
      TransactionMetricHistory transactionMetricHistory =
          TransactionMetricHistory.builder().transactionName(txn).metricHistoryList(new ArrayList<>()).build();
      metricList.forEach(metric -> {
        MetricHistory metricHistory =
            MetricHistory.builder().metricName(metric).value(Arrays.asList(0.1, 0.2, 0.3, 0.4)).build();
        transactionMetricHistory.getMetricHistoryList().add(metricHistory);
      });
      shortTermHistoryList.add(transactionMetricHistory);
    });
    return shortTermHistoryList;
  }

  private Map<String, Map<String, List<Double>>> buildShortTermHistoryMap() {
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");

    Map<String, Map<String, List<Double>>> shortTermHistoryMap = new HashMap<>();
    transactions.forEach(txn -> {
      shortTermHistoryMap.put(txn, new HashMap<>());
      metricList.forEach(metric -> {
        Map<String, List<Double>> metricMap = shortTermHistoryMap.get(txn);
        metricMap.put(metric, Arrays.asList(0.1, 0.2, 0.3, 0.4));
      });
    });
    return shortTermHistoryMap;
  }
}
