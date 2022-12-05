/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;
import static software.wings.metrics.RiskLevel.NA;
import static software.wings.metrics.RiskLevel.getRiskLevel;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HIterator;

import software.wings.beans.Base.BaseKeys;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.RiskLevel;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord.ExperimentalLogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.ExperimentalMessageComparisonResult.ExperimentalMessageComparisonResultKeys;
import software.wings.service.impl.analysis.ExperimentalMetricAnalysisRecord.ExperimentalMetricAnalysisRecordKeys;
import software.wings.service.impl.analysis.MetricAnalysisRecord.MetricAnalysisRecordKeys;
import software.wings.service.impl.newrelic.ExperimentalMetricRecord;
import software.wings.service.impl.newrelic.ExperimentalMetricRecord.ExperimentalMetricAnalysis;
import software.wings.service.impl.newrelic.ExperimentalMetricRecord.ExperimentalMetricAnalysisValue;
import software.wings.service.impl.splunk.LogMLClusterScores;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.analysis.ExperimentalAnalysisService;
import software.wings.service.intfc.analysis.ExperimentalMetricAnalysisRecordService;
import software.wings.service.intfc.analysis.TimeSeriesMLAnalysisRecordService;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Sort;

@ValidateOnExecution
@Slf4j
public class ExperimentalAnalysisServiceImpl implements ExperimentalAnalysisService, OwnedByAccount {
  private static final double HIGH_RISK_THRESHOLD = 50;
  private static final double MEDIUM_RISK_THRESHOLD = 25;

  @Inject private WingsPersistence wingsPersistence;

  @Inject private AnalysisService analysisService;

  @Inject private DataStoreService dataStoreService;

  @Inject private ExperimentalMetricAnalysisRecordService experimentalMetricAnalysisRecordService;

  @Inject private TimeSeriesMLAnalysisRecordService timeSeriesMLAnalysisRecordService;

  @Override
  public ExperimentPerformance getMetricExpAnalysisPerformance(
      PageRequest<ExperimentalMetricAnalysisRecord> pageRequest) {
    pageRequest.addFieldsIncluded(ExperimentalMetricAnalysisRecordKeys.experimentStatus)
        .addFilter(ExperimentalMetricAnalysisRecordKeys.experimentStatus, EQ, ExperimentStatus.SUCCESS)
        .addFilter(MetricAnalysisRecordKeys.analysisMinute, SearchFilter.Operator.EQ, 0);

    long success =
        wingsPersistence.query(ExperimentalMetricAnalysisRecord.class, pageRequest, excludeAuthority).getTotal();

    pageRequest.addFieldsIncluded(ExperimentalMetricAnalysisRecordKeys.experimentStatus)
        .addFilter(ExperimentalMetricAnalysisRecordKeys.experimentStatus, EQ, ExperimentStatus.FAILURE)
        .addFilter(MetricAnalysisRecordKeys.analysisMinute, SearchFilter.Operator.EQ, 0);

    long failure =
        wingsPersistence.query(ExperimentalMetricAnalysisRecord.class, pageRequest, excludeAuthority).getTotal();

    pageRequest.addFieldsIncluded(ExperimentalMetricAnalysisRecordKeys.experimentStatus)
        .addFilter(MetricAnalysisRecordKeys.analysisMinute, SearchFilter.Operator.EQ, 0);

    long total =
        wingsPersistence.query(ExperimentalMetricAnalysisRecord.class, pageRequest, excludeAuthority).getTotal();

    return ExperimentPerformance.builder()
        .improvementPercentage(success * 100.0 / total)
        .declinePercentage(failure * 100.0 / total)
        .build();
  }

