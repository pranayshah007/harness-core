/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
import static software.wings.common.VerificationConstants.LOGS_HIGH_RISK_THRESHOLD;
import static software.wings.common.VerificationConstants.LOGS_MEDIUM_RISK_THRESHOLD;
import static software.wings.common.VerificationConstants.PREDECTIVE_HISTORY_MINUTES;
import static software.wings.service.impl.analysis.ContinuousVerificationServiceImpl.computeHeatMapUnits;

import static java.lang.Math.ceil;
import static java.lang.Math.min;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delegate.task.DataCollectionExecutorService;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.time.Timestamp;

import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.analysis.AnalysisServiceImpl.CLUSTER_TYPE;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisStatus;
import software.wings.service.impl.analysis.LogMLAnalysisSummary;
import software.wings.service.impl.analysis.LogMLClusterSummary;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.MetricAnalysisRecord.MetricAnalysisRecordKeys;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates.TimeSeriesMetricTemplatesKeys;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.splunk.LogMLClusterScores;
import software.wings.service.impl.splunk.SplunkAnalysisCluster;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.verification.CV24x7DashboardService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;
import software.wings.verification.HeatMap;
import software.wings.verification.HeatMapResolution;
import software.wings.verification.dashboard.HeatMapUnit;
import software.wings.verification.log.LogsCVConfiguration;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

/**
 * @author Praveen
 */
@Slf4j
public class CV24x7DashboardServiceImpl implements CV24x7DashboardService {
  @Inject WingsPersistence wingsPersistence;
  @Inject CVConfigurationService cvConfigurationService;
  @Inject AnalysisService analysisService;
  @Inject FeatureFlagService featureFlagService;
  @Inject private DataCollectionExecutorService dataCollectionService;

  @Override
  public List<HeatMap> getHeatMapForLogs(
      String accountId, String appId, String serviceId, long startTime, long endTime, boolean detailed) {
    List<HeatMap> rv = Collections.synchronizedList(new ArrayList<>());
    List<CVConfiguration> cvConfigurations = getCVConfigurations(appId, serviceId);
    if (isEmpty(cvConfigurations)) {
      log.info("No cv config found for appId={}, serviceId={}", appId, serviceId);
      return new ArrayList<>();
    }

    List<Callable<Void>> callables = new ArrayList<>();
    cvConfigurations.stream()
        .filter(cvConfig -> VerificationConstants.getLogAnalysisStates().contains(cvConfig.getStateType()))
        .forEach(cvConfig -> callables.add(() -> {
          cvConfigurationService.fillInServiceAndConnectorNames(cvConfig);
          String envName = cvConfig.getEnvName();
          log.info("Environment name {}", envName);
          final HeatMap heatMap = HeatMap.builder().cvConfiguration(cvConfig).build();
          rv.add(heatMap);

          List<HeatMapUnit> units = createAllHeatMapUnits(startTime, endTime, cvConfig);
          List<HeatMapUnit> resolvedUnits = resolveHeatMapUnits(units, startTime, endTime);
          heatMap.getRiskLevelSummary().addAll(resolvedUnits);
          return null;
        }));
    dataCollectionService.executeParrallel(callables);

    return rv;
  }

  private List<CVConfiguration> getCVConfigurations(String appId, String serviceId) {
    List<CVConfiguration> cvConfigurations = wingsPersistence.createQuery(CVConfiguration.class)
                                                 .filter("appId", appId)
                                                 .filter(CVConfigurationKeys.serviceId, serviceId)
                                                 .filter(CVConfigurationKeys.enabled24x7, true)
                                                 .asList();
    if (isEmpty(cvConfigurations)) {
      log.info("No cv config found for appId={}, serviceId={}", appId, serviceId);
      return new ArrayList<>();
    }
    return cvConfigurations;
  }

