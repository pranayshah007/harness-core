/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.BULK_OPERATION_THRESHOLD;
import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.CUMULATIVE_SUMS_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_METRIC_TEMPLATE_FILE;
import static io.harness.cvng.analysis.CVAnalysisConstants.PREVIOUS_ANOMALIES_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.SERVICE_GUARD_SHORT_TERM_HISTORY_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_DATA_LENGTH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_BASELINE_WINDOW_FOR_NEW_CLUSTER;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_SAVE_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_TEST_DATA;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_METRIC_NAME;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_METRIC_TEMPLATE;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.Frequency;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.LogAnalysisClusterKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisResultKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.entities.ServiceGuardLogAnalysisTask;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums.MetricSum;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums.TransactionMetricSums;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnomalousPatternsService;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.persistence.HPersistence;
import io.harness.serializer.YamlUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;

@Slf4j
public class TrendAnalysisServiceImpl implements TrendAnalysisService {
  private static final int MAX_FREQUENCY_TREND_SIZE = (int) Duration.ofDays(30).toMinutes();
  @Inject private HPersistence hPersistence;
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  @Inject private HeatMapService heatMapService;
  @Inject private TimeSeriesAnomalousPatternsService timeSeriesAnomalousPatternsService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;

  @Override
  public Map<String, ExecutionStatus> getTaskStatus(List<String> taskIds) {
    return learningEngineTaskService.getTaskStatus(new HashSet<>(taskIds));
  }

  @Override
  public String scheduleTrendAnalysisTask(AnalysisInput input) {
    TimeSeriesLearningEngineTask task = createTrendAnalysisTask(input);
    log.info("Scheduling ServiceGuardLogAnalysisTask {}", task);
    return learningEngineTaskService.createLearningEngineTask(task);
  }

  private TimeSeriesLearningEngineTask createTrendAnalysisTask(AnalysisInput input) {
    String taskId = generateUuid();
    TimeSeriesLearningEngineTask timeSeriesLearningEngineTask =
        TimeSeriesLearningEngineTask.builder()
            .cumulativeSumsUrl(createCumulativeSumsUrl(input))
            .metricTemplateUrl(createMetricTemplateUrl())
            .previousAnalysisUrl(createPreviousAnalysisUrl(input))
            .previousAnomaliesUrl(createAnomaliesUrl(input))
            .testDataUrl(createTestDataUrl(input))
            .build();
    timeSeriesLearningEngineTask.setVerificationTaskId(input.getVerificationTaskId());
    timeSeriesLearningEngineTask.setAnalysisType(LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES);
    timeSeriesLearningEngineTask.setAnalysisStartTime(input.getStartTime());
    timeSeriesLearningEngineTask.setAnalysisEndTime(input.getEndTime());
    timeSeriesLearningEngineTask.setAnalysisEndEpochMinute(
        TimeUnit.MILLISECONDS.toMinutes(input.getEndTime().toEpochMilli()));
    timeSeriesLearningEngineTask.setAnalysisSaveUrl(createAnalysisSaveUrl(taskId));
    timeSeriesLearningEngineTask.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    timeSeriesLearningEngineTask.setUuid(taskId);

    return timeSeriesLearningEngineTask;
  }