  @Override
  public PageResponse<ExpAnalysisInfo> getMetricExpAnalysisInfoList(
      PageRequest<ExperimentalMetricAnalysisRecord> pageRequest) {
    List<ExpAnalysisInfo> result = new ArrayList<>();
    Set<String> stateExecutionIds = new HashSet<>();

    pageRequest.addOrder(BaseKeys.createdAt, SortOrder.OrderType.DESC)
        .addFieldsIncluded(MetricAnalysisRecordKeys.stateExecutionId)
        .addFieldsIncluded(BaseKeys.appId)
        .addFieldsIncluded(MetricAnalysisRecordKeys.stateType)
        .addFieldsIncluded(ExperimentalMetricAnalysisRecordKeys.experimentName)
        .addFieldsIncluded(ExperimentalMetricAnalysisRecordKeys.envId)
        .addFieldsIncluded(MetricAnalysisRecordKeys.workflowExecutionId)
        .addFieldsIncluded(BaseKeys.createdAt)
        .addFilter(MetricAnalysisRecordKeys.analysisMinute, EQ, 0)
        .addFilter(ExperimentalMetricAnalysisRecordKeys.mismatched, EQ, true);

    PageResponse<ExperimentalMetricAnalysisRecord> analysisRecords =
        wingsPersistence.query(ExperimentalMetricAnalysisRecord.class, pageRequest, excludeAuthority);

    for (ExperimentalMetricAnalysisRecord record : analysisRecords) {
      if (!stateExecutionIds.contains(record.getStateExecutionId())) {
        result.add(ExpAnalysisInfo.builder()
                       .stateExecutionId(record.getStateExecutionId())
                       .appId(record.getAppId())
                       .stateType(record.getStateType())
                       .createdAt(record.getCreatedAt())
                       .expName(record.getExperimentName())
                       .envId(record.getEnvId())
                       .workflowExecutionId(record.getWorkflowExecutionId())
                       .mismatch(record.isMismatched())
                       .build());
        stateExecutionIds.add(record.getStateExecutionId());
      }
    }

    PageResponse<ExpAnalysisInfo> analysisInfo = new PageResponse<>();
    analysisInfo.setTotal(analysisRecords.getTotal());
    analysisInfo.setResponse(result);
    return analysisInfo;
  }