  private List<HeatMapUnit> createAllHeatMapUnits(long startTime, long endTime, CVConfiguration cvConfiguration) {
    long cronPollIntervalMs = TimeUnit.MINUTES.toMillis(CRON_POLL_INTERVAL_IN_MINUTES);
    Preconditions.checkState((endTime - startTime) >= cronPollIntervalMs);

    List<LogMLAnalysisRecord> records = getLogAnalysisRecordsInTimeRange(startTime, endTime, false, cvConfiguration);

    return computeHeatMapUnits(startTime, endTime, null, records);
  }

  private List<HeatMapUnit> resolveHeatMapUnits(List<HeatMapUnit> units, long startTime, long endTime) {
    List<HeatMapUnit> resolvedUnits = new ArrayList<>();
    HeatMapResolution heatMapResolution = HeatMapResolution.getResolution(startTime, endTime);

    // time duration represented by each read unit
    int unitDuration = heatMapResolution.getDurationOfHeatMapUnit(heatMapResolution);

    // number of small units to be merged into one reqd unit
    int eventsPerUnit = heatMapResolution.getEventsPerHeatMapUnit(heatMapResolution);

    // total number of read units
    int numberOfUnits = (int) ceil((double) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime) / unitDuration);

    log.info("total small units = {}, number of required units = {}", units.size(), numberOfUnits);

