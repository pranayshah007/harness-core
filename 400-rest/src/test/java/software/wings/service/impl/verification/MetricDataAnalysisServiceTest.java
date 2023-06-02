/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.VerificationOperationException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.metrics.MetricType;
import software.wings.metrics.Threshold;
import software.wings.metrics.ThresholdComparisonType;
import software.wings.metrics.ThresholdType;
import software.wings.metrics.TimeSeriesCustomThresholdType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds.TimeSeriesMLTransactionThresholdKeys;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.sm.StateType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

/**
 * Created by rsingh on 9/7/18.
 */
public class MetricDataAnalysisServiceTest extends WingsBaseTest {
  private String accountId;
  private String appId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String cvConfigId;
  private String groupName;
  private String delegateTaskId;
  private Integer analysisMinute;

  @Inject private HPersistence persistence;
  @Inject private MetricDataAnalysisService metricDataAnalysisService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    accountId = generateUuid();
    appId = generateUuid();
    stateExecutionId = generateUuid();
    workflowId = generateUuid();
    workflowExecutionId = generateUuid();
    serviceId = generateUuid();
    cvConfigId = generateUuid();
    groupName = "groupName-";
    delegateTaskId = UUID.randomUUID().toString();
    analysisMinute = 10;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSavingCustomThresholds() {
    int numOfTransactions = 5;
    int numOfMetricsPerTxns = 10;
    for (int i = 0; i < numOfTransactions; i++) {
      for (int j = 0; j < numOfMetricsPerTxns; j++) {
        metricDataAnalysisService.saveCustomThreshold(accountId, appId, StateType.NEW_RELIC, serviceId, cvConfigId,
            "transaction-" + (i * numOfMetricsPerTxns + j), DEFAULT_GROUP_NAME,
            TimeSeriesMetricDefinition.builder()
                .metricName("metric-" + (i * numOfMetricsPerTxns + j))
                .metricType(MetricType.THROUGHPUT)
                .customThresholds(Lists.newArrayList(Threshold.builder()
                                                         .comparisonType(ThresholdComparisonType.DELTA)
                                                         .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                                                         .ml(i * numOfMetricsPerTxns + j)
                                                         .build()))
                .build(),
            null);
      }
    }

    List<TimeSeriesMLTransactionThresholds> thresholds =
        persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
            .filter("appId", this.appId)
            .order(TimeSeriesMLTransactionThresholds.CREATED_AT_KEY)
            .asList();
    assertThat(thresholds).hasSize(numOfTransactions * numOfMetricsPerTxns);
    for (int i = 0; i < numOfTransactions * numOfMetricsPerTxns; i++) {
      TimeSeriesMLTransactionThresholds threshold = thresholds.get(i);
      assertThat(threshold.getServiceId()).isEqualTo(serviceId);
      assertThat(threshold.getStateType()).isEqualTo(StateType.NEW_RELIC);
      assertThat(threshold.getTransactionName()).isEqualTo("transaction-" + i);
      assertThat(threshold.getMetricName()).isEqualTo("metric-" + i);
      assertThat(threshold.getCvConfigId()).isEqualTo(cvConfigId);
      assertThat(threshold.getVersion()).isEqualTo(0);
      assertThat(threshold.getThresholds().getMetricName()).isEqualTo("metric-" + i);
      assertThat(threshold.getThresholds().getMetricType()).isEqualTo(MetricType.THROUGHPUT);
      assertThat(threshold.getThresholds().getCustomThresholds()).hasSize(1);
      assertThat(threshold.getThresholds().getCustomThresholds().get(0).getThresholdType())
          .isEqualTo(ThresholdType.ALERT_WHEN_HIGHER);
      assertThat(threshold.getThresholds().getCustomThresholds().get(0).getComparisonType())
          .isEqualTo(ThresholdComparisonType.DELTA);
      assertThat(threshold.getThresholds().getCustomThresholds().get(0).getMl()).isEqualTo(i);
    }

    // change a few thresholds
    for (int i = 0; i < 10; i++) {
      metricDataAnalysisService.saveCustomThreshold(accountId, appId, StateType.NEW_RELIC, serviceId, cvConfigId,
          "transaction-" + 21, DEFAULT_GROUP_NAME,
          TimeSeriesMetricDefinition.builder()
              .metricName("metric-" + 21)
              .metricType(MetricType.THROUGHPUT)
              .customThresholds(Lists.newArrayList(Threshold.builder()
                                                       .comparisonType(ThresholdComparisonType.DELTA)
                                                       .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                                                       .ml(2100)
                                                       .build()))
              .build(),
          null);
    }

    for (int i = 0; i < 7; i++) {
      metricDataAnalysisService.saveCustomThreshold(accountId, appId, StateType.NEW_RELIC, serviceId, cvConfigId,
          "transaction-" + 37, DEFAULT_GROUP_NAME,
          TimeSeriesMetricDefinition.builder()
              .metricName("metric-" + 37)
              .metricType(MetricType.THROUGHPUT)
              .customThresholds(Lists.newArrayList(Threshold.builder()
                                                       .comparisonType(ThresholdComparisonType.DELTA)
                                                       .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                                                       .ml(3700)
                                                       .build()))
              .build(),
          null);
    }

    thresholds = persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
                     .filter("appId", this.appId)
                     .order(TimeSeriesMLTransactionThresholds.CREATED_AT_KEY)
                     .asList();
    assertThat(thresholds).hasSize(numOfTransactions * numOfMetricsPerTxns);
    for (int i = 0; i < numOfTransactions * numOfMetricsPerTxns; i++) {
      TimeSeriesMLTransactionThresholds threshold = thresholds.get(i);
      assertThat(threshold.getServiceId()).isEqualTo(serviceId);
      assertThat(threshold.getStateType()).isEqualTo(StateType.NEW_RELIC);
      assertThat(threshold.getTransactionName()).isEqualTo("transaction-" + i);
      assertThat(threshold.getMetricName()).isEqualTo("metric-" + i);
      assertThat(threshold.getCvConfigId()).isEqualTo(cvConfigId);
      assertThat(threshold.getThresholds().getMetricName()).isEqualTo("metric-" + i);
      assertThat(threshold.getThresholds().getMetricType()).isEqualTo(MetricType.THROUGHPUT);
      assertThat(threshold.getThresholds().getCustomThresholds()).hasSize(1);
      assertThat(threshold.getThresholds().getCustomThresholds().get(0).getThresholdType())
          .isEqualTo(ThresholdType.ALERT_WHEN_HIGHER);
      assertThat(threshold.getThresholds().getCustomThresholds().get(0).getComparisonType())
          .isEqualTo(ThresholdComparisonType.DELTA);

      if (i == 21) {
        assertThat(threshold.getVersion()).isEqualTo(10);
        assertThat(threshold.getThresholds().getCustomThresholds().get(0).getMl()).isEqualTo(2100);
      } else if (i == 37) {
        assertThat(threshold.getVersion()).isEqualTo(7);
        assertThat(threshold.getThresholds().getCustomThresholds().get(0).getMl()).isEqualTo(3700);
      } else {
        assertThat(threshold.getVersion()).isEqualTo(0);
        assertThat(threshold.getThresholds().getCustomThresholds().get(0).getMl()).isEqualTo(i);
      }
    }
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testDeleteCustomThresholdsWithoutComparisonType() throws Exception {
    TimeSeriesMLTransactionThresholds timeSeriesMLTransactionThresholds =
        TimeSeriesMLTransactionThresholds.builder()
            .stateType(StateType.NEW_RELIC)
            .serviceId(serviceId)
            .cvConfigId(cvConfigId)
            .transactionName("transaction-name")
            .metricName("metric-name")
            .thresholds(TimeSeriesMetricDefinition.builder()
                            .metricName("metric-name")
                            .metricType(MetricType.THROUGHPUT)
                            .customThresholds(Arrays.asList(Threshold.builder()
                                                                .comparisonType(ThresholdComparisonType.DELTA)
                                                                .thresholdType(ThresholdType.ALERT_WHEN_LOWER)
                                                                .ml(10)
                                                                .build()))
                            .build())
            .build();
    timeSeriesMLTransactionThresholds.setAppId(appId);
    persistence.save(timeSeriesMLTransactionThresholds);
    List<TimeSeriesMLTransactionThresholds> thresholds =
        persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
            .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId)
            .asList();

    assertThat(thresholds.size()).isEqualTo(1);
    metricDataAnalysisService.deleteCustomThreshold(
        appId, StateType.NEW_RELIC, serviceId, cvConfigId, null, "transaction-name", "metric-name", null, null);
    thresholds = persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
                     .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId)
                     .asList();
    assertThat(thresholds).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testDeleteCustomThresholds_deleteSpecificTxnmetric() throws Exception {
    metricDataAnalysisService.saveCustomThreshold(serviceId, cvConfigId, createThresholds());
    List<TimeSeriesMLTransactionThresholds> thresholds =
        persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
            .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId)
            .asList();