  @Override
  public List<ExpAnalysisInfo> getLogExpAnalysisInfoList() {
    List<ExpAnalysisInfo> result = new ArrayList<>();
    try (HIterator<ExperimentalLogMLAnalysisRecord> analysisRecords =
             new HIterator<>(wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class, excludeAuthority)
                                 .project(ExperimentalLogMLAnalysisRecordKeys.stateExecutionId, true)
                                 .project("appId", true)
                                 .project(ExperimentalLogMLAnalysisRecordKeys.stateType, true)
                                 .project(ExperimentalLogMLAnalysisRecordKeys.experiment_name, true)
                                 .project("createdAt", true)
                                 .project(ExperimentalLogMLAnalysisRecordKeys.envId, true)
                                 .project(ExperimentalLogMLAnalysisRecordKeys.workflowExecutionId, true)
                                 .fetch())) {
      while (analysisRecords.hasNext()) {
        final ExperimentalLogMLAnalysisRecord record = analysisRecords.next();
        result.add(ExpAnalysisInfo.builder()
                       .stateExecutionId(record.getStateExecutionId())
                       .appId(record.getAppId())
                       .stateType(record.getStateType())
                       .createdAt(record.getCreatedAt())
                       .expName(record.getExperiment_name())
                       .envId(record.getEnvId())
                       .workflowExecutionId(record.getWorkflowExecutionId())
                       .build());
      }
    }
    return result;
  }

  @Override
  public ExperimentalMetricRecord markExperimentStatus(
      String stateExecutionId, StateType stateType, String experimentName, ExperimentStatus experimentStatus) {
    List<ExperimentalMetricAnalysisRecord> experimentalAnalysisRecords =
        wingsPersistence.createQuery(ExperimentalMetricAnalysisRecord.class, excludeAuthority)
            .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .filter(ExperimentalMetricAnalysisRecordKeys.experimentName, experimentName)
            .order(Sort.descending(MetricAnalysisRecordKeys.analysisMinute))
            .asList();
    experimentalAnalysisRecords.forEach(record -> record.setExperimentStatus(experimentStatus));
    wingsPersistence.save(experimentalAnalysisRecords);
    return getExperimentalMetricAnalysisSummary(stateExecutionId, stateType, experimentName);
  }

  @Override
  public ExperimentalMetricRecord getExperimentalMetricAnalysisSummary(
      String stateExecutionId, StateType stateType, String expName) {
    ExperimentalMetricAnalysisRecord experimentalAnalysisRecord =
        experimentalMetricAnalysisRecordService.getLastAnalysisRecord(stateExecutionId, expName);
    TimeSeriesMLAnalysisRecord analysisRecord =
        timeSeriesMLAnalysisRecordService.getLastAnalysisRecord(stateExecutionId);

    ExperimentalMetricRecord experimentalMetricRecord =
        getExperimentalMetricAnalysisSummary(stateExecutionId, stateType, experimentalAnalysisRecord, analysisRecord);
    return filterMismatchedAnalysisForRecord(experimentalMetricRecord);
  }

  private ExperimentalMetricRecord filterMismatchedAnalysisForRecord(
      ExperimentalMetricRecord experimentalMetricRecord) {
    Preconditions.checkArgument(experimentalMetricRecord.isMismatch(),
        "Requested state execution id has similar actual and experimental analysis");

    ExperimentalMetricRecord filteredRecord =
        ExperimentalMetricRecord.builder()
            .workflowExecutionId(experimentalMetricRecord.getWorkflowExecutionId())
            .stateExecutionId(experimentalMetricRecord.getStateExecutionId())
            .cvConfigId(experimentalMetricRecord.getCvConfigId())
            .analysisMinute(experimentalMetricRecord.getAnalysisMinute())
            .stateType(experimentalMetricRecord.getStateType())
            .mlAnalysisType(experimentalMetricRecord.getMlAnalysisType())
            .baseLineExecutionId(experimentalMetricRecord.getBaseLineExecutionId())
            .mismatch(experimentalMetricRecord.isMismatch())
            .experimentStatus(experimentalMetricRecord.getExperimentStatus())
            .riskLevel(experimentalMetricRecord.getRiskLevel())
            .experimentalRiskLevel(experimentalMetricRecord.getExperimentalRiskLevel())
            .build();

    List<ExperimentalMetricAnalysis> filteredMetricAnalysis = new ArrayList<>();
    for (ExperimentalMetricAnalysis metricAnalysis : experimentalMetricRecord.getMetricAnalysis()) {
      if (metricAnalysis.isMismatch()) {
        List<ExperimentalMetricAnalysisValue> filteredMetricValues =
            metricAnalysis.getMetricValues()
                .stream()
                .filter(ExperimentalMetricAnalysisValue::isMismatch)
                .collect(Collectors.toList());
        filteredMetricAnalysis.add(ExperimentalMetricAnalysis.builder()
                                       .metricName(metricAnalysis.getMetricName())
                                       .tag(metricAnalysis.getTag())
                                       .riskLevel(metricAnalysis.getRiskLevel())
                                       .experimentalRiskLevel(metricAnalysis.getExperimentalRiskLevel())
                                       .mismatch(metricAnalysis.isMismatch())
                                       .metricValues(filteredMetricValues)
                                       .build());
      }
    }

    filteredRecord.setMetricAnalysis(filteredMetricAnalysis);
    return filteredRecord;
  }

  @Override
  public void updateMismatchStatusForExperimentalRecord(String stateExecutionId, Integer analysisMinute) {
    if (stateExecutionId == null || analysisMinute == null) {
      log.info("Cannot update experimental status for state execution id null");
      return;
    }
    ExperimentalMetricAnalysisRecord experimentalMetricAnalysisRecord =
        experimentalMetricAnalysisRecordService.getAnalysisRecordForMinute(stateExecutionId, analysisMinute);
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        timeSeriesMLAnalysisRecordService.getAnalysisRecordForMinute(stateExecutionId, analysisMinute);

    if (timeSeriesMLAnalysisRecord == null || experimentalMetricAnalysisRecord == null
        || experimentalMetricAnalysisRecord.isMismatched()) {
      return;
    }

    ExperimentalMetricRecord experimentalMetricRecord = getExperimentalMetricAnalysisSummary(stateExecutionId,
        timeSeriesMLAnalysisRecord.getStateType(), experimentalMetricAnalysisRecord, timeSeriesMLAnalysisRecord);

    experimentalMetricAnalysisRecord.setMismatched(experimentalMetricRecord.isMismatch());

    wingsPersistence.save(experimentalMetricAnalysisRecord);
  }

  private void validateActualAndExperimentalRecords(ExperimentalMetricAnalysisRecord experimentalAnalysisRecord,
      TimeSeriesMLAnalysisRecord analysisRecord, String stateExecutionId) {
    if (analysisRecord == null) {
      log.info("Actual analysis not done for this state execution id: {}", stateExecutionId);
      throw new InvalidRequestException("Actual analysis not done for this state execution id");
    }

    if (experimentalAnalysisRecord == null) {
      log.info("Experimental analysis not done for this state execution id: {}", stateExecutionId);
      throw new InvalidRequestException("Experimental analysis not done for this state execution id");
    }

    analysisRecord.decompress(false);
    experimentalAnalysisRecord.decompress(false);

    if (isEmpty(analysisRecord.getTransactions())) {
      log.info("No transactions recorded for actual analysis of state execution id: {}", stateExecutionId);
      throw new InvalidRequestException("No transactions recorded for actual analysis");
    }

    if (isEmpty(experimentalAnalysisRecord.getTransactions())) {
      log.info("Experimental transactions not found: {}", stateExecutionId);
      throw new InvalidRequestException("No transactions recorded for experimental analysis");
    }
  }

  private void setGlobalRiskForExperimentalRecord(
      List<ExperimentalMetricAnalysis> metricAnalysis, ExperimentalMetricRecord metricRecord) {
    RiskLevel riskLevel = NA;
    RiskLevel experimentalRiskLevel = NA;

    if (isNotEmpty(metricAnalysis)) {
      riskLevel =
          Collections.max(metricAnalysis, Comparator.comparingInt(analysis -> analysis.getRiskLevel().getRisk()))
              .getRiskLevel();
      experimentalRiskLevel =
          Collections
              .max(metricAnalysis, Comparator.comparingInt(analysis -> analysis.getExperimentalRiskLevel().getRisk()))
              .getExperimentalRiskLevel();
    }

    boolean mismatch = experimentalRiskLevel != riskLevel;

    metricRecord.setRiskLevel(riskLevel);
    metricRecord.setExperimentalRiskLevel(experimentalRiskLevel);
    metricRecord.setMismatch(mismatch);
  }

  private void updateMetricNameForDynatrace(
      List<ExperimentalMetricAnalysis> metricAnalysis, ExperimentalMetricRecord metricRecord) {
    if (metricRecord.getStateType() == StateType.DYNA_TRACE && !isEmpty(metricAnalysis)) {
      for (ExperimentalMetricAnalysis analysis : metricAnalysis) {
        String metricName = analysis.getMetricName();
        String[] split = metricName.split(":");
        if (split == null || split.length == 1) {
          analysis.setDisplayName(metricName);
          analysis.setFullMetricName(metricName);
          continue;
        }
        String btName = split[0];
        String fullBTName = btName + " (" + metricName.substring(btName.length() + 1) + ")";
        analysis.setDisplayName(btName);
        analysis.setFullMetricName(fullBTName);
      }
    }
  }

  private ExperimentalMetricAnalysis getExperimentalMetricAnalysisForTxn(
      TimeSeriesMLTxnSummary txnSummary, TimeSeriesMLTxnSummary experimentalTxnSummary) {
    RiskLevel globalRisk = RiskLevel.NA;
    RiskLevel globalExperimentalRisk = RiskLevel.NA;

    List<ExperimentalMetricAnalysisValue> metricValues = new ArrayList<>();

    for (Map.Entry<String, TimeSeriesMLMetricSummary> metricSummaryEntry : txnSummary.getMetrics().entrySet()) {
      TimeSeriesMLMetricSummary metricSummary = metricSummaryEntry.getValue();
      TimeSeriesMLMetricSummary experimentalMetricSummary =
          experimentalTxnSummary == null || isEmpty(experimentalTxnSummary.getMetrics())
          ? null
          : experimentalTxnSummary.getMetrics().get(metricSummaryEntry.getKey());

      RiskLevel riskLevel = getRiskLevel(metricSummary.getMax_risk());
      RiskLevel experimentalRiskLevel =
          experimentalMetricSummary == null ? NA : getRiskLevel(experimentalMetricSummary.getMax_risk());

      if (riskLevel.compareTo(globalRisk) < 0) {
        globalRisk = riskLevel;
      }

      if (experimentalRiskLevel.compareTo(globalExperimentalRisk) < 0) {
        globalExperimentalRisk = experimentalRiskLevel;
      }

      boolean mismatch = experimentalRiskLevel != riskLevel;

      metricValues.add(ExperimentalMetricAnalysisValue.builder()
                           .name(metricSummary.getMetric_name())
                           .type(metricSummary.getMetric_type())
                           .alertType(metricSummary.getAlert_type())
                           .riskLevel(riskLevel)
                           .experimentalRiskLevel(experimentalRiskLevel)
                           .controlValue(metricSummary.getControl_avg())
                           .testValue(metricSummary.getTest_avg())
                           .mismatch(mismatch)
                           .build());
    }

    boolean mismatch = globalExperimentalRisk != globalRisk;

    return ExperimentalMetricAnalysis.builder()
        .metricName(txnSummary.getTxn_name())
        .tag(txnSummary.getTxn_tag())
        .metricValues(metricValues)
        .riskLevel(globalRisk)
        .experimentalRiskLevel(globalExperimentalRisk)
        .mismatch(mismatch)
        .build();
  }

  private ExperimentalMetricRecord getExperimentalMetricAnalysisSummary(String stateExecutionId, StateType stateType,
      ExperimentalMetricAnalysisRecord experimentalAnalysisRecord, TimeSeriesMLAnalysisRecord analysisRecord) {
    validateActualAndExperimentalRecords(experimentalAnalysisRecord, analysisRecord, stateExecutionId);

    ExperimentalMetricRecord metricRecord = ExperimentalMetricRecord.builder()
                                                .workflowExecutionId(analysisRecord.getWorkflowExecutionId())
                                                .stateExecutionId(stateExecutionId)
                                                .cvConfigId(analysisRecord.getCvConfigId())
                                                .analysisMinute(analysisRecord.getAnalysisMinute())
                                                .stateType(stateType)
                                                .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
                                                .baseLineExecutionId(analysisRecord.getBaseLineExecutionId())
                                                .mismatch(experimentalAnalysisRecord.isMismatched())
                                                .experimentStatus(experimentalAnalysisRecord.getExperimentStatus())
                                                .build();

    List<ExperimentalMetricAnalysis> metricAnalysis = new ArrayList<>();

    for (Map.Entry<String, TimeSeriesMLTxnSummary> txnSummaryEntry : analysisRecord.getTransactions().entrySet()) {
      TimeSeriesMLTxnSummary txnSummary = txnSummaryEntry.getValue();
      TimeSeriesMLTxnSummary experimentalTxnSummary =
          experimentalAnalysisRecord.getTransactions().get(txnSummaryEntry.getKey());

      metricAnalysis.add(getExperimentalMetricAnalysisForTxn(txnSummary, experimentalTxnSummary));
    }

    metricRecord.setMetricAnalysis(metricAnalysis);
    setGlobalRiskForExperimentalRecord(metricAnalysis, metricRecord);

    updateMetricNameForDynatrace(metricAnalysis, metricRecord);

    return metricRecord;
  }

  @Override
  public LogMLAnalysisSummary getExperimentalAnalysisSummary(
      String stateExecutionId, String appId, StateType stateType, String expName) {
    ExperimentalLogMLAnalysisRecord analysisRecord =
        wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class, excludeAuthority)
            .filter(ExperimentalLogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .order(Sort.descending(ExperimentalLogMLAnalysisRecordKeys.logCollectionMinute))
            .get();
    if (analysisRecord == null) {
      return null;
    }
    LogMLClusterScores logMLClusterScores =
        analysisRecord.getCluster_scores() != null ? analysisRecord.getCluster_scores() : new LogMLClusterScores();
    final LogMLAnalysisSummary analysisSummary =
        LogMLAnalysisSummary.builder()
            .query(analysisRecord.getQuery())
            .score(analysisRecord.getScore() * 100)
            .controlClusters(analysisService.computeCluster(
                analysisRecord.getControl_clusters(), Collections.emptyMap(), AnalysisServiceImpl.CLUSTER_TYPE.CONTROL))
            .testClusters(analysisService.computeCluster(
                analysisRecord.getTest_clusters(), logMLClusterScores.getTest(), AnalysisServiceImpl.CLUSTER_TYPE.TEST))
            .unknownClusters(analysisService.computeCluster(analysisRecord.getUnknown_clusters(),
                logMLClusterScores.getUnknown(), AnalysisServiceImpl.CLUSTER_TYPE.UNKNOWN))
            .ignoreClusters(analysisService.computeCluster(
                analysisRecord.getIgnore_clusters(), Collections.emptyMap(), AnalysisServiceImpl.CLUSTER_TYPE.IGNORE))
            .build();

    if (!analysisRecord.isBaseLineCreated()) {
      analysisSummary.setTestClusters(analysisSummary.getControlClusters());
      analysisSummary.setControlClusters(new ArrayList<>());
    }

    RiskLevel riskLevel = RiskLevel.NA;
    String analysisSummaryMsg = isEmpty(analysisRecord.getAnalysisSummaryMessage())
        ? analysisSummary.getControlClusters().isEmpty()
            ? "No baseline data for the given queries. This will be baseline for the next run."
            : analysisSummary.getTestClusters().isEmpty()
            ? "No new data for the given queries. Showing baseline data if any."
            : "No anomaly found"
        : analysisRecord.getAnalysisSummaryMessage();

    int unknownClusters = 0;
    int highRiskClusters = 0;
    int mediumRiskCluster = 0;
    int lowRiskClusters = 0;
    if (isNotEmpty(analysisSummary.getUnknownClusters())) {
      for (LogMLClusterSummary clusterSummary : analysisSummary.getUnknownClusters()) {
        if (clusterSummary.getScore() > HIGH_RISK_THRESHOLD) {
          ++highRiskClusters;
        } else if (clusterSummary.getScore() > MEDIUM_RISK_THRESHOLD) {
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

    int unknownFrequency = analysisService.getUnexpectedFrequency(analysisRecord.getTest_clusters());
    if (unknownFrequency > 0) {
      analysisSummary.setHighRiskClusters(analysisSummary.getHighRiskClusters() + unknownFrequency);
      riskLevel = RiskLevel.HIGH;
    }

    if (highRiskClusters > 0 || mediumRiskCluster > 0 || lowRiskClusters > 0) {
      analysisSummaryMsg = analysisSummary.getHighRiskClusters() + " high risk, "
          + analysisSummary.getMediumRiskClusters() + " medium risk, " + analysisSummary.getLowRiskClusters()
          + " low risk anomalous cluster(s) found";
    } else if (unknownClusters > 0 || unknownFrequency > 0) {
      final int totalAnomalies = unknownClusters + unknownFrequency;
      analysisSummaryMsg = totalAnomalies == 1 ? totalAnomalies + " anomalous cluster found"
                                               : totalAnomalies + " anomalous clusters found";
    }

    analysisSummary.setRiskLevel(riskLevel);
    analysisSummary.setAnalysisSummaryMessage(analysisSummaryMsg);
    return analysisSummary;
  }

  @Override
  public List<ExperimentalMessageComparisonResult> getMessagePairsToVote(String serviceId) {
    log.info("Getting msg pairs to vote {}", serviceId);
    List<ExperimentalMessageComparisonResult> messagesToShow = new ArrayList<>();
    User currentUser = UserThreadLocal.get();

    try (HIterator<CVConfiguration> cvConfigurationHIterator =
             new HIterator<>(wingsPersistence.createQuery(CVConfiguration.class, excludeAuthority)
                                 .filter(CVConfigurationKeys.serviceId, serviceId)
                                 .fetch())) {
      while (cvConfigurationHIterator.hasNext() && messagesToShow.size() <= 10) {
        CVConfiguration cvConfiguration = cvConfigurationHIterator.next();
        PageRequest<ExperimentalMessageComparisonResult> comparisonResultPageRequest =
            PageRequestBuilder.aPageRequest()
                .addFilter(ExperimentalMessageComparisonResultKeys.cvConfigId, Operator.EQ, cvConfiguration.getUuid())
                .addFilter(ExperimentalMessageComparisonResultKeys.numVotes, Operator.LT, 3)
                .build();
        log.info("Querying GDS for serviceID {}, cvConfigId {}", serviceId, cvConfiguration.getUuid());
        List<ExperimentalMessageComparisonResult> comparisonResults =
            dataStoreService.list(ExperimentalMessageComparisonResult.class, comparisonResultPageRequest);
        log.info("Got {} comparison results from GDS for msg pairs to vote. serviceID {}, cvConfigId {}",
            comparisonResults.size(), serviceId, cvConfiguration.getUuid());
        if (isNotEmpty(comparisonResults)) {
          for (ExperimentalMessageComparisonResult result : comparisonResults) {
            if (messagesToShow.size() >= 10) {
              break;
            }

            if (isEmpty(result.getUserVotes())) {
              messagesToShow.add(result);
              continue;
            }

            if (!result.getUserVotes().containsKey(currentUser.getName())) {
              messagesToShow.add(result);
              continue;
            }
          }
        }
      }
    }
    log.info("Returning {} msg pairs to vote. serviceID {}", messagesToShow.size(), serviceId);
    return messagesToShow;
  }

  @Override
  public boolean saveMessagePairsToVote(String serviceId, Map<String, String> userVotes) {
    User currentUser = UserThreadLocal.get();
    if (isNotEmpty(userVotes)) {
      List<ExperimentalMessageComparisonResult> resultsToSave = new ArrayList<>();
      userVotes.forEach((uuid, vote) -> {
        ExperimentalMessageComparisonResult result =
            dataStoreService.getEntity(ExperimentalMessageComparisonResult.class, uuid);
        if (result.getUserVotes() == null) {
          result.setUserVotes(new HashMap<>());
        }
        result.getUserVotes().put(currentUser.getName(), vote);
        result.incrementNumVotes();
        resultsToSave.add(result);
      });
      dataStoreService.save(ExperimentalMessageComparisonResult.class, resultsToSave, false);
    }
    return true;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class).filter(ACCOUNT_ID_KEY2, accountId));
  }
}