  private String createAnalysisSaveUrl(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TREND_ANALYSIS_RESOURCE + "/" + TREND_ANALYSIS_SAVE_PATH);
    uriBuilder.addParameter("taskId", taskId);
    return getUriString(uriBuilder);
  }

  private String createPreviousAnalysisUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/" + SERVICE_GUARD_SHORT_TERM_HISTORY_URL);
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    return getUriString(uriBuilder);
  }

  private String createCumulativeSumsUrl(AnalysisInput input) {
    Instant startTime = input.getStartTime().minus(1, ChronoUnit.DAYS);
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/" + CUMULATIVE_SUMS_URL);
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("analysisStartTime", startTime.toString());
    uriBuilder.addParameter("analysisEndTime", input.getEndTime().toString());
    return getUriString(uriBuilder);
  }

  private String createAnomaliesUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/" + PREVIOUS_ANOMALIES_URL);
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    return getUriString(uriBuilder);
  }

  private String createTestDataUrl(AnalysisInput input) {
    Instant startForTestData = input.getEndTime()
                                   .truncatedTo(ChronoUnit.SECONDS)
                                   .minus(TIMESERIES_SERVICE_GUARD_DATA_LENGTH, ChronoUnit.MINUTES);

    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TREND_ANALYSIS_RESOURCE + "/" + TREND_ANALYSIS_TEST_DATA);
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("analysisStartTime", startForTestData.toString());
    uriBuilder.addParameter("analysisEndTime", input.getEndTime().toString());
    return getUriString(uriBuilder);
  }

  private String createMetricTemplateUrl() {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TREND_ANALYSIS_RESOURCE + "/" + TREND_METRIC_TEMPLATE);
    return getUriString(uriBuilder);
  }

  private String getUriString(URIBuilder uriBuilder) {
    try {
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<TimeSeriesRecordDTO> getTestData(String verificationTaskId, Instant startTime, Instant endTime) {
    List<LogAnalysisCluster> logAnalysisClusters =
        hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority)
            .filter(LogAnalysisClusterKeys.verificationTaskId, verificationTaskId)
            .filter(LogAnalysisClusterKeys.isEvicted, Boolean.FALSE)
            .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
    List<TimeSeriesRecordDTO> testData = new ArrayList<>();
    logAnalysisClusters.forEach(logAnalysisCluster -> {
      if (Instant.ofEpochMilli(logAnalysisCluster.getFirstSeenTime())
              .plus(TREND_ANALYSIS_BASELINE_WINDOW_FOR_NEW_CLUSTER, ChronoUnit.MINUTES)
              .isBefore(endTime)) {
        String groupName = String.valueOf(logAnalysisCluster.getLabel());
        List<TimeSeriesRecordDTO> testDataForCluster =
            getTestDataForCluster(logAnalysisCluster, startTime, endTime, groupName);
        testData.addAll(testDataForCluster);
      }
    });
    return testData;
  }

  private List<TimeSeriesRecordDTO> getTestDataForCluster(
      LogAnalysisCluster cluster, Instant startTime, Instant endTime, String groupName) {
    List<TimeSeriesRecordDTO> testData = new ArrayList<>();
    int index = cluster.getFrequencyTrend().size() - 1;
    while (index >= 0) {
      Frequency frequency = cluster.getFrequencyTrend().get(index);
      Instant timestamp = DateTimeUtils.epochMinuteToInstant(frequency.getTimestamp());
      if (timestamp.isBefore(startTime)) {
        break;
      }
      if (timestamp.isBefore(endTime)) {
        testData.add(TimeSeriesRecordDTO.builder()
                         .groupName(groupName)
                         .metricName(TREND_METRIC_NAME)
                         .metricValue(Double.valueOf(frequency.getCount()))
                         .epochMinute(frequency.getTimestamp())
                         .build());
      }
      index--;
    }
    return testData;
  }

  @Override
  public void saveAnalysis(String taskId, ServiceGuardTimeSeriesAnalysisDTO analysis) {
    LearningEngineTask learningEngineTask = learningEngineTaskService.get(taskId);
    Preconditions.checkNotNull(learningEngineTask, "Needs to be a valid LE task.");
    ServiceGuardLogAnalysisTask logAnalysisTask =
        (ServiceGuardLogAnalysisTask) hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
            .filter(LearningEngineTaskKeys.verificationTaskId, learningEngineTask.getVerificationTaskId())
            .filter(LearningEngineTaskKeys.analysisEndTime, learningEngineTask.getAnalysisEndTime())
            .filter(LearningEngineTaskKeys.analysisType, SERVICE_GUARD_LOG_ANALYSIS)
            .get();
    Preconditions.checkNotNull(
        logAnalysisTask, "Log analysis task not present for trend task id: ", learningEngineTask.getUuid());
    Instant startTime = learningEngineTask.getAnalysisStartTime();
    Instant endTime = learningEngineTask.getAnalysisEndTime();
    analysis.setVerificationTaskId(learningEngineTask.getVerificationTaskId());
    analysis.setAnalysisStartTime(startTime);
    analysis.setAnalysisEndTime(endTime);

    TimeSeriesShortTermHistory shortTermHistory = buildShortTermHistory(analysis);
    TimeSeriesCumulativeSums cumulativeSums = buildCumulativeSums(analysis, startTime, endTime);
    TimeSeriesRiskSummary riskSummary = buildRiskSummary(analysis, startTime, endTime);

    saveRisk(
        analysis, startTime, endTime, learningEngineTask.getVerificationTaskId(), logAnalysisTask.isBaselineWindow());
    timeSeriesAnalysisService.saveShortTermHistory(shortTermHistory);
    timeSeriesAnomalousPatternsService.saveAnomalousPatterns(analysis, learningEngineTask.getVerificationTaskId());
    hPersistence.save(cumulativeSums);
    hPersistence.save(riskSummary);
    log.info("Saving analysis for verification task Id: {}", learningEngineTask.getVerificationTaskId());
    learningEngineTaskService.markCompleted(taskId);
  }

  private void saveRisk(ServiceGuardTimeSeriesAnalysisDTO analysis, Instant startTime, Instant endTime,
      String verificationTaskId, boolean isBaselineRun) {
    Map<Long, Double> unexpectedClustersWithRiskScore = new HashMap<>();
    String cvConfigId = verificationTaskService.getCVConfigId(verificationTaskId);
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    Preconditions.checkNotNull(cvConfig, "Config not present for verification task id: {}", verificationTaskId);

    saveRiskForLogClusters(analysis, startTime, endTime, verificationTaskId, unexpectedClustersWithRiskScore);
    updateRiskForLogAnalysisResult(
        analysis, startTime, endTime, cvConfig, verificationTaskId, unexpectedClustersWithRiskScore, isBaselineRun);
  }

  private void updateRiskForLogAnalysisResult(ServiceGuardTimeSeriesAnalysisDTO analysis, Instant startTime,
      Instant endTime, CVConfig cvConfig, String verificationTaskId, Map<Long, Double> unexpectedClustersWithRiskScore,
      boolean isBaselineRun) {
    LogAnalysisResult analysisResult = hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority)
                                           .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
                                           .filter(LogAnalysisResultKeys.analysisEndTime, endTime)
                                           .get();
    if (isNotEmpty(unexpectedClustersWithRiskScore)) {
      long anomalousCount = 0;
      double score = Math.max(analysis.getOverallMetricScores().values().stream().mapToDouble(s -> s).max().orElse(0.0),
          analysisResult.getOverallRisk());
      analysisResult.setOverallRisk(score);
      for (LogAnalysisResult.AnalysisResult logAnalysisCluster : analysisResult.getLogAnalysisResults()) {
        if (unexpectedClustersWithRiskScore.containsKey(logAnalysisCluster.getLabel())
            && logAnalysisCluster.getTag() == LogAnalysisTag.KNOWN && !isBaselineRun) {
          logAnalysisCluster.setTag(LogAnalysisTag.UNEXPECTED);
          logAnalysisCluster.setRiskScore(unexpectedClustersWithRiskScore.get(logAnalysisCluster.getLabel()));
          anomalousCount++;
        }
      }
      hPersistence.save(analysisResult);
      heatMapService.updateRiskScore(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
          cvConfig.getProjectIdentifier(), cvConfig, cvConfig.getCategory(), startTime, score, 0, anomalousCount);
    }
  }

  private void saveRiskForLogClusters(ServiceGuardTimeSeriesAnalysisDTO analysis, Instant startTime, Instant endTime,
      String verificationTaskId, Map<Long, Double> unexpectedClustersWithRiskScore) {
    List<LogAnalysisCluster> logAnalysisClusters =
        hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority)
            .filter(LogAnalysisClusterKeys.verificationTaskId, verificationTaskId)
            .filter(LogAnalysisClusterKeys.isEvicted, Boolean.FALSE)
            .asList();
    Map<Long, LogAnalysisCluster> logAnalysisClusterMap =
        logAnalysisClusters.stream().collect(Collectors.toMap(LogAnalysisCluster::getLabel, cluster -> cluster));

    final DBCollection collection = hPersistence.getCollection(LogAnalysisCluster.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int numberOfBulkOperations = 0;
    for (Map.Entry<String, Map<String, ServiceGuardTxnMetricAnalysisDataDTO>> txnMetricAnalysis :
        analysis.getTxnMetricAnalysisData().entrySet()) {
      String txnName = txnMetricAnalysis.getKey();
      ServiceGuardTxnMetricAnalysisDataDTO analysisDataDTO = txnMetricAnalysis.getValue().get(TREND_METRIC_NAME);
      LogAnalysisCluster cluster = logAnalysisClusterMap.get(Long.valueOf(txnName));
      if (cluster == null) {
        // This if condition is added as the LE also sends the data for control data points in case of log clusters as
        // the last step for log analysis is SERVICE_GAURD_TIME_SERIES. once we remove this and do distribution analysis
        // instead, we need to make sure that the data for control cluster is not there in the LE output and remove this
        // if condition
        log.warn(String.format(
            "Cluster does not exists for verificationTaskId %s , txnName %s", verificationTaskId, txnName));
      } else {
        if (analysisDataDTO.getRisk().isGreaterThanEq(Risk.OBSERVE)) {
          unexpectedClustersWithRiskScore.put(cluster.getLabel(), analysisDataDTO.getScore());
        }
        /*
         This is just a performance fix to avoid saving too many frequency trend records.
         We should not have these many records in the first place but it is happening because of a bug in the annalysis.
         This is a short term fix to avoid performance issues.
        */
        if (cluster.getFrequencyTrend().size() > MAX_FREQUENCY_TREND_SIZE) {
          int freqListSize = cluster.getFrequencyTrend().size();
          cluster.setFrequencyTrend(
              cluster.getFrequencyTrend().subList(freqListSize - MAX_FREQUENCY_TREND_SIZE, freqListSize));
        }
        int index = cluster.getFrequencyTrend().size() - 1;
        while (index >= 0) {
          Frequency frequency = cluster.getFrequencyTrend().get(index);
          Instant timestamp = DateTimeUtils.epochMinuteToInstant(frequency.getTimestamp());
          if (timestamp.isBefore(startTime)) {
            break;
          }
          if (timestamp.isBefore(endTime)) {
            frequency.setRiskScore(analysisDataDTO.getScore());
          }
          index--;
        }
        bulkWriteOperation
            .find(hPersistence.createQuery(LogAnalysisCluster.class)
                      .filter(LogAnalysisCluster.UUID_KEY, cluster.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject(CVConstants.SET_KEY,
                new BasicDBObject(LogAnalysisClusterKeys.frequencyTrend, cluster.getFrequencyTrend())));
        numberOfBulkOperations++;
        if (numberOfBulkOperations > BULK_OPERATION_THRESHOLD) {
          bulkWriteOperation.execute();
          numberOfBulkOperations = 0;
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
        }
      }
    }
    if (numberOfBulkOperations > 0) {
      bulkWriteOperation.execute();
    }
  }

  private TimeSeriesRiskSummary buildRiskSummary(
      ServiceGuardTimeSeriesAnalysisDTO analysisDTO, Instant startTime, Instant endTime) {
    List<TimeSeriesRiskSummary.TransactionMetricRisk> metricRiskList = new ArrayList<>();
    analysisDTO.getTxnMetricAnalysisData().forEach(
        (txnName, metricMap) -> metricMap.forEach((metricIdentifier, metricData) -> {
          TimeSeriesRiskSummary.TransactionMetricRisk metricRisk = TimeSeriesRiskSummary.TransactionMetricRisk.builder()
                                                                       .transactionName(txnName)
                                                                       .metricIdentifier(metricIdentifier)
                                                                       .metricRisk(metricData.getRisk().getValue())
                                                                       .metricScore(metricData.getScore())
                                                                       .lastSeenTime(metricData.getLastSeenTime())
                                                                       .longTermPattern(metricData.isLongTermPattern())
                                                                       .build();
          metricRiskList.add(metricRisk);
        }));
    return TimeSeriesRiskSummary.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .analysisStartTime(startTime)
        .analysisEndTime(endTime)
        .transactionMetricRiskList(metricRiskList)
        .build();
  }

  private TimeSeriesCumulativeSums buildCumulativeSums(
      ServiceGuardTimeSeriesAnalysisDTO analysisDTO, Instant startTime, Instant endTime) {
    Map<String, Map<String, MetricSum>> cumulativeSumsMap = new HashMap<>();
    analysisDTO.getTxnMetricAnalysisData().forEach((txnName, metricMap) -> {
      cumulativeSumsMap.put(txnName, new HashMap<>());
      metricMap.forEach((metricIdentifier, metricSums) -> {
        if (metricSums.getCumulativeSums() != null) {
          TimeSeriesCumulativeSums.MetricSum sums = metricSums.getCumulativeSums().toMetricSum();
          sums.setMetricIdentifier(metricIdentifier);
          cumulativeSumsMap.get(txnName).put(metricIdentifier, sums);
        }
      });
    });

    List<TransactionMetricSums> transactionMetricSums =
        TimeSeriesCumulativeSums.convertMapToTransactionMetricSums(cumulativeSumsMap);

    return TimeSeriesCumulativeSums.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .transactionMetricSums(transactionMetricSums)
        .analysisStartTime(startTime)
        .analysisEndTime(endTime)
        .build();
  }

  private TimeSeriesShortTermHistory buildShortTermHistory(ServiceGuardTimeSeriesAnalysisDTO analysisDTO) {
    Map<String, Map<String, List<Double>>> shortTermHistoryMap = new HashMap<>();
    analysisDTO.getTxnMetricAnalysisData().forEach((txnName, metricMap) -> {
      shortTermHistoryMap.put(txnName, new HashMap<>());
      metricMap.forEach(
          (metricIdentifier, txnMetricData)
              -> shortTermHistoryMap.get(txnName).put(metricIdentifier, txnMetricData.getShortTermHistory()));
    });

    return TimeSeriesShortTermHistory.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .transactionMetricHistories(TimeSeriesShortTermHistory.convertFromMap(shortTermHistoryMap))
        .build();
  }

  @Override
  public List<TimeSeriesMetricDefinition> getTimeSeriesMetricDefinitions() {
    InputStream url = getClass().getResourceAsStream(LOG_METRIC_TEMPLATE_FILE);
    try {
      String metricDefinitionYaml = IOUtils.toString(url, StandardCharsets.UTF_8);
      YamlUtils yamlUtils = new YamlUtils();
      return yamlUtils.read(metricDefinitionYaml, new TypeReference<List<TimeSeriesMetricDefinition>>() {});
    } catch (IOException e) {
      log.error("Exception while reading metric template yaml file", e);
      throw new VerificationOperationException(
          ErrorCode.FILE_READ_FAILED, "Exception when fetching metric template from yaml file");
    }
  }
}