    for (int i = 0; i < numberOfUnits; i++) {
      // merge [i * eventsPerUnit, (i + 1) * eventsPerUnit)
      // [x, y) denotes x inclusive, y exclusive
      // Note: This works because the smallest unit is composed of exactly 1 event
      int startIndex = i * eventsPerUnit;
      int endIndex = min((i + 1) * eventsPerUnit, units.size());

      if (startIndex >= endIndex) {
        continue;
      }
      List<HeatMapUnit> subList = units.subList(startIndex, endIndex);
      if (subList.size() > 0) {
        resolvedUnits.add(merge(subList));
      }
    }
    return resolvedUnits;
  }

  private HeatMapUnit merge(List<HeatMapUnit> units) {
    HeatMapUnit mergedUnit = HeatMapUnit.builder()
                                 .startTime(units.get(0).getStartTime())
                                 .endTime(units.get(units.size() - 1).getEndTime())
                                 .overallScore(-2)
                                 .build();
    units.forEach(unit -> {
      if (unit.getScoreList() != null) {
        mergedUnit.updateOverallScore(unit.getOverallScore());
      }
    });

    return mergedUnit;
  }

  private List<LogMLAnalysisRecord> getLogAnalysisRecordsInTimeRange(
      long startTime, long endTime, boolean readDetails, CVConfiguration cvConfiguration) {
    Query<LogMLAnalysisRecord> analysisRecordQuery =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
            .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfiguration.getUuid())
            .field(LogMLAnalysisRecordKeys.logCollectionMinute)
            .lessThanOrEq(TimeUnit.MILLISECONDS.toMinutes(endTime))
            .project(LogMLAnalysisRecordKeys.analysisDetailsCompressedJson, readDetails)
            .project(LogMLAnalysisRecordKeys.protoSerializedAnalyisDetails, readDetails)
            .project(LogMLAnalysisRecordKeys.cluster_scores, readDetails);

    if (readDetails) {
      analysisRecordQuery = analysisRecordQuery.project(LogMLAnalysisRecordKeys.analysisStatus, true);
      analysisRecordQuery = analysisRecordQuery.project(LogMLAnalysisRecordKeys.logCollectionMinute, true);
    }

    if (readDetails) {
      analysisRecordQuery = analysisRecordQuery.field(LogMLAnalysisRecordKeys.logCollectionMinute)
                                .greaterThan(TimeUnit.MILLISECONDS.toMinutes(startTime));
    } else {
      analysisRecordQuery =
          analysisRecordQuery.field(LogMLAnalysisRecordKeys.logCollectionMinute)
              .greaterThanOrEq(TimeUnit.MILLISECONDS.toMinutes(startTime) + CRON_POLL_INTERVAL_IN_MINUTES);
    }

    // iterate and remove any dupes in the same time (LE vs FE)
    Map<Integer, Integer> analysisMinRecordIndexMap = new HashMap<>();
    List<Integer> indexesWithFeedback = new ArrayList<>();
    List<LogMLAnalysisRecord> records = new ArrayList<>();
    int index = 0;
    try (HIterator<LogMLAnalysisRecord> iterator = new HIterator<>(analysisRecordQuery.fetch())) {
      while (iterator.hasNext()) {
        LogMLAnalysisRecord record = iterator.next();
        record.decompressLogAnalysisRecord();
        if (analysisMinRecordIndexMap.containsKey(record.getLogCollectionMinute())) {
          Integer indexInMap = analysisMinRecordIndexMap.get(record.getLogCollectionMinute());
          LogMLAnalysisRecord prevRecord = indexInMap != null ? records.get(indexInMap) : null;
          if (prevRecord != null) {
            prevRecord.decompressLogAnalysisRecord();
            if (prevRecord.getAnalysisStatus() == LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE) {
              indexesWithFeedback.add(analysisMinRecordIndexMap.get(record.getLogCollectionMinute()));
            } else {
              indexesWithFeedback.add(index);
            }
          }
        }
        analysisMinRecordIndexMap.put(record.getLogCollectionMinute(), index);
        records.add(record);
        index++;
      }
    }

    // now remove all indexes with feedback.
    Collections.sort(indexesWithFeedback, Collections.reverseOrder());

    for (int ind : indexesWithFeedback) {
      records.remove(ind);
    }

    return records;
  }

  private LogMLAnalysisRecord getLastAnalysisRecord(String appId, String cvConfigId) {
    return wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
        .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
        .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute))
        .get();
  }

  @Override
  public LogMLAnalysisSummary getAnalysisSummary(String cvConfigId, Long startTime, Long endTime, String appId) {
    LogsCVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    if (!VerificationConstants.getLogAnalysisStates().contains(cvConfiguration.getStateType())) {
      log.error("Incorrect CVConfigID to fetch logAnalysisSummary {}", cvConfigId);
      return null;
    }

    List<LogMLAnalysisRecord> analysisRecords;
    if (startTime != null && endTime != null) {
      analysisRecords = getLogAnalysisRecordsInTimeRange(startTime, endTime, true, cvConfiguration);
    } else {
      analysisRecords = Arrays.asList(getLastAnalysisRecord(appId, cvConfigId));
    }

    if (analysisRecords == null) {
      return null;
    }

    analysisRecords.forEach(LogMLAnalysisRecord::decompressLogAnalysisRecord);

    // TODO: Incorporate user feedbacks into this.
    final LogMLAnalysisSummary analysisSummary = LogMLAnalysisSummary.builder().build();
    double totalScore = 0.0;
    int unknownFrequency = 0;
    for (LogMLAnalysisRecord record : analysisRecords) {
      analysisSummary.getControlClusters().addAll(
          analysisService.computeCluster(record.getControl_clusters(), Collections.emptyMap(), CLUSTER_TYPE.CONTROL));
      LogMLClusterScores logMLClusterScores =
          record.getCluster_scores() != null ? record.getCluster_scores() : new LogMLClusterScores();
      analysisSummary.getTestClusters().addAll(
          analysisService.computeCluster(record.getTest_clusters(), logMLClusterScores.getTest(), CLUSTER_TYPE.TEST));
      analysisSummary.getUnknownClusters().addAll(analysisService.computeCluster(
          record.getUnknown_clusters(), logMLClusterScores.getUnknown(), CLUSTER_TYPE.UNKNOWN));
      analysisSummary.getIgnoreClusters().addAll(
          analysisService.computeCluster(record.getIgnore_clusters(), Collections.emptyMap(), CLUSTER_TYPE.IGNORE));

      unknownFrequency += getUnexpectedFrequency(record.getTest_clusters());
      analysisSummary.setQuery(record.getQuery());
      totalScore += record.getScore();
    }

    if (cvConfiguration.is247LogsV2()) {
      analysisService.updateClustersFrequencyMapV2(analysisSummary.getUnknownClusters());
    }

    // if empty response and is not baseline then send baseline data
    if (analysisSummary.isEmptyResult()
        && TimeUnit.MILLISECONDS.toMinutes(endTime) > cvConfiguration.getBaselineEndMinute()) {
      LogMLAnalysisRecord record = wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                       .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
                                       .field(LogMLAnalysisRecordKeys.logCollectionMinute)
                                       .lessThanOrEq(cvConfiguration.getBaselineEndMinute())
                                       .filter(LogMLAnalysisRecordKeys.deprecated, false)
                                       .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute))
                                       .get();
      if (record != null) {
        record.decompressLogAnalysisRecord();
        analysisSummary.setControlClusters(
            analysisService.computeCluster(record.getControl_clusters(), Collections.emptyMap(), CLUSTER_TYPE.CONTROL));
      }
    }

    analysisSummary.setScore(totalScore / analysisRecords.size() * 100);

    RiskLevel riskLevel = RiskLevel.NA;

    int unknownClusters = 0;
    int highRiskClusters = 0;
    int mediumRiskCluster = 0;
    int lowRiskClusters = 0;
    if (isNotEmpty(analysisSummary.getUnknownClusters())) {
      for (LogMLClusterSummary clusterSummary : analysisSummary.getUnknownClusters()) {
        if (clusterSummary.getScore() > LOGS_HIGH_RISK_THRESHOLD) {
          ++highRiskClusters;
        } else if (clusterSummary.getScore() > LOGS_MEDIUM_RISK_THRESHOLD) {
          ++mediumRiskCluster;
        } else if (clusterSummary.getScore() > 0) {
          ++lowRiskClusters;
        }
      }
      riskLevel = highRiskClusters > 0 ? RiskLevel.HIGH
          : mediumRiskCluster > 0      ? RiskLevel.MEDIUM
          : lowRiskClusters > 0        ? RiskLevel.LOW
                                       : RiskLevel.HIGH;

      unknownClusters = analysisSummary.getUnknownClusters().size();
      analysisSummary.setHighRiskClusters(highRiskClusters);
      analysisSummary.setMediumRiskClusters(mediumRiskCluster);
      analysisSummary.setLowRiskClusters(lowRiskClusters);
    }

    if (unknownFrequency > 0) {
      analysisSummary.setHighRiskClusters(analysisSummary.getHighRiskClusters() + unknownFrequency);
      riskLevel = RiskLevel.HIGH;
    }

    String analysisSummaryMsg = "";
    if (highRiskClusters > 0 || mediumRiskCluster > 0 || lowRiskClusters > 0) {
      analysisSummaryMsg = analysisSummary.getHighRiskClusters() + " high risk, "
          + analysisSummary.getMediumRiskClusters() + " medium risk, " + analysisSummary.getLowRiskClusters()
          + " low risk anomalous cluster(s) found";
    } else if (unknownClusters > 0 || unknownFrequency > 0) {
      final int totalAnomalies = unknownClusters + unknownFrequency;
      analysisSummaryMsg = totalAnomalies == 1 ? totalAnomalies + " anomalous cluster found"
                                               : totalAnomalies + " anomalous clusters found";
    }
    long analysisEndMin = TimeUnit.MILLISECONDS.toMinutes(endTime);

    if (!featureFlagService.isEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, cvConfiguration.getAccountId())) {
      List<CVFeedbackRecord> feedbackRecords = analysisService.getFeedbacks(cvConfigId, null, false);
      Map<CLUSTER_TYPE, Map<Integer, CVFeedbackRecord>> clusterTypeRecordMap = new HashMap<>();
      feedbackRecords.forEach(cvFeedbackRecord -> {
        if (isNotEmpty(cvFeedbackRecord.getCvConfigId()) && cvFeedbackRecord.getCvConfigId().equals(cvConfigId)
            && cvFeedbackRecord.getAnalysisMinute() == analysisEndMin) {
          CLUSTER_TYPE type = cvFeedbackRecord.getClusterType();
          if (!clusterTypeRecordMap.containsKey(type)) {
            clusterTypeRecordMap.put(type, new HashMap<>());
          }

          clusterTypeRecordMap.get(cvFeedbackRecord.getClusterType())
              .put(cvFeedbackRecord.getClusterLabel(), cvFeedbackRecord);
        }
      });

      analysisService.updateClustersWithFeedback(
          clusterTypeRecordMap, CLUSTER_TYPE.CONTROL, analysisSummary.getControlClusters());
      analysisService.updateClustersWithFeedback(
          clusterTypeRecordMap, CLUSTER_TYPE.TEST, analysisSummary.getTestClusters());
      analysisService.updateClustersWithFeedback(
          clusterTypeRecordMap, CLUSTER_TYPE.UNKNOWN, analysisSummary.getUnknownClusters());
    }

    analysisSummary.setRiskLevel(riskLevel);
    analysisSummary.setAnalysisSummaryMessage(analysisSummaryMsg);
    analysisSummary.setStateType(cvConfiguration.getStateType());
    analysisSummary.setQuery(((LogsCVConfiguration) cvConfiguration).getQuery());
    updateFeedbacksWithJira(cvConfiguration, analysisSummary);
    return analysisSummary;
  }

  private int getUnexpectedFrequency(Map<String, Map<String, SplunkAnalysisCluster>> testClusters) {
    int unexpectedFrequency = 0;
    if (isEmpty(testClusters)) {
      return unexpectedFrequency;
    }
    for (Entry<String, Map<String, SplunkAnalysisCluster>> labelEntry : testClusters.entrySet()) {
      for (Entry<String, SplunkAnalysisCluster> hostEntry : labelEntry.getValue().entrySet()) {
        final SplunkAnalysisCluster analysisCluster = hostEntry.getValue();
        if (analysisCluster.isUnexpected_freq()) {
          unexpectedFrequency++;
          break;
        }
      }
    }

    return unexpectedFrequency;
  }

  private void updateFeedbacksWithJira(CVConfiguration cvConfiguration, LogMLAnalysisSummary summary) {
    List<CVFeedbackRecord> cvFeedbackRecords = analysisService.getFeedbacks(cvConfiguration.getUuid(), null, false);

    if (!isEmpty(cvFeedbackRecords)) {
      Map<String, CVFeedbackRecord> feedbackIdMap = new HashMap<>();
      cvFeedbackRecords.forEach(feedbackRecord -> feedbackIdMap.put(feedbackRecord.getUuid(), feedbackRecord));
      addJiraLinkToClusters(feedbackIdMap, summary.getControlClusters());
      addJiraLinkToClusters(feedbackIdMap, summary.getUnknownClusters());
      addJiraLinkToClusters(feedbackIdMap, summary.getTestClusters());
    }
  }

  private void addJiraLinkToClusters(
      Map<String, CVFeedbackRecord> feedbackRecordMap, List<LogMLClusterSummary> clusterSummary) {
    if (clusterSummary == null) {
      return;
    }
    clusterSummary.forEach(controlCluster -> {
      String feedbackId = controlCluster.getLogMLFeedbackId();
      if (isNotEmpty(feedbackId)) {
        // add in the Jira link if available
        if (feedbackRecordMap.containsKey(feedbackId)) {
          controlCluster.setJiraLink(feedbackRecordMap.get(feedbackId).getJiraLink());
        }
      }
    });
  }

  @Override
  public Map<String, Double> getMetricTags(
      String accountId, String appId, String cvConfigId, long startTime, long endTIme) {
    Set<String> tags = new HashSet<>();
    Map<String, Double> tagScoreMap = new HashMap<>();
    TimeSeriesMetricTemplates template = wingsPersistence.createQuery(TimeSeriesMetricTemplates.class, excludeAuthority)
                                             .filter(TimeSeriesMetricTemplatesKeys.cvConfigId, cvConfigId)
                                             .get();

    if (template != null) {
      template.getMetricTemplates().forEach((key, value) -> {
        if (isNotEmpty(value.getTags())) {
          tags.addAll(value.getTags());
        }
      });
    }
    if (tags.size() > 1) {
      tags.forEach(tag -> tagScoreMap.put(tag, computeOverallScoreForTag(startTime, endTIme, cvConfigId, tag)));
    }
    return tagScoreMap;
  }

  private Double computeOverallScoreForTag(long startTime, long endTime, String cvConfigId, String tag) {
    final Set<TimeSeriesMLAnalysisRecord> timeSeriesMLAnalysisRecords =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
            .filter(MetricAnalysisRecordKeys.cvConfigId, cvConfigId)
            .field(MetricAnalysisRecordKeys.analysisMinute)
            .greaterThanOrEq(TimeUnit.MILLISECONDS.toMinutes(startTime))
            .field(MetricAnalysisRecordKeys.analysisMinute)
            .lessThanOrEq(TimeUnit.MILLISECONDS.toMinutes(endTime))
            .filter(MetricAnalysisRecordKeys.tag, tag)
            .order(MetricAnalysisRecordKeys.analysisMinute)
            .project(MetricAnalysisRecordKeys.transactions, false)
            .project(MetricAnalysisRecordKeys.transactionsCompressedJson, false)
            .asList()
            .stream()
            .collect(Collectors.toSet());

    List<Double> scoreList = new ArrayList<>();
    timeSeriesMLAnalysisRecords.forEach(record -> {
      if (record.getRiskScore() == null) {
        scoreList.add(-1.0);
      } else {
        scoreList.add(record.getRiskScore());
      }
    });

    return scoreList.stream().mapToDouble(val -> val).average().orElse(0.0);
  }

  private long getCurrentAnalysisMinuteForTimeseries(CVConfiguration cvConfiguration) {
    TimeSeriesMLAnalysisRecord analysisRecord =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
            .filter(MetricAnalysisRecordKeys.cvConfigId, cvConfiguration.getUuid())
            .order(Sort.descending(MetricAnalysisRecordKeys.analysisMinute))
            .project(MetricAnalysisRecordKeys.analysisMinute, true)
            .project(MetricAnalysisRecordKeys.cvConfigId, true)
            .get();
    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    if (analysisRecord == null) {
      log.info("No analysis has been done for {}", cvConfiguration.getUuid());
      return TimeUnit.MINUTES.toMillis(currentMinute);
    } else {
      if (analysisRecord.getAnalysisMinute() + PREDECTIVE_HISTORY_MINUTES < currentMinute) {
        log.info("For {} It has been more than 2 hours since the last one. We will be attempting the one 2 hours ago",
            cvConfiguration.getUuid());
        long currentWindowEnd =
            TimeUnit.MINUTES.toMillis(currentMinute - PREDECTIVE_HISTORY_MINUTES + CRON_POLL_INTERVAL_IN_MINUTES);
        log.info("For {} Returning {} as the current window end time ", cvConfiguration.getUuid(), currentWindowEnd);
        return currentWindowEnd;
      } else {
        log.info(
            "For {}, the last analysis minute was {}", cvConfiguration.getUuid(), analysisRecord.getAnalysisMinute());
        return TimeUnit.MINUTES.toMillis(analysisRecord.getAnalysisMinute() + CRON_POLL_INTERVAL_IN_MINUTES);
      }
    }
  }

  private long getCurrentAnalysisMinuteForLogs(CVConfiguration cvConfiguration) {
    LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;
    long baselineStart = logsCVConfiguration.getBaselineStartMinute(),
         baselineEnd = logsCVConfiguration.getBaselineEndMinute();
    LogMLAnalysisRecord lastAnalysis = wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                                           .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfiguration.getUuid())
                                           .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute))
                                           .project(LogMLAnalysisRecordKeys.logCollectionMinute, true)
                                           .project(LogMLAnalysisRecordKeys.cvConfigId, true)
                                           .get();

    if (lastAnalysis == null) {
      log.info("There has been no analysis done for {}. So current window end time minute is {}",
          cvConfiguration.getUuid(), baselineStart + CRON_POLL_INTERVAL_IN_MINUTES);
      return TimeUnit.MINUTES.toMillis(baselineStart + CRON_POLL_INTERVAL_IN_MINUTES);
    }

    if (lastAnalysis.getLogCollectionMinute() > baselineStart && lastAnalysis.getLogCollectionMinute() < baselineEnd) {
      log.info("We are within the baseline window for {}", cvConfiguration.getUuid());
      return TimeUnit.MINUTES.toMillis(lastAnalysis.getLogCollectionMinute() + CRON_POLL_INTERVAL_IN_MINUTES);
    }

    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());

    // if the last analysis is baselineEnd, then current window is now.
    if (lastAnalysis.getLogCollectionMinute() == baselineEnd
        || lastAnalysis.getLogCollectionMinute() + PREDECTIVE_HISTORY_MINUTES
            >= TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary())) {
      return TimeUnit.MINUTES.toMillis(lastAnalysis.getLogCollectionMinute() + CRON_POLL_INTERVAL_IN_MINUTES);
    }

    if (lastAnalysis.getLogCollectionMinute() + PREDECTIVE_HISTORY_MINUTES < currentMinute) {
      log.info("For {} Last analysis was more than 2 hours ago, we will restart analysis from 2hours ago.",
          cvConfiguration.getUuid());
      long expectedStart = currentMinute - PREDECTIVE_HISTORY_MINUTES;
      if (Math.floorMod(expectedStart - 1, CRON_POLL_INTERVAL_IN_MINUTES) != 0) {
        expectedStart -= Math.floorMod(expectedStart - 1, CRON_POLL_INTERVAL_IN_MINUTES);
      }
      return TimeUnit.MINUTES.toMillis(expectedStart + CRON_POLL_INTERVAL_IN_MINUTES);
    }

    return 0;
  }

  @Override
  public long getCurrentAnalysisWindow(final String cvConfigId) {
    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);

    // First check to see if there are any LE tasks running for this configId.
    LearningEngineAnalysisTask analysisTask = wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                                                  .filter(LearningEngineAnalysisTaskKeys.cvConfigId, cvConfigId)
                                                  .field(LearningEngineAnalysisTaskKeys.executionStatus)
                                                  .in(Arrays.asList(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING))
                                                  .field(LearningEngineAnalysisTaskKeys.ml_analysis_type)
                                                  .in(Arrays.asList(MLAnalysisType.LOG_ML, MLAnalysisType.TIME_SERIES))
                                                  .order(LearningEngineAnalysisTaskKeys.analysis_minute)
                                                  .get();
    if (analysisTask != null) {
      log.info("For {} found an analysis task in {} status. Returning analysisMinute {}", cvConfigId,
          analysisTask.getExecutionStatus(), analysisTask.getAnalysis_minute());
      return TimeUnit.MINUTES.toMillis(analysisTask.getAnalysis_minute());
    }

    if (VerificationConstants.getLogAnalysisStates().contains(cvConfiguration.getStateType())) {
      log.info("The configuration {} is a log config. Calling getCurrentAnalysisMinuteForLogs", cvConfigId);
      return getCurrentAnalysisMinuteForLogs(cvConfiguration);
    } else if (VerificationConstants.getMetricAnalysisStates().contains(cvConfiguration.getStateType())) {
      log.info(
          "The configuration {} is a timeseries config. Calling getCurrentAnalysisMinuteForTimeseries", cvConfigId);
      return getCurrentAnalysisMinuteForTimeseries(cvConfiguration);
    } else {
      final String errMsg = "The stateType for cvConfigId: " + cvConfigId
          + "does not belong to timeseries or logs. Invalid State provided";
      log.error(errMsg);
      throw new WingsException(errMsg);
    }
  }
}