    assertThat(thresholds.size()).isEqualTo(2);
    assertThat(thresholds.get(0).getTransactionName()).isEqualTo("transaction-name");
    assertThat(thresholds.get(0).getMetricName()).isEqualTo("metric-name");
    assertThat(thresholds.get(1).getTransactionName()).isEqualTo("transaction-name");
    assertThat(thresholds.get(1).getMetricName()).isEqualTo("metric-name");
    assertThat(thresholds.get(0).getThresholds().getCustomThresholds().get(0).getComparisonType())
        .isIn(Arrays.asList(ThresholdComparisonType.RATIO, ThresholdComparisonType.DELTA));
    assertThat(thresholds.get(1).getThresholds().getCustomThresholds().get(0).getComparisonType())
        .isIn(Arrays.asList(ThresholdComparisonType.RATIO, ThresholdComparisonType.DELTA));
    assertThat(thresholds.get(0).getThresholds().getCustomThresholds().get(0).getComparisonType())
        .isNotEqualByComparingTo(thresholds.get(1).getThresholds().getCustomThresholds().get(0).getComparisonType());
    metricDataAnalysisService.deleteCustomThreshold(appId, StateType.NEW_RELIC, serviceId, cvConfigId, null,
        "transaction-name", "metric-name", ThresholdComparisonType.RATIO, null);

