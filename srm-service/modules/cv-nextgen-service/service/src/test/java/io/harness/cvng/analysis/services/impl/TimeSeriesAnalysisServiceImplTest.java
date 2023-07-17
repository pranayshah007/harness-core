/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_DATA_LENGTH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_WINDOW_SIZE;
import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.NAVEEN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO.ServiceGuardTimeSeriesAnalysisDTOBuilder;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask_v2;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesLoadTestLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary.TransactionMetricRisk;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.entities.TimeSeriesRecord.TimeSeriesRecordKeys;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TimeSeriesAnalysisServiceImplTest extends CvNextGenTestBase {
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private CVConfigService cvConfigService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private HeatMapService heatMapService;
  @Inject private MetricPackService metricPackService;

  private String cvConfigId;
  private String verificationTaskId;
  private String learningEngineTaskId;
  private String accountId;
  private long deploymentStartTimeMs;
  private Instant instant;
  private BuilderFactory builderFactory;
  static List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    instant = Instant.parse("2020-07-27T10:44:06.390Z");
    deploymentStartTimeMs = instant.toEpochMilli();
    CVConfig cvConfig = newCVConfig();
    cvConfigService.save(cvConfig);
    cvConfigId = cvConfig.getUuid();
    verificationTaskId = verificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId);

    TimeSeriesLearningEngineTask timeSeriesLearningEngineTask = TimeSeriesLearningEngineTask.builder().build();
    timeSeriesLearningEngineTask.setVerificationTaskId(verificationTaskId);
    timeSeriesLearningEngineTask.setAnalysisStartTime(Instant.now());
    timeSeriesLearningEngineTask.setAnalysisEndTime(Instant.now().plus(Duration.ofMinutes(5)));
    timeSeriesLearningEngineTask.setPickedAt(Instant.now().plus(Duration.ofMinutes(2)));
    timeSeriesLearningEngineTask.setWindowSize(5);
    learningEngineTaskId = learningEngineTaskService.createLearningEngineTask(timeSeriesLearningEngineTask);

    FieldUtils.writeField(timeSeriesAnalysisService, "heatMapService", heatMapService, true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testScheduleServiceGuardAnalysis() {
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES))
                              .endTime(TIME_FOR_TESTS)
                              .build();
    List<String> taskIds = timeSeriesAnalysisService.scheduleServiceGuardAnalysis(input);

    assertThat(taskIds).isNotNull();

    assertThat(taskIds.size()).isEqualTo(1);

    TimeSeriesLearningEngineTask task =
        (TimeSeriesLearningEngineTask) hPersistence.get(LearningEngineTask.class, taskIds.get(0));
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES.name());
    assertThat(task.getDataLength()).isEqualTo(TIMESERIES_SERVICE_GUARD_DATA_LENGTH);
    assertThat(task.getWindowSize()).isEqualTo(TIMESERIES_SERVICE_GUARD_WINDOW_SIZE);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetCumulativeSums() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant end = Instant.now().minus(5, ChronoUnit.MINUTES);
    TimeSeriesCumulativeSums cumulativeSums = TimeSeriesCumulativeSums.builder()
                                                  .verificationTaskId(verificationTaskId)
                                                  .analysisStartTime(start)
                                                  .analysisEndTime(end)
                                                  .transactionMetricSums(buildTransactionMetricSums())
                                                  .build();

    hPersistence.save(cumulativeSums);
    TimeSeriesCumulativeSums cumulativeSums2 = TimeSeriesCumulativeSums.builder()
                                                   .verificationTaskId(verificationTaskId)
                                                   .analysisStartTime(start.minus(5, ChronoUnit.MINUTES))
                                                   .analysisEndTime(end.minus(5, ChronoUnit.MINUTES))
                                                   .transactionMetricSums(buildTransactionMetricSums())
                                                   .build();
    hPersistence.save(cumulativeSums2);
    Map<String, Map<String, List<ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO>>> actual =
        timeSeriesAnalysisService.getCumulativeSums(verificationTaskId, start.minus(10, ChronoUnit.MINUTES), end);
    Map<String, Map<String, List<ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO>>> expected =
        TimeSeriesCumulativeSums.convertToMap(Lists.newArrayList(cumulativeSums, cumulativeSums2));
    expected.forEach((key, map) -> {
      assertThat(actual.containsKey(key));
      map.forEach((metric, cumsum) -> {
        assertThat(actual.get(key).containsKey(metric));
        assertThat(cumsum).isEqualTo(actual.get(key).get(metric));
      });
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTestData() throws Exception {
    List<TimeSeriesRecord> records = getTimeSeriesRecords();
    hPersistence.save(records);
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    List<TimeSeriesRecordDTO> testData =
        timeSeriesAnalysisService.getTimeSeriesRecordDTOs(cvConfigId, start, start.plus(5, ChronoUnit.MINUTES));

    assertThat(testData).isNotNull();
    assertThat(testData.size()).isEqualTo(183);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetDeploymentMetricTimeSeriesRecordDTOs() throws Exception {
    List<TimeSeriesRecord> records = getTimeSeriesRecords();
    hPersistence.save(records);
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    String hosts = "host1,host2";
    List<TimeSeriesRecordDTO> testData = timeSeriesAnalysisService.getDeploymentMetricTimeSeriesRecordDTOs(
        cvConfigId, start, start.plus(5, ChronoUnit.MINUTES), hosts);
    List<TimeSeriesRecordDTO> filteredTestData =
        testData.stream().filter(t -> hosts.contains(t.getHost())).collect(Collectors.toList());
    assertThat(testData.size()).isEqualTo(filteredTestData.size());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLongTermAnomalies() {
    TimeSeriesAnomalousPatterns patterns = TimeSeriesAnomalousPatterns.builder()
                                               .verificationTaskId(verificationTaskId)
                                               .anomalies(buildAnomList()
                                                              .stream()
                                                              .map(TimeSeriesAnomaliesDTO::toTimeSeriesAnomalies)
                                                              .collect(Collectors.toList()))
                                               .uuid("patternsUuid")
                                               .build();
    hPersistence.save(patterns);

    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> actual =
        timeSeriesAnalysisService.getLongTermAnomalies(verificationTaskId);
    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> expected = patterns.convertToMap();
    expected.forEach((key, map) -> {
      assertThat(actual.containsKey(key));
      map.forEach((metric, anomList) -> {
        assertThat(actual.get(key).containsKey(metric));
        assertThat(anomList).isEqualTo(actual.get(key).get(metric));
      });
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLongTermAnomalies_noPreviousAnoms() {
    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> actual =
        timeSeriesAnalysisService.getLongTermAnomalies(cvConfigId);
    assertThat(actual).isNotNull();
    assertThat(actual.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetShortTermHistory() {
    TimeSeriesShortTermHistory shortTermHistory = TimeSeriesShortTermHistory.builder()
                                                      .verificationTaskId(verificationTaskId)
                                                      .transactionMetricHistories(buildShortTermHistory())
                                                      .build();
    timeSeriesAnalysisService.saveShortTermHistory(shortTermHistory);
    Map<String, Map<String, List<Double>>> actual = timeSeriesAnalysisService.getShortTermHistory(verificationTaskId);
    Map<String, Map<String, List<Double>>> expected = shortTermHistory.convertToMap();

    expected.forEach((key, map) -> {
      assertThat(actual.containsKey(key));
      map.forEach((metric, history) -> {
        assertThat(actual.get(key).containsKey(metric));
        assertThat(history).isEqualTo(actual.get(key).get(metric));
      });
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetShortTermHistory_noPreviousHistory() {
    Map<String, Map<String, List<Double>>> actual = timeSeriesAnalysisService.getShortTermHistory(verificationTaskId);
    assertThat(actual).isNotNull();
    assertThat(actual.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveAnalysis_serviceGuard() {
    timeSeriesAnalysisService.saveAnalysis(
        learningEngineTaskId, generateServiceGuardMetricAnalysisDTOBuilder().build());

    TimeSeriesCumulativeSums cumulativeSums =
        hPersistence.createQuery(TimeSeriesCumulativeSums.class).filter("verificationTaskId", verificationTaskId).get();
    assertThat(cumulativeSums).isNotNull();
    TimeSeriesAnomalousPatterns anomalousPatterns = hPersistence.createQuery(TimeSeriesAnomalousPatterns.class)
                                                        .filter("verificationTaskId", verificationTaskId)
                                                        .get();
    assertThat(anomalousPatterns).isNotNull();
    TimeSeriesShortTermHistory shortTermHistory = hPersistence.createQuery(TimeSeriesShortTermHistory.class)
                                                      .filter("verificationTaskId", verificationTaskId)
                                                      .get();
    assertThat(shortTermHistory).isNotNull();
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(
        heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getAnomalousMetricsCount()).isEqualTo(9));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testSaveAnalysis_serviceGuardNoData() {
    ServiceGuardTimeSeriesAnalysisDTO serviceGuardTimeSeriesAnalysisDTO =
        generateServiceGuardMetricAnalysisDTOBuilder().overallMetricScores(new HashMap<>()).build();
    timeSeriesAnalysisService.saveAnalysis(learningEngineTaskId, serviceGuardTimeSeriesAnalysisDTO);

    TimeSeriesCumulativeSums cumulativeSums =
        hPersistence.createQuery(TimeSeriesCumulativeSums.class).filter("verificationTaskId", verificationTaskId).get();
    assertThat(cumulativeSums).isNotNull();
    TimeSeriesAnomalousPatterns anomalousPatterns = hPersistence.createQuery(TimeSeriesAnomalousPatterns.class)
                                                        .filter("verificationTaskId", verificationTaskId)
                                                        .get();
    assertThat(anomalousPatterns).isNotNull();
    TimeSeriesShortTermHistory shortTermHistory = hPersistence.createQuery(TimeSeriesShortTermHistory.class)
                                                      .filter("verificationTaskId", verificationTaskId)
                                                      .get();
    assertThat(shortTermHistory).isNotNull();
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getRiskScore()).isEqualTo(-2.0));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysis_serviceGuard_withoutCumulativeSums() {
    timeSeriesAnalysisService.saveAnalysis(
        learningEngineTaskId, buildServiceGuardMetricAnalysisDTO_emptyCumulativeSums(verificationTaskId));

    TimeSeriesCumulativeSums cumulativeSums =
        hPersistence.createQuery(TimeSeriesCumulativeSums.class).filter("verificationTaskId", verificationTaskId).get();
    assertThat(cumulativeSums).isNotNull();
    assertThat(TimeSeriesCumulativeSums.convertToMap(Collections.singletonList(cumulativeSums))).isEmpty();
    TimeSeriesAnomalousPatterns anomalousPatterns = hPersistence.createQuery(TimeSeriesAnomalousPatterns.class)
                                                        .filter("verificationTaskId", verificationTaskId)
                                                        .get();
    assertThat(anomalousPatterns).isNotNull();
    TimeSeriesShortTermHistory shortTermHistory = hPersistence.createQuery(TimeSeriesShortTermHistory.class)
                                                      .filter("verificationTaskId", verificationTaskId)
                                                      .get();
    assertThat(shortTermHistory).isNotNull();
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(
        heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getAnomalousMetricsCount()).isEqualTo(9));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSaveAnalysis_serviceGuard_customerDefinedUnhealthy() throws Exception {
    List<TimeSeriesRecord> timeSeriesRecords = getTimeSeriesRecords_forServiceGuard();
    hPersistence.save(timeSeriesRecords);
    timeSeriesAnalysisService.saveAnalysis(
        learningEngineTaskId, generateServiceGuardMetricAnalysisDTOBuilder_customerDefinedUnhealthy().build());

    TimeSeriesAnomalousPatterns anomalousPatterns = hPersistence.createQuery(TimeSeriesAnomalousPatterns.class)
                                                        .filter("verificationTaskId", verificationTaskId)
                                                        .get();
    assertThat(anomalousPatterns).isNotNull();
    TimeSeriesShortTermHistory shortTermHistory = hPersistence.createQuery(TimeSeriesShortTermHistory.class)
                                                      .filter("verificationTaskId", verificationTaskId)
                                                      .get();
    assertThat(shortTermHistory).isNotNull();
    List<TimeSeriesRecord> timeSeriesRecords1 = hPersistence.createQuery(TimeSeriesRecord.class)
                                                    .filter(TimeSeriesRecordKeys.verificationTaskId, verificationTaskId)
                                                    .field(TimeSeriesRecordKeys.metricIdentifier)
                                                    .in(metricList)
                                                    .asList();
    timeSeriesRecords1.forEach(timeSeriesRecord
        -> assertThat(timeSeriesRecord.getTimeSeriesGroupValues().iterator().next().getRiskScore())
               .isEqualTo(Risk.CUSTOMER_DEFINED_UNHEALTHY.getValue()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveAnalysis_deploymentVerification() {
    timeSeriesAnalysisService.saveAnalysis(learningEngineTaskId, buildDeploymentVerificationDTO());
    List<DeploymentTimeSeriesAnalysis> results =
        deploymentTimeSeriesAnalysisService.getAnalysisResults(verificationTaskId);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getVerificationTaskId()).isEqualTo(verificationTaskId);
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    assertThat(heatMaps.size()).isEqualTo(0);
  }

  private ServiceGuardTimeSeriesAnalysisDTOBuilder generateServiceGuardMetricAnalysisDTOBuilder() {
    Map<String, Double> overallMetricScores = new HashMap<>();
    overallMetricScores.put("Errors per Minute", 0.872);
    overallMetricScores.put("Average Response Time", 0.212);
    overallMetricScores.put("Calls Per Minute", 0.0);

    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricMap = new HashMap<>();
    transactions.forEach(txn -> {
      txnMetricMap.put(txn, new HashMap<>());
      metricList.forEach(metric -> {
        Map<String, ServiceGuardTxnMetricAnalysisDataDTO> metricMap = txnMetricMap.get(txn);
        ServiceGuardTxnMetricAnalysisDataDTO txnMetricData =
            ServiceGuardTxnMetricAnalysisDataDTO.builder()
                .isKeyTransaction(false)
                .cumulativeSums(ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO.builder().risk(0.5).data(0.9).build())
                .shortTermHistory(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                .anomalousPatterns(Arrays.asList(TimeSeriesAnomaliesDTO.builder()
                                                     .transactionName(txn)
                                                     .metricIdentifier(metric)
                                                     .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                     .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                                     .build()))
                .lastSeenTime(0)
                .metricType(TimeSeriesMetricType.ERROR)
                .risk(2)
                .build();
        metricMap.put(metric, txnMetricData);
      });
    });

    return ServiceGuardTimeSeriesAnalysisDTO.builder()
        .verificationTaskId(verificationTaskId)
        .analysisStartTime(Instant.now().minus(10, ChronoUnit.MINUTES))
        .analysisEndTime(Instant.now().minus(5, ChronoUnit.MINUTES))
        .overallMetricScores(overallMetricScores)
        .txnMetricAnalysisData(txnMetricMap);
  }

  private ServiceGuardTimeSeriesAnalysisDTOBuilder
  generateServiceGuardMetricAnalysisDTOBuilder_customerDefinedUnhealthy() {
    Map<String, Double> overallMetricScores = new HashMap<>();
    overallMetricScores.put("Errors per Minute", 0.872);
    overallMetricScores.put("Average Response Time", 0.212);
    overallMetricScores.put("Calls Per Minute", 0.0);

    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricMap = new HashMap<>();
    transactions.forEach(txn -> {
      txnMetricMap.put(txn, new HashMap<>());
      metricList.forEach(metric -> {
        Map<String, ServiceGuardTxnMetricAnalysisDataDTO> metricMap = txnMetricMap.get(txn);
        ServiceGuardTxnMetricAnalysisDataDTO txnMetricData =
            ServiceGuardTxnMetricAnalysisDataDTO.builder()
                .isKeyTransaction(false)
                .cumulativeSums(ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO.builder().risk(0.5).data(0.9).build())
                .shortTermHistory(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                .anomalousPatterns(Arrays.asList(TimeSeriesAnomaliesDTO.builder()
                                                     .transactionName(txn)
                                                     .metricIdentifier(metric)
                                                     .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                     .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                                     .build()))
                .lastSeenTime(0)
                .metricType(TimeSeriesMetricType.ERROR)
                .risk(4)
                .build();
        metricMap.put(metric, txnMetricData);
      });
    });

    return ServiceGuardTimeSeriesAnalysisDTO.builder()
        .verificationTaskId(verificationTaskId)
        .analysisStartTime(Instant.now().minus(10, ChronoUnit.MINUTES))
        .analysisEndTime(Instant.now().minus(5, ChronoUnit.MINUTES))
        .overallMetricScores(overallMetricScores)
        .txnMetricAnalysisData(txnMetricMap);
  }

  private ServiceGuardTimeSeriesAnalysisDTO generateServiceGuardMetricAnalysisDTOBuilder(
      List<String> metricNames, List<Double> scores) {
    Map<String, Double> overallMetricScores = new HashMap<>();
    overallMetricScores.put("Errors per Minute", 0.872);
    overallMetricScores.put("Average Response Time", 0.212);
    overallMetricScores.put("Calls Per Minute", 0.0);
    Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricMap = new HashMap<>();
    String txn = "txn";
    txnMetricMap.put(txn, new HashMap<>());
    for (int i = 0; i < metricNames.size(); i++) {
      String metric = metricNames.get(i);
      Map<String, ServiceGuardTxnMetricAnalysisDataDTO> metricMap = txnMetricMap.get(txn);
      ServiceGuardTxnMetricAnalysisDataDTO txnMetricData =
          ServiceGuardTxnMetricAnalysisDataDTO.builder()
              .isKeyTransaction(false)
              .cumulativeSums(ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO.builder().risk(0.5).data(0.9).build())
              .shortTermHistory(Arrays.asList(0.1, 0.2, 0.3, 0.4))
              .anomalousPatterns(Arrays.asList(TimeSeriesAnomaliesDTO.builder()
                                                   .transactionName(txn)
                                                   .metricIdentifier(metric)
                                                   .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                   .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                                   .build()))
              .lastSeenTime(0)
              .metricType(TimeSeriesMetricType.ERROR)
              .risk(1)
              .score(scores.get(i))
              .build();
      metricMap.put(metric, txnMetricData);
    }

    return ServiceGuardTimeSeriesAnalysisDTO.builder()
        .verificationTaskId(verificationTaskId)
        .analysisStartTime(Instant.now().minus(10, ChronoUnit.MINUTES))
        .analysisEndTime(Instant.now().minus(5, ChronoUnit.MINUTES))
        .overallMetricScores(overallMetricScores)
        .txnMetricAnalysisData(txnMetricMap)
        .build();
  }

  private ServiceGuardTimeSeriesAnalysisDTO buildServiceGuardMetricAnalysisDTO_emptyCumulativeSums(
      String verificationTaskId) {
    Map<String, Double> overallMetricScores = new HashMap<>();
    overallMetricScores.put("Errors per Minute", 0.872);
    overallMetricScores.put("Average Response Time", 0.212);
    overallMetricScores.put("Calls Per Minute", 0.0);

    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricMap = new HashMap<>();
    transactions.forEach(txn -> {
      txnMetricMap.put(txn, new HashMap<>());
      metricList.forEach(metric -> {
        Map<String, ServiceGuardTxnMetricAnalysisDataDTO> metricMap = txnMetricMap.get(txn);
        ServiceGuardTxnMetricAnalysisDataDTO txnMetricData =
            ServiceGuardTxnMetricAnalysisDataDTO.builder()
                .isKeyTransaction(false)
                .shortTermHistory(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                .anomalousPatterns(Arrays.asList(TimeSeriesAnomaliesDTO.builder()
                                                     .transactionName(txn)
                                                     .metricIdentifier(metric)
                                                     .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                     .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                                     .build()))
                .lastSeenTime(0)
                .metricType(TimeSeriesMetricType.ERROR)
                .risk(2)
                .build();
        metricMap.put(metric, txnMetricData);
      });
    });

    return ServiceGuardTimeSeriesAnalysisDTO.builder()
        .verificationTaskId(verificationTaskId)
        .analysisStartTime(Instant.now().minus(10, ChronoUnit.MINUTES))
        .analysisEndTime(Instant.now().minus(5, ChronoUnit.MINUTES))
        .overallMetricScores(overallMetricScores)
        .txnMetricAnalysisData(txnMetricMap)
        .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testScheduleCanaryVerificationTaskAnalysis() {
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstance(builderFactory.canaryVerificationJobBuilder().build());
    cvConfigService.save(newCVConfig());
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(10, ChronoUnit.MINUTES))
                              .endTime(instant.plus(11, ChronoUnit.MINUTES))
                              .build();

    List<String> taskIds = timeSeriesAnalysisService.scheduleCanaryVerificationTaskAnalysis(input);
    assertThat(taskIds).isNotNull();

    assertThat(taskIds.size()).isEqualTo(1);

    TimeSeriesCanaryLearningEngineTask task =
        (TimeSeriesCanaryLearningEngineTask) hPersistence.get(LearningEngineTask.class, taskIds.get(0));
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(task.getDataLength()).isEqualTo(9);
    assertThat(task.getDeploymentVerificationTaskInfo().getDeploymentStartTime())
        .isEqualTo(verificationJobInstance.getDeploymentStartTime().toEpochMilli());
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.TIME_SERIES_CANARY.name());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testScheduleDeploymentTimeSeriesAnalysisCanaryDeploymentTimeSeries() {
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstance(builderFactory.canaryVerificationJobBuilder().build());
    cvConfigService.save(newCVConfig());
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(10, ChronoUnit.MINUTES))
                              .endTime(instant.plus(11, ChronoUnit.MINUTES))
                              .controlHosts(new HashSet<>(Collections.singletonList("host1")))
                              .testHosts(new HashSet<>(Collections.singletonList("host2")))
                              .verificationJobInstanceId(verificationJobInstance.getUuid())
                              .learningEngineTaskType(LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES)
                              .build();

    List<String> taskIds = timeSeriesAnalysisService.scheduleDeploymentTimeSeriesAnalysis(input);
    assertThat(taskIds).isNotNull();

    assertThat(taskIds.size()).isEqualTo(1);

    TimeSeriesCanaryLearningEngineTask_v2 task =
        (TimeSeriesCanaryLearningEngineTask_v2) hPersistence.get(LearningEngineTask.class, taskIds.get(0));
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(task.getDataLength()).isEqualTo(9);
    assertThat(task.getDeploymentVerificationTaskInfo().getDeploymentStartTime())
        .isEqualTo(verificationJobInstance.getDeploymentStartTime().toEpochMilli());
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES.name());
    String controlDataUrl = String.format(
        "/cv/api/timeseries-analysis/time-series-data?verificationTaskId=%s&hosts=%s&startTime=%s&endTime=%s",
        input.getVerificationTaskId(), "host1", verificationJobInstance.getStartTime().toEpochMilli(),
        input.getEndTime().toEpochMilli());
    assertThat(task.getControlDataUrl()).isEqualTo(controlDataUrl);
    String testDataUrl = String.format(
        "/cv/api/timeseries-analysis/time-series-data?verificationTaskId=%s&startTime=%s&endTime=%s&hosts=%s",
        input.getVerificationTaskId(), verificationJobInstance.getStartTime().toEpochMilli(),
        input.getEndTime().toEpochMilli(), "host2");
    assertThat(task.getTestDataUrl()).isEqualTo(testDataUrl);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testScheduleDeploymentTimeSeriesAnalysisBeforeAfterDeployment() {
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstance(builderFactory.canaryVerificationJobBuilder().build());
    cvConfigService.save(newCVConfig());
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(10, ChronoUnit.MINUTES))
                              .endTime(instant.plus(11, ChronoUnit.MINUTES))
                              .controlHosts(new HashSet<>(Collections.singletonList("host1")))
                              .testHosts(new HashSet<>(Collections.singletonList("host2")))
                              .verificationJobInstanceId(verificationJobInstance.getUuid())
                              .learningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES)
                              .build();

    List<String> taskIds = timeSeriesAnalysisService.scheduleDeploymentTimeSeriesAnalysis(input);
    assertThat(taskIds).isNotNull();

    assertThat(taskIds.size()).isEqualTo(1);

    TimeSeriesCanaryLearningEngineTask_v2 task =
        (TimeSeriesCanaryLearningEngineTask_v2) hPersistence.get(LearningEngineTask.class, taskIds.get(0));
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(task.getDataLength()).isEqualTo(9);
    assertThat(task.getDeploymentVerificationTaskInfo().getDeploymentStartTime())
        .isEqualTo(verificationJobInstance.getDeploymentStartTime().toEpochMilli());
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name())
        .isEqualTo(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES.name());
    CanaryBlueGreenVerificationJob verificationJob =
        (CanaryBlueGreenVerificationJob) verificationJobInstance.getResolvedJob();
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
    String controlDataUrl = String.format(
        "/cv/api/timeseries-analysis/time-series-data?verificationTaskId=%s&hosts=%s&startTime=%s&endTime=%s",
        input.getVerificationTaskId(), "host1", preDeploymentTimeRange.get().getStartTime().toEpochMilli(),
        preDeploymentTimeRange.get().getEndTime().toEpochMilli());
    assertThat(task.getControlDataUrl()).isEqualTo(controlDataUrl);
    String testDataUrl = String.format(
        "/cv/api/timeseries-analysis/time-series-data?verificationTaskId=%s&startTime=%s&endTime=%s&hosts=%s",
        input.getVerificationTaskId(), verificationJobInstance.getStartTime().toEpochMilli(),
        input.getEndTime().toEpochMilli(), "host2");
    assertThat(task.getTestDataUrl()).isEqualTo(testDataUrl);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplate() {
    AppDynamicsCVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(
        cvConfig.getAccountId(), cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), APP_DYNAMICS);
    metricPacks.forEach(
        metricPack -> metricPack.getMetrics().forEach(metricDefinition -> metricDefinition.setIncluded(true)));
    metricPackService.saveMetricPacks(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
        cvConfig.getProjectIdentifier(), APP_DYNAMICS, metricPacks);
    cvConfig.setMetricPack(metricPacks.get(0));
    hPersistence.save(cvConfig);
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setCvConfigMap(new HashMap<String, CVConfig>() {
      { put(cvConfig.getUuid(), cvConfig); }
    });
    hPersistence.save(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstance.getUuid(), APP_DYNAMICS);
    hPersistence.delete(cvConfig);
    List<TimeSeriesMetricDefinition> timeSeriesMetricDefinitions =
        timeSeriesAnalysisService.getMetricTemplate(verificationTaskId);
    assertThat(timeSeriesMetricDefinitions.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetricTemplate_forSLI() {
    String verificationTaskId = verificationTaskService.createSLIVerificationTask(accountId, generateUuid());
    List<TimeSeriesMetricDefinition> timeSeriesMetricDefinitions =
        timeSeriesAnalysisService.getMetricTemplate(verificationTaskId);
    assertThat(timeSeriesMetricDefinitions).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testScheduleTestVerificationTaskAnalysis() {
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstance(builderFactory.testVerificationJobBuilder().build());
    String verificationTaskId = verificationTaskService.getVerificationTaskId(
        verificationJobInstance.getAccountId(), cvConfigId, verificationJobInstance.getUuid());
    cvConfigService.save(newCVConfig());
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(10, ChronoUnit.MINUTES))
                              .endTime(instant.plus(11, ChronoUnit.MINUTES))
                              .build();
    List<String> taskIds = timeSeriesAnalysisService.scheduleTestVerificationTaskAnalysis(input);
    assertThat(taskIds.size()).isEqualTo(1);
    TimeSeriesLoadTestLearningEngineTask task =
        (TimeSeriesLoadTestLearningEngineTask) hPersistence.get(LearningEngineTask.class, taskIds.get(0));
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.TIME_SERIES_LOAD_TEST.name());
    assertThat(task.getControlDataUrl())
        .isEqualTo("/cv/api/timeseries-analysis/time-series-data?verificationTaskId=" + verificationTaskId
            + "&startTime=1595846760000&endTime=1595847660000");
    assertThat(task.getBaselineStartTime()).isEqualTo(1595846760000L);
    assertThat(task.getTestDataUrl())
        .isEqualTo("/cv/api/timeseries-analysis/time-series-data?verificationTaskId=" + verificationTaskId
            + "&startTime=1595846760000&endTime=1595847306390");
    assertThat(task.getMetricTemplateUrl())
        .isEqualTo("/cv/api/timeseries-analysis/timeseries-serviceguard-metric-template?verificationTaskId="
            + verificationTaskId);
    assertThat(task.getDataLength()).isEqualTo(9);
    assertThat(task.getTolerance()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testScheduleTestVerificationTaskAnalysis_baselineRun() {
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstance(builderFactory.testVerificationJobBuilder().build());
    ((TestVerificationJob) verificationJobInstance.getResolvedJob()).setBaselineVerificationJobInstanceId(null);
    verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.getVerificationTaskId(
        verificationJobInstance.getAccountId(), cvConfigId, verificationJobInstance.getUuid());
    cvConfigService.save(newCVConfig());
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(10, ChronoUnit.MINUTES))
                              .endTime(instant.plus(11, ChronoUnit.MINUTES))
                              .build();
    List<String> taskIds = timeSeriesAnalysisService.scheduleTestVerificationTaskAnalysis(input);
    assertThat(taskIds.size()).isEqualTo(1);
    TimeSeriesLoadTestLearningEngineTask task =
        (TimeSeriesLoadTestLearningEngineTask) hPersistence.get(LearningEngineTask.class, taskIds.get(0));
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.TIME_SERIES_LOAD_TEST.name());
    assertThat(task.getControlDataUrl()).isNull();
    assertThat(task.getBaselineStartTime()).isNull();
    assertThat(task.getTestDataUrl())
        .isEqualTo(CVConstants.SERVICE_BASE_URL + "/timeseries-analysis/time-series-data?verificationTaskId="
            + verificationTaskId + "&startTime=1595846760000&endTime=1595847306390");
    assertThat(task.getMetricTemplateUrl())
        .isEqualTo(CVConstants.SERVICE_BASE_URL
            + "/timeseries-analysis/timeseries-serviceguard-metric-template?verificationTaskId=" + verificationTaskId);
    assertThat(task.getDataLength()).isEqualTo(9);
    assertThat(task.getTolerance()).isEqualTo(2);
  }

  private DeploymentTimeSeriesAnalysisDTO buildDeploymentVerificationDTO() {
    return DeploymentTimeSeriesAnalysisDTO.builder().build();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTopTimeSeriesTransactionMetricRisk_validOrder() throws IllegalAccessException {
    FieldUtils.writeField(timeSeriesAnalysisService, "heatMapService", mock(HeatMapService.class), true);
    TimeSeriesLearningEngineTask task = TimeSeriesLearningEngineTask.builder().build();
    task.setTestDataUrl("testData");
    task.setWindowSize(5);
    fillCommon(task, LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES);
    Instant start = instant.minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    task.setPickedAt(end.plus(Duration.ofMinutes(2)));
    task.setAnalysisStartTime(start);
    task.setAnalysisEndTime(end);

    learningEngineTaskService.createLearningEngineTask(task);
    String metricNames[] = {"metric1", "metric2", "metric3", "metrics4"};
    Double scores[] = {.1, .2, .3, .4};
    for (int i = 0; i < 2; i++) {
      timeSeriesAnalysisService.saveAnalysis(task.getUuid(),
          generateServiceGuardMetricAnalysisDTOBuilder(Arrays.asList(metricNames), Arrays.asList(scores)));
    }
    List<TransactionMetricRisk> transactionMetricRisks =
        timeSeriesAnalysisService.getTopTimeSeriesTransactionMetricRisk(Collections.singletonList(verificationTaskId),
            end.minus(Duration.ofMinutes(10)), end.plus(Duration.ofMinutes(1)));
    assertThat(transactionMetricRisks).hasSize(3);

    for (int i = 0; i < 3; i++) {
      assertThat(transactionMetricRisks.get(i).getMetricIdentifier()).isEqualTo(metricNames[i + 1]);
      assertThat(transactionMetricRisks.get(i).getTransactionName()).isEqualTo("txn");
      assertThat(transactionMetricRisks.get(i).getMetricScore()).isEqualTo(scores[i + 1], offset(.0001));
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTopTimeSeriesTransactionMetricRisk_empty() {
    assertThat(
        timeSeriesAnalysisService.getTopTimeSeriesTransactionMetricRisk(Collections.singletonList(generateUuid()),
            instant.minus(Duration.ofMinutes(10)), instant.plus(Duration.ofMinutes(1))))
        .isEmpty();
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

  private List<TimeSeriesShortTermHistory.TransactionMetricHistory> buildShortTermHistory() {
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");

    List<TimeSeriesShortTermHistory.TransactionMetricHistory> shortTermHistoryList = new ArrayList<>();

    transactions.forEach(txn -> {
      TimeSeriesShortTermHistory.TransactionMetricHistory transactionMetricHistory =
          TimeSeriesShortTermHistory.TransactionMetricHistory.builder()
              .transactionName(txn)
              .metricHistoryList(new ArrayList<>())
              .build();
      metricList.forEach(metric -> {
        TimeSeriesShortTermHistory.MetricHistory metricHistory = TimeSeriesShortTermHistory.MetricHistory.builder()
                                                                     .metricName(metric)
                                                                     .value(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                                     .build();
        transactionMetricHistory.getMetricHistoryList().add(metricHistory);
      });
      shortTermHistoryList.add(transactionMetricHistory);
    });
    return shortTermHistoryList;
  }

  private List<TimeSeriesCumulativeSums.TransactionMetricSums> buildTransactionMetricSums() {
    List<TimeSeriesCumulativeSums.TransactionMetricSums> txnMetricSums = new ArrayList<>();
    List<String> transactions = Arrays.asList("txn1", "txn2", "txn3");
    List<String> metricList = Arrays.asList("metric1", "metric2", "metric3");

    transactions.forEach(txn -> {
      TimeSeriesCumulativeSums.TransactionMetricSums transactionMetricSums =
          TimeSeriesCumulativeSums.TransactionMetricSums.builder()
              .transactionName(txn)
              .metricSums(new ArrayList<>())
              .build();

      metricList.forEach(metric -> {
        TimeSeriesCumulativeSums.MetricSum metricSums =
            TimeSeriesCumulativeSums.MetricSum.builder().metricName(metric).risk(0.5).data(0.9).build();
        transactionMetricSums.getMetricSums().add(metricSums);
      });
      txnMetricSums.add(transactionMetricSums);
    });
    return txnMetricSums;
  }

  private List<TimeSeriesRecord> getTimeSeriesRecords() throws Exception {
    File file = new File(getResourceFilePath("timeseries/timeseriesRecords.json"));
    final Gson gson = new Gson();
    List<String> hosts = Arrays.asList("host1", "host2", "host3", "host4", "host5");
    Random r = new Random();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
      List<TimeSeriesRecord> timeSeriesMLAnalysisRecords = gson.fromJson(br, type);
      timeSeriesMLAnalysisRecords.forEach(timeSeriesMLAnalysisRecord -> {
        timeSeriesMLAnalysisRecord.setVerificationTaskId(verificationTaskId);
        timeSeriesMLAnalysisRecord.setBucketStartTime(Instant.parse("2020-07-07T02:40:00.000Z"));
        timeSeriesMLAnalysisRecord.setHost(hosts.get(r.nextInt(hosts.size())));
        timeSeriesMLAnalysisRecord.getTimeSeriesGroupValues().forEach(groupVal -> {
          Instant baseTime = Instant.parse("2020-07-07T02:40:00.000Z");
          Random random = new Random();
          groupVal.setTimeStamp(baseTime.plus(random.nextInt(4), ChronoUnit.MINUTES));
        });
      });
      return timeSeriesMLAnalysisRecords;
    }
  }

  private List<TimeSeriesRecord> getTimeSeriesRecords_forServiceGuard() throws Exception {
    List<TimeSeriesRecord> timeSeriesMLAnalysisRecords = new ArrayList<>();
    Instant now = roundDownTo5MinBoundary(Instant.now().plus(Duration.ofMinutes(5)));
    Set<TimeSeriesRecord.TimeSeriesGroupValue> timeSeriesGroupValues = new HashSet<>();
    int i = 0;
    while (i < 5) {
      int k = 1;
      while (k < 4) {
        timeSeriesGroupValues.add(TimeSeriesRecord.TimeSeriesGroupValue.builder()
                                      .groupName("txn" + k)
                                      .metricValue(231)
                                      .riskScore(-1.0)
                                      .timeStamp(now.plus(i, ChronoUnit.MINUTES))
                                      .build());
        k++;
      }
      i++;
    }
    int j = 0;
    while (j < 3) {
      timeSeriesMLAnalysisRecords.add(TimeSeriesRecord.builder()
                                          .bucketStartTime(now)
                                          .verificationTaskId(verificationTaskId)
                                          .metricIdentifier(metricList.get(j))
                                          .timeSeriesGroupValues(timeSeriesGroupValues)
                                          .build());

      j++;
    }
    return timeSeriesMLAnalysisRecords;
  }

  private CVConfig newCVConfig() {
    return builderFactory.splunkCVConfigBuilder().build();
  }

  private VerificationJobInstance createVerificationJobInstance(VerificationJob verificationJob) {
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .accountId(accountId)
            .executionStatus(VerificationJobInstance.ExecutionStatus.QUEUED)
            .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
            .resolvedJob(verificationJob)
            .startTime(Instant.ofEpochMilli(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis()))
            .build();
    if (verificationJob.getType() == VerificationJobType.TEST) {
      verificationJobInstance.setUuid(((TestVerificationJob) verificationJob).getBaselineVerificationJobInstanceId());
    }
    verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstance.getUuid(), APP_DYNAMICS);
    return verificationJobInstance;
  }
  private void fillCommon(LearningEngineTask learningEngineTask, LearningEngineTaskType analysisType) {
    learningEngineTask.setTaskStatus(LearningEngineTask.ExecutionStatus.QUEUED);
    learningEngineTask.setVerificationTaskId(verificationTaskId);
    learningEngineTask.setAnalysisType(analysisType);
    learningEngineTask.setFailureUrl("failure-url");
    learningEngineTask.setAnalysisStartTime(instant.minus(Duration.ofMinutes(10)));
    learningEngineTask.setAnalysisEndTime(instant);
  }
}
