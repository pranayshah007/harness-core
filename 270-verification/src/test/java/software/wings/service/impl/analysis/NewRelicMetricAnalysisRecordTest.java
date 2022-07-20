/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricAnalysis;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisValue;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NewRelicMetricAnalysisRecordTest extends WingsBaseTest {
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSortMetricAnalysisWithRisk() {
    NewRelicMetricAnalysis txn1 =
        NewRelicMetricAnalysis.builder()
            .metricName(generateUuid())
            .riskLevel(RiskLevel.HIGH)
            .metricValues(Arrays.asList(NewRelicMetricAnalysisValue.builder().testValue(10.0).build()))
            .build();
    NewRelicMetricAnalysis txn2 =
        NewRelicMetricAnalysis.builder()
            .metricName(generateUuid())
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(Arrays.asList(NewRelicMetricAnalysisValue.builder().testValue(15.0).build()))
            .build();

    assertThat(txn1.compareTo(txn2)).isLessThan(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSortMetricAnalysis_whenEmptyMetricAnalysis() {
    NewRelicMetricAnalysis txn1 =
        NewRelicMetricAnalysis.builder()
            .metricName(generateUuid())
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(Arrays.asList(NewRelicMetricAnalysisValue.builder().testValue(10.0).build()))
            .build();
    NewRelicMetricAnalysis txn2 =
        NewRelicMetricAnalysis.builder().metricName(generateUuid()).riskLevel(RiskLevel.MEDIUM).build();

    assertThat(txn1.compareTo(txn2)).isLessThan(0);
    assertThat(txn2.compareTo(txn1)).isGreaterThan(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSortMetricAnalysis_whenAllEmptyMetricAnalysis() {
    NewRelicMetricAnalysis txn1 =
        NewRelicMetricAnalysis.builder().metricName("txn1").riskLevel(RiskLevel.MEDIUM).build();
    NewRelicMetricAnalysis txn2 =
        NewRelicMetricAnalysis.builder().metricName("txn2").riskLevel(RiskLevel.MEDIUM).build();

    assertThat(txn1.compareTo(txn2)).isLessThan(0);
    assertThat(txn2.compareTo(txn1)).isGreaterThan(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSortMetricAnalysis_whenNonSpecifiedOrder() {
    NewRelicMetricAnalysis txn1 =
        NewRelicMetricAnalysis.builder()
            .metricName("txn1")
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(
                Arrays.asList(NewRelicMetricAnalysisValue.builder().name(generateUuid()).testValue(10.0).build()))
            .build();
    NewRelicMetricAnalysis txn2 =
        NewRelicMetricAnalysis.builder()
            .metricName("txn2")
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(
                Arrays.asList(NewRelicMetricAnalysisValue.builder().name(generateUuid()).testValue(10.0).build()))
            .build();

    assertThat(txn1.compareTo(txn2)).isLessThan(0);
    assertThat(txn2.compareTo(txn1)).isGreaterThan(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSortMetricAnalysis_whenOneSpecifiedOrder() {
    NewRelicMetricAnalysis txn1 =
        NewRelicMetricAnalysis.builder()
            .metricName("txn1")
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(
                Arrays.asList(NewRelicMetricAnalysisValue.builder().name(generateUuid()).testValue(10.0).build()))
            .build();
    NewRelicMetricAnalysis txn2 =
        NewRelicMetricAnalysis.builder()
            .metricName("txn2")
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(Arrays.asList(NewRelicMetricAnalysisValue.builder()
                                            .name(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE)
                                            .testValue(10.0)
                                            .build()))
            .build();

    assertThat(txn1.compareTo(txn2)).isGreaterThan(0);
    assertThat(txn2.compareTo(txn1)).isLessThan(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSortMetricAnalysis_whenBothSpecifiedOrder() {
    NewRelicMetricAnalysis txn1 =
        NewRelicMetricAnalysis.builder()
            .metricName("txn1")
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(Arrays.asList(NewRelicMetricAnalysisValue.builder()
                                            .name(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE)
                                            .testValue(15.0)
                                            .build()))
            .build();
    NewRelicMetricAnalysis txn2 =
        NewRelicMetricAnalysis.builder()
            .metricName("txn2")
            .riskLevel(RiskLevel.MEDIUM)
            .metricValues(Arrays.asList(NewRelicMetricAnalysisValue.builder()
                                            .name(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE)
                                            .testValue(10.0)
                                            .build()))
            .build();

    assertThat(txn1.compareTo(txn2)).isLessThan(0);
    assertThat(txn2.compareTo(txn1)).isGreaterThan(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMetricAnalysisSorted() throws IOException {
    File file = new File("270-verification/src/test/resources/verification/newrelic_metric_analysis_sorting.json");
    final Gson gson = new Gson();
    List<NewRelicMetricAnalysis> metricDataRecords;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<List<NewRelicMetricAnalysis>>() {}.getType();
      metricDataRecords = gson.fromJson(br, type);
    }
    for (int i = 0; i < metricDataRecords.size() - 2; i++) {
      for (int j = i + 1; j < metricDataRecords.size() - 1; j++) {
        for (int k = j + 1; k < metricDataRecords.size(); k++) {
          NewRelicMetricAnalysis txn1 = metricDataRecords.get(i);
          NewRelicMetricAnalysis txn2 = metricDataRecords.get(j);
          NewRelicMetricAnalysis txn3 = metricDataRecords.get(k);
          int ab = txn1.compareTo(txn2);
          int bc = txn2.compareTo(txn3);
          int ac = txn1.compareTo(txn3);

          // a < b, b < c ==> a < c
          if (ab < 0 && bc < 0) {
            assertThat(ac).withFailMessage("failed for %s %s %s", i, j, k).isLessThan(0);
          }

          // a < b, b = c ==> a < c
          if (ab < 0 && bc == 0) {
            assertThat(ac).withFailMessage("failed for %s %s %s", i, j, k).isLessThan(0);
          }

          // a>b, b>c ==> a > c
          if (ab > 0 && bc > 0) {
            assertThat(ac).withFailMessage("failed for %s %s %s", i, j, k).isGreaterThan(0);
          }

          // a>b, b = c ==> a > c
          if (ab > 0 && bc > 0) {
            assertThat(ac).withFailMessage("failed for %s %s %s", i, j, k).isGreaterThan(0);
          }
        }
      }
    }
    Collections.sort(metricDataRecords);
  }
}
