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
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_METRIC_NAME;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.Frequency;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.entities.ServiceGuardLogAnalysisTask;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class TrendAnalysisServiceImplTest extends CvNextGenTestBase {
  private String cvConfigId;
  private String verificationTaskId;
  @Inject private HPersistence hPersistence;
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private TrendAnalysisService trendAnalysisService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private HeatMapService heatMapService;
  private BuilderFactory builderFactory;

  @Before
  public void setUp() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    CVConfig cvConfig = createCVConfig();
    cvConfigService.save(cvConfig);
    cvConfigId = cvConfig.getUuid();
    verificationTaskId = verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfigId);

    FieldUtils.writeField(trendAnalysisService, "heatMapService", heatMapService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testScheduleTrendAnalysisTask() {
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES))
                              .endTime(TIME_FOR_TESTS)
                              .build();
    String taskId = trendAnalysisService.scheduleTrendAnalysisTask(input);

    assertThat(taskId).isNotNull();

    TimeSeriesLearningEngineTask task =
        (TimeSeriesLearningEngineTask) hPersistence.get(LearningEngineTask.class, taskId);
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES.name());
    assertThat(task.getDataLength()).isEqualTo(TIMESERIES_SERVICE_GUARD_DATA_LENGTH);
    assertThat(task.getWindowSize()).isEqualTo(TIMESERIES_SERVICE_GUARD_WINDOW_SIZE);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetTestData() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> logAnalysisClusters = createLogAnalysisClusters(start, end);
    hPersistence.save(logAnalysisClusters);

    List<TimeSeriesRecordDTO> testData = trendAnalysisService.getTestData(verificationTaskId, start, end);

    assertThat(testData).isNotNull();
    assertThat(testData.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetTestData_withBaselineClusters() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> logAnalysisClusters = createLogAnalysisClusters(start, end);

    LogAnalysisCluster record = LogAnalysisCluster.builder()
                                    .verificationTaskId(verificationTaskId)
                                    .label(1)
                                    .firstSeenTime(start.toEpochMilli())
                                    .frequencyTrend(new ArrayList<>())
                                    .isEvicted(false)
                                    .build();
    logAnalysisClusters.add(record);

    hPersistence.save(logAnalysisClusters);

    List<TimeSeriesRecordDTO> testData = trendAnalysisService.getTestData(verificationTaskId, start, end);

    assertThat(testData).isNotNull();
    assertThat(testData.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetTestData_withNonBaselineClusters() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> logAnalysisClusters = createLogAnalysisClusters(start, end);

    LogAnalysisCluster record =
        LogAnalysisCluster.builder()
            .verificationTaskId(verificationTaskId)
            .label(1)
            .firstSeenTime(start.minus(40, ChronoUnit.MINUTES).toEpochMilli())
            .frequencyTrend(Collections.singletonList(Frequency.builder()
                                                          .timestamp(TimeUnit.SECONDS.toMinutes(start.getEpochSecond()))
                                                          .count(4)
                                                          .riskScore(0.5)
                                                          .build()))
            .isEvicted(false)
            .build();
    logAnalysisClusters.add(record);

    hPersistence.save(logAnalysisClusters);

    List<TimeSeriesRecordDTO> testData = trendAnalysisService.getTestData(verificationTaskId, start, end);

    assertThat(testData).isNotNull();
    assertThat(testData.size()).isEqualTo(11);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysis() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> logAnalysisClusters = createLogAnalysisClusters(start, end.plus(Duration.ofMinutes(5)));
    List<LogAnalysisResult> logAnalysisResults = createLogAnalysisResults(start, end);
    hPersistence.save(logAnalysisClusters);
    hPersistence.save(logAnalysisResults);
    saveLELogAnalysisTask(start, end, false);
    String learningEngineTaskId = saveLETask(start, end);

    trendAnalysisService.saveAnalysis(learningEngineTaskId, buildServiceGuardMetricAnalysisDTO(start, end));

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

    List<LogAnalysisCluster> savedClusters =
        hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority).asList();
    assertThat(savedClusters).isNotEmpty();
    assertThat(savedClusters.size()).isEqualTo(2);
    int index = 0;
    for (LogAnalysisCluster cluster : savedClusters) {
      for (Frequency frequency : cluster.getFrequencyTrend()) {
        if (frequency.getTimestamp() >= start.toEpochMilli() && frequency.getTimestamp() < end.toEpochMilli()) {
          assertThat(frequency.getRiskScore()).isEqualTo((double) index);
        }
      }
      index++;
    }

    LogAnalysisResult analysisResult = hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority).get();
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.getOverallRisk()).isEqualTo(0.872);
    assertThat(analysisResult.getLogAnalysisResults().get(0))
        .isEqualTo(AnalysisResult.builder().label(1).tag(LogAnalysisTag.KNOWN).count(14).build());
    assertThat(analysisResult.getLogAnalysisResults().get(1))
        .isEqualTo(AnalysisResult.builder().label(2).tag(LogAnalysisTag.UNEXPECTED).count(14).riskScore(1.0).build());
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(
        heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getAnomalousLogsCount()).isEqualTo(1));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysis_emptyCumulativeSums() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> logAnalysisClusters = createLogAnalysisClusters(start, end);
    List<LogAnalysisResult> logAnalysisResults = createLogAnalysisResults(start, end);
    hPersistence.save(logAnalysisClusters);
    hPersistence.save(logAnalysisResults);
    saveLELogAnalysisTask(start, end, false);
    String learningEngineTaskId = saveLETask(start, end);

    trendAnalysisService.saveAnalysis(learningEngineTaskId, buildServiceGuardMetricAnalysisDTO_emptySums(start, end));

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

    List<LogAnalysisCluster> savedClusters =
        hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority).asList();
    assertThat(savedClusters).isNotEmpty();
    assertThat(savedClusters.size()).isEqualTo(2);
    for (LogAnalysisCluster cluster : savedClusters) {
      for (Frequency frequency : cluster.getFrequencyTrend()) {
        assertThat(frequency.getRiskScore()).isEqualTo(1.0);
      }
    }
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(
        heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getAnomalousLogsCount()).isEqualTo(2));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysis_baselineWindow() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> logAnalysisClusters = createLogAnalysisClusters(start, end);
    List<LogAnalysisResult> logAnalysisResults = createLogAnalysisResults(start, end);
    hPersistence.save(logAnalysisClusters);
    hPersistence.save(logAnalysisResults);
    saveLELogAnalysisTask(start, end, true);
    String learningEngineTaskId = saveLETask(start, end);

    trendAnalysisService.saveAnalysis(learningEngineTaskId, buildServiceGuardMetricAnalysisDTO(start, end));

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

    List<LogAnalysisCluster> savedClusters =
        hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority).asList();
    assertThat(savedClusters).isNotEmpty();
    assertThat(savedClusters.size()).isEqualTo(2);
    int index = 0;
    for (LogAnalysisCluster cluster : savedClusters) {
      for (Frequency frequency : cluster.getFrequencyTrend()) {
        assertThat(frequency.getRiskScore()).isEqualTo((double) index);
      }
      index++;
    }

    LogAnalysisResult analysisResult = hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority).get();
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.getOverallRisk()).isEqualTo(0.872);
    assertThat(analysisResult.getLogAnalysisResults().get(0))
        .isEqualTo(AnalysisResult.builder().label(1).tag(LogAnalysisTag.KNOWN).count(14).build());
    assertThat(analysisResult.getLogAnalysisResults().get(1))
        .isEqualTo(AnalysisResult.builder().label(2).tag(LogAnalysisTag.KNOWN).count(14).build());
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(
        heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getAnomalousLogsCount()).isEqualTo(0));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetTimeSeriesMetricDefinitions() {
    List<TimeSeriesMetricDefinition> timeSeriesMetricDefinitions =
        trendAnalysisService.getTimeSeriesMetricDefinitions();
    assertThat(timeSeriesMetricDefinitions).isNotEmpty();
    assertThat(timeSeriesMetricDefinitions.size()).isEqualTo(2);
    for (TimeSeriesMetricDefinition timeSeriesMetricDefinition : timeSeriesMetricDefinitions) {
      assertThat(timeSeriesMetricDefinition.getMetricName()).isEqualTo(TREND_METRIC_NAME);
      assertThat(timeSeriesMetricDefinition.getMetricType()).isEqualTo(TimeSeriesMetricType.ERROR);
      assertThat(timeSeriesMetricDefinition.getMetricGroupName()).isEqualTo("*");
    }
  }

  private String saveLETask(Instant start, Instant end) {
    TimeSeriesLearningEngineTask timeSeriesLearningEngineTask = TimeSeriesLearningEngineTask.builder().build();
    timeSeriesLearningEngineTask.setVerificationTaskId(verificationTaskId);
    timeSeriesLearningEngineTask.setAnalysisStartTime(start);
    timeSeriesLearningEngineTask.setAnalysisEndTime(end);
    timeSeriesLearningEngineTask.setPickedAt(end.plus(Duration.ofMinutes(2)));
    return learningEngineTaskService.createLearningEngineTask(timeSeriesLearningEngineTask);
  }

  private void saveLELogAnalysisTask(Instant start, Instant end, boolean isBaselineWindow) {
    ServiceGuardLogAnalysisTask serviceGuardLogAnalysisTask =
        ServiceGuardLogAnalysisTask.builder().isBaselineWindow(isBaselineWindow).build();
    serviceGuardLogAnalysisTask.setVerificationTaskId(verificationTaskId);
    serviceGuardLogAnalysisTask.setAnalysisStartTime(start);
    serviceGuardLogAnalysisTask.setAnalysisEndTime(end);
    serviceGuardLogAnalysisTask.setAnalysisType(LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS);
    learningEngineTaskService.createLearningEngineTask(serviceGuardLogAnalysisTask);
  }

  private ServiceGuardTimeSeriesAnalysisDTO buildServiceGuardMetricAnalysisDTO(Instant start, Instant end) {
    Map<String, Double> overallMetricScores = new HashMap<>();
    overallMetricScores.put(TREND_METRIC_NAME, 0.872);

    int index = 0;
    List<String> transactions = Arrays.asList("1", "2");
    Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricMap = new HashMap<>();
    for (String txn : transactions) {
      txnMetricMap.put(txn, new HashMap<>());
      Map<String, ServiceGuardTxnMetricAnalysisDataDTO> metricMap = txnMetricMap.get(txn);
      ServiceGuardTxnMetricAnalysisDataDTO txnMetricData =
          ServiceGuardTxnMetricAnalysisDataDTO.builder()
              .isKeyTransaction(false)
              .cumulativeSums(ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO.builder().risk(0.5).data(0.9).build())
              .shortTermHistory(Arrays.asList(0.1, 0.2, 0.3, 0.4))
              .anomalousPatterns(
                  Collections.singletonList(TimeSeriesAnomaliesDTO.builder()
                                                .transactionName(txn)
                                                .metricIdentifier(TREND_METRIC_NAME)
                                                .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                                .build()))
              .lastSeenTime(0)
              .metricType(TimeSeriesMetricType.ERROR)
              .risk(index)
              .score((double) index)
              .build();
      metricMap.put(TREND_METRIC_NAME, txnMetricData);
      index += 1;
    }

    return ServiceGuardTimeSeriesAnalysisDTO.builder()
        .verificationTaskId(verificationTaskId)
        .analysisStartTime(start)
        .analysisEndTime(end)
        .overallMetricScores(overallMetricScores)
        .txnMetricAnalysisData(txnMetricMap)
        .build();
  }

  private ServiceGuardTimeSeriesAnalysisDTO buildServiceGuardMetricAnalysisDTO_emptySums(Instant start, Instant end) {
    Map<String, Double> overallMetricScores = new HashMap<>();
    overallMetricScores.put(TREND_METRIC_NAME, 0.872);

    List<String> transactions = Arrays.asList("1", "2");
    Map<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricMap = new HashMap<>();
    transactions.forEach(txn -> {
      txnMetricMap.put(txn, new HashMap<>());
      Map<String, ServiceGuardTxnMetricAnalysisDataDTO> metricMap = txnMetricMap.get(txn);
      ServiceGuardTxnMetricAnalysisDataDTO txnMetricData =
          ServiceGuardTxnMetricAnalysisDataDTO.builder()
              .isKeyTransaction(false)
              .shortTermHistory(Arrays.asList(0.1, 0.2, 0.3, 0.4))
              .anomalousPatterns(
                  Collections.singletonList(TimeSeriesAnomaliesDTO.builder()
                                                .transactionName(txn)
                                                .metricIdentifier(TREND_METRIC_NAME)
                                                .testData(Arrays.asList(0.1, 0.2, 0.3, 0.4))
                                                .anomalousTimestamps(Arrays.asList(12345l, 12346l, 12347l))
                                                .build()))
              .lastSeenTime(0)
              .metricType(TimeSeriesMetricType.ERROR)
              .risk(2)
              .score(1.0)
              .build();
      metricMap.put(TREND_METRIC_NAME, txnMetricData);
    });

    return ServiceGuardTimeSeriesAnalysisDTO.builder()
        .verificationTaskId(verificationTaskId)
        .analysisStartTime(start)
        .analysisEndTime(end)
        .overallMetricScores(overallMetricScores)
        .txnMetricAnalysisData(txnMetricMap)
        .build();
  }

  private List<LogAnalysisCluster> createLogAnalysisClusters(Instant startTime, Instant endTime) {
    List<LogAnalysisCluster> logRecords = new ArrayList<>();

    LogAnalysisCluster record1 = LogAnalysisCluster.builder()
                                     .verificationTaskId(verificationTaskId)
                                     .label(1)
                                     .frequencyTrend(new ArrayList<>())
                                     .isEvicted(false)
                                     .build();
    LogAnalysisCluster record2 = LogAnalysisCluster.builder()
                                     .verificationTaskId(verificationTaskId)
                                     .label(2)
                                     .frequencyTrend(new ArrayList<>())
                                     .isEvicted(false)
                                     .build();
    Instant timestamp = startTime;
    while (timestamp.isBefore(endTime)) {
      Frequency frequency1 =
          Frequency.builder().timestamp(TimeUnit.SECONDS.toMinutes(timestamp.getEpochSecond())).count(4).build();
      Frequency frequency2 =
          Frequency.builder().timestamp(TimeUnit.SECONDS.toMinutes(timestamp.getEpochSecond())).count(10).build();
      record1.getFrequencyTrend().add(frequency1);
      record2.getFrequencyTrend().add(frequency2);
      timestamp = timestamp.plus(1, ChronoUnit.MINUTES);
    }
    logRecords.add(record1);
    logRecords.add(record2);

    return logRecords;
  }

  private List<LogAnalysisResult> createLogAnalysisResults(Instant startTime, Instant endTime) {
    List<LogAnalysisResult> logAnalysisResults = new ArrayList<>();

    logAnalysisResults.add(LogAnalysisResult.builder()
                               .analysisStartTime(startTime)
                               .analysisEndTime(endTime)
                               .verificationTaskId(verificationTaskId)
                               .overallRisk(0.0)
                               .logAnalysisResults(Lists.newArrayList(
                                   AnalysisResult.builder().label(1).tag(LogAnalysisTag.KNOWN).count(14).build(),
                                   AnalysisResult.builder().label(2).tag(LogAnalysisTag.KNOWN).count(14).build()))
                               .build());

    return logAnalysisResults;
  }

  private CVConfig createCVConfig() {
    return builderFactory.splunkCVConfigBuilder().build();
  }
}