    thresholds = persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
                     .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId)
                     .asList();

    assertThat(thresholds.size()).isEqualTo(1);
    assertThat(thresholds.get(0).getThresholds().getCustomThresholds().iterator().next().getComparisonType())
        .isEqualTo(ThresholdComparisonType.DELTA);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_SameTxnMetricThresholdType() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.DELTA);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.DELTA);
    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_BadRatioValue() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.RATIO);
    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setMl(-1.0);

    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_TooManyDeviationThresholds() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.DELTA);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.DELTA);

    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_TooManyDeviationAnomalousThresholds() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.DELTA);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.DELTA);
    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setCustomThresholdType(
        TimeSeriesCustomThresholdType.ANOMALOUS);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setCustomThresholdType(
        TimeSeriesCustomThresholdType.ANOMALOUS);

    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_TooManyAbsoluteAnomalousThresholds() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    TimeSeriesMLTransactionThresholds third = timeSeriesMLTransactionThresholds.get(0).cloneWithoutCustomThresholds();
    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);
    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setCustomThresholdType(
        TimeSeriesCustomThresholdType.ANOMALOUS);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setCustomThresholdType(
        TimeSeriesCustomThresholdType.ANOMALOUS);
    third.setThresholds(timeSeriesMLTransactionThresholds.get(0).getThresholds());

    timeSeriesMLTransactionThresholds.add(third);
    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_SwitchedHigherAndLowerValues() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);

    Threshold upper = Threshold.builder()
                          .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                          .comparisonType(ThresholdComparisonType.ABSOLUTE)
                          .thresholdType(ThresholdType.ALERT_WHEN_LOWER)
                          .ml(10)
                          .build();

    Threshold lower = Threshold.builder()
                          .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                          .comparisonType(ThresholdComparisonType.ABSOLUTE)
                          .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                          .ml(5)
                          .build();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().setCustomThresholds(Arrays.asList(upper));
    timeSeriesMLTransactionThresholds.get(1).getThresholds().setCustomThresholds(Arrays.asList(lower));
    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_sameCriteriaDiffValues() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);
    timeSeriesMLTransactionThresholds.get(1).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);

    Threshold upper = Threshold.builder()
                          .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                          .comparisonType(ThresholdComparisonType.ABSOLUTE)
                          .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                          .ml(10)
                          .build();

    Threshold lower = Threshold.builder()
                          .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                          .comparisonType(ThresholdComparisonType.ABSOLUTE)
                          .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                          .ml(5)
                          .build();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().setCustomThresholds(Arrays.asList(upper));
    timeSeriesMLTransactionThresholds.get(1).getThresholds().setCustomThresholds(Arrays.asList(lower));
    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidThreshold_onlyHigherAbsoluteValue() {
    List<TimeSeriesMLTransactionThresholds> timeSeriesMLTransactionThresholds = createThresholds();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().getCustomThresholds().get(0).setComparisonType(
        ThresholdComparisonType.ABSOLUTE);
    timeSeriesMLTransactionThresholds.remove(1);

    Threshold upper = Threshold.builder()
                          .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                          .comparisonType(ThresholdComparisonType.ABSOLUTE)
                          .thresholdType(ThresholdType.ALERT_WHEN_LOWER)
                          .ml(10)
                          .build();

    timeSeriesMLTransactionThresholds.get(0).getThresholds().setCustomThresholds(Arrays.asList(upper));

    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, timeSeriesMLTransactionThresholds);

    List<TimeSeriesMLTransactionThresholds> thresholdsInDB =
        persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
            .filter(TimeSeriesMLTransactionThresholdKeys.customThresholdRefId,
                timeSeriesMLTransactionThresholds.get(0).getCustomThresholdRefId())
            .asList();
    assertThat(thresholdsInDB.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testBulkDeleteForCustomThresholdRefId_HappyCase() {
    List<TimeSeriesMLTransactionThresholds> thresholds = createThresholds();
    metricDataAnalysisService.saveCustomThreshold(null, cvConfigId, thresholds);
    List<TimeSeriesMLTransactionThresholds> thresholdsInDB =
        persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
            .filter(
                TimeSeriesMLTransactionThresholdKeys.customThresholdRefId, thresholds.get(0).getCustomThresholdRefId())
            .asList();

    assertThat(thresholdsInDB.size()).isEqualTo(2);

    metricDataAnalysisService.bulkDeleteCustomThreshold(thresholds.get(0).getCustomThresholdRefId());
    thresholdsInDB = persistence.createQuery(TimeSeriesMLTransactionThresholds.class)
                         .filter(TimeSeriesMLTransactionThresholdKeys.customThresholdRefId,
                             thresholds.get(0).getCustomThresholdRefId())
                         .asList();
    assertThat(thresholdsInDB).isEmpty();
  }

  private List<TimeSeriesMLTransactionThresholds> createThresholds() {
    String customThresholdRefId = generateUuid();
    TimeSeriesMLTransactionThresholds timeSeriesMLTransactionThresholds =
        TimeSeriesMLTransactionThresholds.builder()
            .customThresholdRefId(customThresholdRefId)
            .stateType(StateType.NEW_RELIC)
            .serviceId(serviceId)
            .cvConfigId(cvConfigId)
            .transactionName("transaction-name")
            .metricName("metric-name")
            .thresholds(
                TimeSeriesMetricDefinition.builder()
                    .metricName("metric-name")
                    .metricType(MetricType.THROUGHPUT)
                    .customThresholds(Arrays.asList(Threshold.builder()
                                                        .comparisonType(ThresholdComparisonType.DELTA)
                                                        .thresholdType(ThresholdType.ALERT_WHEN_LOWER)
                                                        .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                                                        .ml(10)
                                                        .build()))
                    .build())
            .build();
    timeSeriesMLTransactionThresholds.setAppId(appId);

    TimeSeriesMLTransactionThresholds timeSeriesMLTransactionThresholds2 =
        TimeSeriesMLTransactionThresholds.builder()
            .customThresholdRefId(customThresholdRefId)
            .stateType(StateType.NEW_RELIC)
            .serviceId(serviceId)
            .cvConfigId(cvConfigId)
            .transactionName("transaction-name")
            .metricName("metric-name")
            .thresholds(
                TimeSeriesMetricDefinition.builder()
                    .metricName("metric-name")
                    .metricType(MetricType.THROUGHPUT)
                    .customThresholds(Arrays.asList(Threshold.builder()
                                                        .comparisonType(ThresholdComparisonType.RATIO)
                                                        .thresholdType(ThresholdType.ALERT_WHEN_LOWER)
                                                        .customThresholdType(TimeSeriesCustomThresholdType.ACCEPTABLE)
                                                        .ml(10)
                                                        .build()))
                    .build())
            .build();
    timeSeriesMLTransactionThresholds2.setAppId(appId);

    return Lists.newArrayList(timeSeriesMLTransactionThresholds, timeSeriesMLTransactionThresholds2);
  }
}
