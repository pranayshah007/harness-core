/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.beans.FeatureName.DISABLE_LOGML_NEURAL_NET;
import static io.harness.beans.FeatureName.DISABLE_SERVICEGUARD_LOG_ALERTS;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;
import static software.wings.common.VerificationConstants.DUMMY_HOST_NAME;
import static software.wings.common.VerificationConstants.GA_PER_MINUTE_CV_STATES;
import static software.wings.common.VerificationConstants.NON_HOST_PREVIOUS_ANALYSIS;
import static software.wings.common.VerificationConstants.NUM_LOG_RECORDS;
import static software.wings.common.VerificationConstants.PER_MINUTE_CV_STATES;
import static software.wings.service.intfc.analysis.ClusterLevel.H0;
import static software.wings.service.intfc.analysis.ClusterLevel.H1;
import static software.wings.service.intfc.analysis.ClusterLevel.H2;
import static software.wings.service.intfc.analysis.ClusterLevel.HF;
import static software.wings.service.intfc.analysis.ClusterLevel.L0;
import static software.wings.service.intfc.analysis.ClusterLevel.L1;
import static software.wings.service.intfc.analysis.ClusterLevel.L2;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.govern.Switch;
import io.harness.logging.Misc;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.persistence.HIterator;
import io.harness.service.intfc.ContinuousVerificationService;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GoogleDataStoreServiceImpl;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataKeys;
import software.wings.service.impl.analysis.ExpAnalysisInfo;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord.ExperimentalLogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.ExperimentalMessageComparisonResult;
import software.wings.service.impl.analysis.FeedbackAction;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisStatus;
import software.wings.service.impl.analysis.LogMLFeedbackRecord;
import software.wings.service.impl.analysis.LogRequest;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask.LearningEngineExperimentalAnalysisTaskKeys;
import software.wings.service.impl.splunk.LogAnalysisResult;
import software.wings.service.impl.splunk.SplunkAnalysisCluster;
import software.wings.service.impl.splunk.SplunkAnalysisCluster.MessageFrequency;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.log.LogsCVConfiguration;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DuplicateKeyException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateResults;

/**
 * Created by Praveen
 */
@Slf4j
public class LogAnalysisServiceImpl implements LogAnalysisService {
  private static final double HIGH_RISK_THRESHOLD = 50;
  private static final double MEDIUM_RISK_THRESHOLD = 25;

  private static final SecureRandom random = new SecureRandom();

  @Inject protected WingsPersistence wingsPersistence;
  @Inject private DataStoreService dataStoreService;
  @Inject private LearningEngineService learningEngineService;
  @Inject private VerificationManagerClientHelper managerClientHelper;
  @Inject private VerificationManagerClient managerClient;
  @Inject private HarnessMetricRegistry metricRegistry;
  @Inject private UsageMetricsHelper usageMetricsHelper;
  @Inject private ContinuousVerificationService continuousVerificationService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private CVActivityLogService cvActivityLogService;

  @Override
  public void bumpClusterLevel(StateType stateType, String stateExecutionId, String appId, String searchQuery,
      Set<String> host, long logCollectionMinute, ClusterLevel fromLevel, ClusterLevel toLevel) {
    log.info("For {} bumping cluster level  from {} to {} for minute {}", stateExecutionId, fromLevel, toLevel,
        logCollectionMinute);
    Query<LogDataRecord> query = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                     .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                     .filter(LogDataRecordKeys.clusterLevel, fromLevel)
                                     .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute);

    if (isNotEmpty(host)) {
      query = query.field(LogDataRecordKeys.host).in(host);
    }
    try {
      UpdateResults results = wingsPersistence.update(query,
          wingsPersistence.createUpdateOperations(LogDataRecord.class).set(LogDataRecordKeys.clusterLevel, toLevel));
      log.info("for {} bumped records {}", stateExecutionId, results.getUpdatedCount());
    } catch (DuplicateKeyException e) {
      log.warn(
          "duplicate update operation for state: {}, stateExecutionId: {}, searchQuery: {}, hosts: {}, logCollectionMinute: {}, from: {}, to: {}",
          stateType, stateExecutionId, searchQuery, host, logCollectionMinute, fromLevel, toLevel);
    }
  }

  @Override
  public void deleteClusterLevel(StateType stateType, String stateExecutionId, String appId, String searchQuery,
      Set<String> host, long logCollectionMinute, ClusterLevel... clusterLevels) {
    Query<LogDataRecord> query = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                     .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                     .field(LogDataRecordKeys.clusterLevel)
                                     .in(asList(clusterLevels))
                                     .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute);
    if (isNotEmpty(host)) {
      query = query.field(LogDataRecordKeys.host).in(host);
    }
    wingsPersistence.delete(query);
  }

  private void deleteClusterLevel(
      String cvConfigId, String host, int logCollectionMinute, ClusterLevel fromLevel, ClusterLevel toLevel) {
    Query<LogDataRecord> query = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                     .filter(LogDataRecordKeys.cvConfigId, cvConfigId)
                                     .filter(LogDataRecordKeys.clusterLevel, fromLevel);

    if (L2 == toLevel) {
      query = query.field(LogDataRecordKeys.logCollectionMinute).lessThanOrEq(logCollectionMinute);
    } else {
      query = query.filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute);
    }

    if (isNotEmpty(host)) {
      query = query.filter(LogDataRecordKeys.host, host);
    }
    boolean deleted = wingsPersistence.delete(query);
    log.info("Deleted clustered data for cvConfigId: {}, minute {}, fromLevel {}, toLevel {}, host {}, deleted {}",
        cvConfigId, logCollectionMinute, fromLevel, toLevel, host, deleted);
    try {
      query = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                  .filter(LogDataRecordKeys.cvConfigId, cvConfigId)
                  .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.getHeartBeatLevel(fromLevel));

      if (L2 == toLevel) {
        query = query.field(LogDataRecordKeys.logCollectionMinute).lessThanOrEq(logCollectionMinute);
      } else {
        query = query.filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute);
      }

      if (isNotEmpty(host)) {
        query = query.filter(LogDataRecordKeys.host, host);
      }
      UpdateResults updatedResults = wingsPersistence.update(query,
          wingsPersistence.createUpdateOperations(LogDataRecord.class)
              .set(LogDataRecordKeys.clusterLevel, ClusterLevel.getHeartBeatLevel(toLevel)));

      if (updatedResults.getUpdatedCount() > 0 && host == null) {
        log.info("Updated heartbeat record from {} to {} for min {} and cvConfigId {}",
            ClusterLevel.getHeartBeatLevel(fromLevel), ClusterLevel.getHeartBeatLevel(toLevel), logCollectionMinute,
            cvConfigId);
      }

      if (updatedResults.getUpdatedCount() == 0 && DUMMY_HOST_NAME.equals(host)) {
        log.error("did not update heartbeat from {} to {}  for min {} host {}", fromLevel, toLevel, logCollectionMinute,
            host);
      }
    } catch (DuplicateKeyException e) {
      log.error("for {} for hosts {} for min {} level is already updated to {}", cvConfigId, host, logCollectionMinute,
          toLevel);
    }
  }

  @Override
  public Boolean saveLogData(StateType stateType, String accountId, String appId, String cvConfigId,
      String stateExecutionId, String workflowId, String workflowExecutionId, String serviceId,
      ClusterLevel clusterLevel, String delegateTaskId, List<LogElement> logData) {
    try {
      if (isEmpty(cvConfigId) && !learningEngineService.isStateValid(appId, stateExecutionId)) {
        log.warn(
            "State is no longer active " + stateExecutionId + ". Sending delegate abort request " + delegateTaskId);
        return false;
      }
      log.info("inserting {}  pieces of log data for cvConfigId: {}, stateExecutionId: {}", logData.size(), cvConfigId,
          stateExecutionId);

      if (logData.isEmpty()) {
        return true;
      }

      boolean hasHeartBeat =
          logData.stream().anyMatch((LogElement data) -> Integer.parseInt(data.getClusterLabel()) < 0);

      /*
       * LOGZ, ELK, SUMO report cluster level L0. This is then clustered twice
       * in python and reported here as L1 and L2. Only L0 will have the heartbeat
       */
      if (clusterLevel == ClusterLevel.L0 && !hasHeartBeat) {
        log.error("Delegate reporting log records without a "
            + "heartbeat for state " + stateType + " : id " + stateExecutionId);
        return false;
      }

      /*
       * The only time we see data for Splunk is from the delegate at level L2. There is no
       * additional clustering for Splunk
       */
      if (stateType == StateType.SPLUNKV2 && !hasHeartBeat) {
        log.error("Delegate reporting log records without a "
            + "heartbeat for state " + stateType + " : id " + stateExecutionId);

        return false;
      }

      List<LogDataRecord> logDataRecords = LogDataRecord.generateDataRecords(stateType, appId, cvConfigId,
          stateExecutionId, workflowId, workflowExecutionId, serviceId, clusterLevel,
          ClusterLevel.getHeartBeatLevel(clusterLevel), logData, accountId);

      if (!checkIfL0AlreadyPresent(appId, cvConfigId, logDataRecords, stateExecutionId)) {
        return true;
      }
      wingsPersistence.saveIgnoringDuplicateKeys(logDataRecords);

      if (dataStoreService instanceof GoogleDataStoreServiceImpl && clusterLevel == L2) {
        try {
          logDataRecords.forEach(
              logRecord -> logRecord.setValidUntil(Date.from(OffsetDateTime.now().plusMonths(6).toInstant())));
          dataStoreService.save(LogDataRecord.class, logDataRecords, true);
        } catch (Exception e) {
          log.info("Error saving log records for cvConfig {} stateExecution {}", cvConfigId, stateExecutionId, e);
        }
      }

      // bump the level for clustered data
      long logCollectionMinute = logData.get(0).getLogCollectionMinute();
      String query = logData.get(0).getQuery();
      switch (clusterLevel) {
        case L0:
          break;
        case L1:
          String node = logData.get(0).getHost();
          bumpClusterLevel(stateType, stateExecutionId, appId, query, Collections.singleton(node), logCollectionMinute,
              ClusterLevel.getHeartBeatLevel(ClusterLevel.L0), ClusterLevel.getHeartBeatLevel(ClusterLevel.L1));
          deleteClusterLevel(stateType, stateExecutionId, appId, query, Collections.singleton(node),
              logCollectionMinute, ClusterLevel.L0);
          learningEngineService.markCompleted(accountId, workflowExecutionId, stateExecutionId, logCollectionMinute,
              MLAnalysisType.LOG_CLUSTER, ClusterLevel.L1);
          break;
        case L2:
          bumpClusterLevel(stateType, stateExecutionId, appId, query, emptySet(), logCollectionMinute,
              ClusterLevel.getHeartBeatLevel(ClusterLevel.L1), ClusterLevel.getHeartBeatLevel(L2));
          deleteClusterLevel(
              stateType, stateExecutionId, appId, query, emptySet(), logCollectionMinute, ClusterLevel.L1);
          learningEngineService.markCompleted(
              accountId, workflowExecutionId, stateExecutionId, logCollectionMinute, MLAnalysisType.LOG_CLUSTER, L2);
          break;
        default:
          Switch.unhandled(clusterLevel);
          return false;
      }
      metricRegistry.recordGaugeValue(NUM_LOG_RECORDS, null, logData.size());
      return true;
    } catch (Exception ex) {
      log.error("Save log data failed for {}", stateExecutionId, ex);
      return false;
    }
  }

  private boolean checkIfL0AlreadyPresent(
      String appId, String cvConfigId, List<LogDataRecord> logDataRecords, String stateExecutionId) {
    if (isEmpty(logDataRecords)) {
      return true;
    }
    String fieldNameForQuery = isEmpty(cvConfigId) ? LogDataRecordKeys.stateExecutionId : LogDataRecordKeys.cvConfigId;
    String fieldValueForQuery = isEmpty(cvConfigId) ? stateExecutionId : cvConfigId;

    Set<Long> clusteredMinutes = new HashSet<>();
    logDataRecords.stream().filter(logDataRecord -> logDataRecord.getClusterLevel() == H0).forEach(logDataRecord -> {
      // Assumption: We either save all the records or none of the records in a batch.
      Set<String> hostsForMinute = getHostsForMinute(
          appId, fieldNameForQuery, fieldValueForQuery, logDataRecord.getLogCollectionMinute(), H0, H1, H2, HF);
      if (isNotEmpty(hostsForMinute)
          && (isEmpty(stateExecutionId) || hostsForMinute.contains(logDataRecord.getHost()))) {
        clusteredMinutes.add(logDataRecord.getLogCollectionMinute());
      }
    });
    if (clusteredMinutes.isEmpty()) {
      return true;
    }

    log.info("for {} got logs for minutes {} which are already clustered", fieldValueForQuery, clusteredMinutes);
    return false;
  }

  @Override
  public boolean saveClusteredLogData(String appId, String cvConfigId, ClusterLevel clusterLevel,
      int logCollectionMinute, String host, List<LogElement> logData) {
    LogsCVConfiguration logsCVConfiguration = wingsPersistence.get(LogsCVConfiguration.class, cvConfigId);
    if (logsCVConfiguration == null) {
      log.info("No configuration found for {} in app {}. It may have been deleted.", cvConfigId, appId);
      return false;
    }
    if (isNotEmpty(logData)) {
      List<LogDataRecord> logDataRecords = LogDataRecord.generateDataRecords(logsCVConfiguration.getStateType(), appId,
          cvConfigId, null, null, null, logsCVConfiguration.getServiceId(), clusterLevel,
          ClusterLevel.getHeartBeatLevel(clusterLevel), logData, logsCVConfiguration.getAccountId());
      wingsPersistence.saveIgnoringDuplicateKeys(logDataRecords);
      log.info("Saved {} clustered data for cvConfig: {}, minute {}, toLevel {}, host {}", logDataRecords.size(),
          cvConfigId, logCollectionMinute, clusterLevel, host);

      if (dataStoreService instanceof GoogleDataStoreServiceImpl && L2 == clusterLevel) {
        try {
          dataStoreService.save(LogDataRecord.class, logDataRecords, true);
          log.info("Saved L2 clustered data to GoogleDatStore for cvConfig: {}, minute {}, toLevel {}", cvConfigId,
              logCollectionMinute, clusterLevel);

        } catch (Exception e) {
          log.info("for {} failed to save clusterd log data to google store", cvConfigId, e);
        }
      }
    }

    switch (clusterLevel) {
      case L0:
        break;
      case L1:
        deleteClusterLevel(cvConfigId, host, logCollectionMinute, ClusterLevel.L0, ClusterLevel.L1);
        Set<String> hosts =
            getHostsForMinute(appId, LogDataRecordKeys.cvConfigId, cvConfigId, logCollectionMinute, ClusterLevel.L0);
        if (isEmpty(hosts)) {
          deleteClusterLevel(cvConfigId, null, logCollectionMinute, L0, L1);
        }
        if (isEmpty(getHostsForMinute(appId, LogDataRecordKeys.cvConfigId, cvConfigId, logCollectionMinute, L0, H0))) {
          try {
            learningEngineService.markCompleted(logsCVConfiguration.getAccountId(), null,
                "LOGS_CLUSTER_L1_" + cvConfigId + "_" + logCollectionMinute, logCollectionMinute,
                MLAnalysisType.LOG_CLUSTER, ClusterLevel.L1);
          } catch (DuplicateKeyException e) {
            log.info(
                "for {} task for L1 clustering min {} has already marked completed", cvConfigId, logCollectionMinute);
          }
        }
        break;
      case L2:
        deleteClusterLevel(cvConfigId, null, logCollectionMinute, ClusterLevel.L1, L2);
        learningEngineService.markCompleted(logsCVConfiguration.getAccountId(), null,
            "LOGS_CLUSTER_L2_" + cvConfigId + "_" + logCollectionMinute, logCollectionMinute,
            MLAnalysisType.LOG_CLUSTER, L2);
        break;
      default:
        Switch.unhandled(clusterLevel);
        return false;
    }
    return true;
  }

  @Override
  public Set<LogDataRecord> getLogData(LogRequest logRequest, boolean compareCurrent, String workflowExecutionId,
      ClusterLevel clusterLevel, StateType stateType, String accountId) {
    Query<LogDataRecord> recordQuery;

    Set<LogDataRecord> rv = new HashSet<>();
    if (compareCurrent) {
      recordQuery = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                        .filter(LogDataRecordKeys.stateExecutionId, logRequest.getStateExecutionId())
                        .filter(LogDataRecordKeys.clusterLevel, clusterLevel)
                        .filter(LogDataRecordKeys.logCollectionMinute, logRequest.getLogCollectionMinute())
                        .field(LogDataRecordKeys.host)
                        .hasAnyOf(logRequest.getNodes());

      try (HIterator<LogDataRecord> records = new HIterator<>(recordQuery.fetch())) {
        while (records.hasNext()) {
          rv.add(records.next());
        }
      }

      log.info("returning " + rv.size() + " records for request: " + logRequest);

    } else {
      LogMLAnalysisRecord logMLAnalysisRecord =
          wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
              .filter(LogMLAnalysisRecordKeys.workflowExecutionId, workflowExecutionId)
              .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute))
              .get();
      if (logMLAnalysisRecord == null) {
        log.info("No analysis found for control data for state {} with executionId ", logRequest.getStateExecutionId(),
            workflowExecutionId);
        return rv;
      }

      log.info("For {} serving control data from {}", logRequest.getStateExecutionId(), logMLAnalysisRecord.getUuid());
      logMLAnalysisRecord.decompressLogAnalysisRecord();
      Map<String, List<SplunkAnalysisCluster>> eventsFromBaseline;
      if (logMLAnalysisRecord.isBaseLineCreated()) {
        eventsFromBaseline = logMLAnalysisRecord.getTest_events();
      } else {
        eventsFromBaseline = logMLAnalysisRecord.getControl_events();
      }
      if (isEmpty(eventsFromBaseline)) {
        log.info("No test events found for control data for state {} with analysisId ",
            logRequest.getStateExecutionId(), logMLAnalysisRecord.getUuid());
        return rv;
      }

      eventsFromBaseline.forEach((s, analysisClusters) -> {
        if (isEmpty(analysisClusters)) {
          return;
        }

        analysisClusters.forEach(analysisCluster -> {
          if (isEmpty(analysisCluster.getMessage_frequencies())) {
            log.error(
                "for state {} the control analysis {} has empty message frequencies. Control data for this execution may not be correct",
                logRequest.getStateExecutionId(), logMLAnalysisRecord.getUuid());
            return;
          }

          if (analysisCluster.getMessage_frequencies().size() > 1) {
            log.error("for state {} the control analysis {} has {} message frequencies",
                logRequest.getStateExecutionId(), logMLAnalysisRecord.getUuid(),
                analysisCluster.getMessage_frequencies());
          }

          MessageFrequency messageFrequency = analysisCluster.getMessage_frequencies().get(0);
          rv.add(LogDataRecord.builder()
                     .stateType(logMLAnalysisRecord.getStateType())
                     .workflowExecutionId(logMLAnalysisRecord.getWorkflowExecutionId())
                     .stateExecutionId(logMLAnalysisRecord.getStateExecutionId())
                     .query(logMLAnalysisRecord.getQuery())
                     .clusterLabel(Integer.toString(analysisCluster.getCluster_label()))
                     .host(messageFrequency.getHost())
                     .timeStamp(messageFrequency.getTime())
                     .logMessage(analysisCluster.getText())
                     .count(messageFrequency.getCount())
                     .build());
        });
      });
    }

    return rv;
  }

  @Override
  public Set<LogDataRecord> getLogData(String appId, String cvConfigId, ClusterLevel clusterLevel,
      int logCollectionMinute, int startMinute, int endMinute, LogRequest logRequest) {
    Preconditions.checkState(
        logCollectionMinute > 0 ? startMinute <= 0 && endMinute <= 0 : startMinute >= 0 && endMinute >= 0,
        "both logCollectionMinute and start end minute set");
    Preconditions.checkState(L0 != clusterLevel || (L0 == clusterLevel && isNotEmpty(logRequest.getNodes())),
        "for L0 -> L1 clustering nodes can not be empty, level: " + clusterLevel + " logRequest: " + logRequest);
    Set<LogDataRecord> logDataRecords = new HashSet<>();
    Query<LogDataRecord> recordQuery = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                           .filter(LogDataRecordKeys.cvConfigId, cvConfigId)
                                           .filter(LogDataRecordKeys.clusterLevel, clusterLevel);

    if (logCollectionMinute > 0) {
      recordQuery.filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute);
    } else {
      recordQuery.field(LogDataRecordKeys.logCollectionMinute)
          .greaterThanOrEq(startMinute)
          .field(LogDataRecordKeys.logCollectionMinute)
          .lessThanOrEq(endMinute);
    }

    if (isNotEmpty(logRequest.getNodes())) {
      recordQuery.field(LogDataRecordKeys.host).in(logRequest.getNodes());
    }

    try (HIterator<LogDataRecord> iterator = new HIterator<>(recordQuery.fetch())) {
      while (iterator.hasNext()) {
        logDataRecords.add(iterator.next());
      }
    }

    return logDataRecords;
  }

  private boolean deleteFeedbackHelper(String feedbackId) {
    dataStoreService.delete(LogMLFeedbackRecord.class, feedbackId);

    return true;
  }

  @Override
  public boolean deleteFeedback(String feedbackId) {
    Preconditions.checkState(isNotEmpty(feedbackId), "empty or null feedback id set");
    return deleteFeedbackHelper(feedbackId);
  }

  @Override
  public List<LogMLFeedbackRecord> getMLFeedback(
      String appId, String serviceId, String workflowId, String workflowExecutionId) {
    PageRequest<LogMLFeedbackRecord> feedbackRecordPageRequest =
        PageRequestBuilder.aPageRequest().withLimit(UNLIMITED).build();

    feedbackRecordPageRequest.addFilter("serviceId", Operator.EQ, serviceId);
    feedbackRecordPageRequest.addFilter("workflowId", Operator.EQ, workflowId);
    feedbackRecordPageRequest.addFilter("workflowExecutionId", Operator.EQ, workflowExecutionId);

    List<LogMLFeedbackRecord> records = dataStoreService.list(LogMLFeedbackRecord.class, feedbackRecordPageRequest);

    PageRequest<LogMLFeedbackRecord> feedbackRecordPageRequestServiceOnly =
        PageRequestBuilder.aPageRequest().withLimit(UNLIMITED).addFilter("serviceId", Operator.EQ, serviceId).build();

    List<LogMLFeedbackRecord> recordsServiceOnlyFilter =
        dataStoreService.list(LogMLFeedbackRecord.class, feedbackRecordPageRequestServiceOnly);

    Set<LogMLFeedbackRecord> recordSet = new HashSet<>();

    records.forEach(record -> recordSet.add(record));
    recordsServiceOnlyFilter.forEach(record -> recordSet.add(record));

    return new ArrayList<>(recordSet);
  }

  @Override
  public boolean isLogDataCollected(
      String appId, String stateExecutionId, String query, long logCollectionMinute, StateType stateType) {
    Query<LogDataRecord> splunkLogDataRecordQuery =
        wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
            .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
            .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute);
    return !splunkLogDataRecordQuery.asList().isEmpty();
  }

  @Override
  public String getLastSuccessfulWorkflowExecutionIdWithLogs(
      StateType stateType, String appId, String serviceId, String workflowId, String query) {
    // TODO should we limit the number of executions to search in ??
    List<String> successfulExecutions = new ArrayList<>();
    List<ContinuousVerificationExecutionMetaData> cvList =
        wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class, excludeAuthority)
            .filter(ContinuousVerificationExecutionMetaDataKeys.workflowId, workflowId)
            .filter(ContinuousVerificationExecutionMetaDataKeys.stateType, stateType)
            .filter(ContinuousVerificationExecutionMetaDataKeys.executionStatus, ExecutionStatus.SUCCESS)
            .order(Sort.descending(ContinuousVerificationExecutionMetaDataKeys.workflowStartTs))
            .asList();
    cvList.forEach(cvMetadata -> successfulExecutions.add(cvMetadata.getWorkflowExecutionId()));
    for (String successfulExecution : successfulExecutions) {
      if (wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
              .filter(LogDataRecordKeys.workflowExecutionId, successfulExecution)
              .filter(LogDataRecordKeys.clusterLevel, L2)
              .filter(LogDataRecordKeys.query, query)
              .count(upToOne)
          > 0) {
        return successfulExecution;
      }
    }
    if (isNotEmpty(successfulExecutions)) {
      return cvList.get(0).getWorkflowExecutionId();
    }
    log.warn("Could not get a successful workflow to find control nodes");
    return null;
  }

  @Override
  public boolean saveLogAnalysisRecords(LogMLAnalysisRecord mlAnalysisResponse, StateType stateType,
      Optional<String> taskId, Optional<Boolean> isFeedbackAnalysis) {
    mlAnalysisResponse.setStateType(stateType);
    boolean isAnalysisEmpty =
        isEmpty(mlAnalysisResponse.getControl_events()) && isEmpty(mlAnalysisResponse.getTest_events());
    mlAnalysisResponse.compressLogAnalysisRecord();
    if (!isFeedbackAnalysis.isPresent() || !isFeedbackAnalysis.get()) {
      mlAnalysisResponse.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    } else {
      mlAnalysisResponse.setAnalysisStatus(LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);
    }

    if (mlAnalysisResponse.getLogCollectionMinute() == -1 || !isAnalysisEmpty
        || (isFeedbackAnalysis.isPresent() && isFeedbackAnalysis.get())) {
      wingsPersistence.saveIgnoringDuplicateKeys(Collections.singletonList(mlAnalysisResponse));
      log.info("inserted ml LogMLAnalysisRecord to persistence layer for stateExecutionInstanceId: {}",
          mlAnalysisResponse.getStateExecutionId());
    }
    bumpClusterLevel(stateType, mlAnalysisResponse.getStateExecutionId(), mlAnalysisResponse.getAppId(),
        mlAnalysisResponse.getQuery(), emptySet(), mlAnalysisResponse.getLogCollectionMinute(),
        ClusterLevel.getHeartBeatLevel(L2), ClusterLevel.getFinal());
    logAnalysisSummaryMessage(mlAnalysisResponse);
    if (taskId.isPresent()) {
      learningEngineService.markCompleted(taskId.get());
    }
    return true;
  }

  private void logAnalysisSummaryMessage(LogMLAnalysisRecord mlAnalysisResponse) {
    if (isNotEmpty(mlAnalysisResponse.getAnalysisSummaryMessage())) {
      cvActivityLogService
          .getLogger(mlAnalysisResponse.getAccountId(), mlAnalysisResponse.getCvConfigId(),
              mlAnalysisResponse.getLogCollectionMinute(), mlAnalysisResponse.getStateExecutionId())
          .warn("Learning engine: " + mlAnalysisResponse.getAnalysisSummaryMessage());
    }
  }

  @Override
  public boolean save24X7LogAnalysisRecords(String appId, String cvConfigId, int analysisMinute,
      LogMLAnalysisRecord mlAnalysisResponse, Optional<String> taskId, Optional<Boolean> isFeedbackAnalysis) {
    mlAnalysisResponse.setValidUntil(Date.from(OffsetDateTime.now().plusMonths(1).toInstant()));
    LogsCVConfiguration logsCVConfiguration = wingsPersistence.get(LogsCVConfiguration.class, cvConfigId);
    mlAnalysisResponse.setAccountId(logsCVConfiguration.getAccountId());
    if (taskId.isPresent()) {
      LearningEngineAnalysisTask analysisTask = learningEngineService.getTaskById(taskId.get());
      Preconditions.checkNotNull(analysisTask);
      long currentEpoch = currentTimeMillis();
      long timeTaken = currentEpoch - analysisTask.getCreatedAt();
      log.info("Finished analysis: Analysis type: {}, time delay: {} seconds",
          isFeedbackAnalysis.isPresent() && isFeedbackAnalysis.get() ? MLAnalysisType.FEEDBACK_ANALYSIS.name()
                                                                     : MLAnalysisType.LOG_ML.name(),
          TimeUnit.MILLISECONDS.toSeconds(timeTaken));
    }
    if (isNotEmpty(logsCVConfiguration.getContextId())) {
      AnalysisContext analysisContext = wingsPersistence.get(AnalysisContext.class, logsCVConfiguration.getContextId());
      mlAnalysisResponse.setStateExecutionId(analysisContext.getStateExecutionId());
      mlAnalysisResponse.setStateType(analysisContext.getStateType());
    }
    Map<String, Map<String, SplunkAnalysisCluster>> unknownClusters = mlAnalysisResponse.getUnknown_clusters();
    Map<Integer, LogAnalysisResult> logAnalysisResultMap = mlAnalysisResponse.getLog_analysis_result();
    mlAnalysisResponse.compressLogAnalysisRecord();
    mlAnalysisResponse.setCvConfigId(cvConfigId);
    mlAnalysisResponse.setAppId(appId);
    mlAnalysisResponse.setLogCollectionMinute(analysisMinute);
    if (!isFeedbackAnalysis.isPresent() || !isFeedbackAnalysis.get()) {
      mlAnalysisResponse.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    } else {
      mlAnalysisResponse.setAnalysisStatus(LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);
    }
    if (wingsPersistence.createQuery(LogMLAnalysisRecord.class)
            .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
            .filter(LogMLAnalysisRecordKeys.analysisStatus, mlAnalysisResponse.getAnalysisStatus())
            .filter(LogMLAnalysisRecordKeys.logCollectionMinute, analysisMinute)
            .project(LogMLAnalysisRecordKeys.protoSerializedAnalyisDetails, false)
            .get()
        == null) {
      wingsPersistence.save(mlAnalysisResponse);
      mlAnalysisResponse.setUnknown_clusters(unknownClusters);
      mlAnalysisResponse.setLog_analysis_result(logAnalysisResultMap);
      if (mlAnalysisResponse.getAnalysisStatus() == LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE
          || managerClientHelper
                 .callManagerWithRetry(
                     managerClient.isFeatureEnabled(DISABLE_LOGML_NEURAL_NET, logsCVConfiguration.getAccountId()))
                 .getResource()) {
        if (!managerClientHelper
                 .callManagerWithRetry(managerClient.isFeatureEnabled(
                     DISABLE_SERVICEGUARD_LOG_ALERTS, logsCVConfiguration.getAccountId()))
                 .getResource()) {
          continuousVerificationService.triggerLogAnalysisAlertIfNecessary(
              cvConfigId, mlAnalysisResponse, analysisMinute);
        }
      }
    }

    wingsPersistence.update(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                .filter(LogDataRecordKeys.cvConfigId, cvConfigId)
                                .filter(LogDataRecordKeys.clusterLevel, H2)
                                .field(LogDataRecordKeys.logCollectionMinute)
                                .lessThanOrEq(analysisMinute),
        wingsPersistence.createUpdateOperations(LogDataRecord.class)
            .set(LogDataRecordKeys.clusterLevel, ClusterLevel.getFinal()));
    if (taskId.isPresent()) {
      learningEngineService.markCompleted(taskId.get());
    }
    return true;
  }

  @Override
  public boolean save24X7ExpLogAnalysisRecords(String appId, String cvConfigId, int analysisMinute,
      AnalysisComparisonStrategy comparisonStrategy, ExperimentalLogMLAnalysisRecord mlAnalysisResponse,
      Optional<String> taskId, Optional<Boolean> isFeedbackAnalysis) {
    mlAnalysisResponse.setValidUntil(Date.from(OffsetDateTime.now().plusMonths(1).toInstant()));
    List<ExperimentalMessageComparisonResult> comparisonResults = mlAnalysisResponse.getComparisonMsgPairs();
    mlAnalysisResponse.setCvConfigId(cvConfigId);
    mlAnalysisResponse.setAppId(appId);
    mlAnalysisResponse.setLogCollectionMinute(analysisMinute);
    if (!isFeedbackAnalysis.isPresent() || !isFeedbackAnalysis.get()) {
      mlAnalysisResponse.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    } else {
      mlAnalysisResponse.setAnalysisStatus(LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);
    }
    wingsPersistence.save(mlAnalysisResponse);

    if (taskId.isPresent()) {
      learningEngineService.markExpTaskCompleted(taskId.get());
    }

    if (isNotEmpty(comparisonResults)) {
      comparisonResults.forEach(result -> {
        result.setCvConfigId(cvConfigId);
        result.setLogCollectionMinute(mlAnalysisResponse.getLogCollectionMinute());
        result.setStateExecutionId(mlAnalysisResponse.getStateExecutionId());
        result.setModelVersion(mlAnalysisResponse.getModelVersion());
      });
      dataStoreService.save(ExperimentalMessageComparisonResult.class, comparisonResults, true);
    }
    return true;
  }

  @Override
  public Map<FeedbackAction, List<CVFeedbackRecord>> getUserFeedback(
      String cvConfigId, String stateExecutionId, String appId) {
    List<CVFeedbackRecord> feedbackRecords =
        managerClientHelper.callManagerWithRetry(managerClient.getFeedbackList(cvConfigId, stateExecutionId))
            .getResource();

    Map<FeedbackAction, List<CVFeedbackRecord>> actionRecordMap = new HashMap<>();
    for (FeedbackAction action : FeedbackAction.values()) {
      actionRecordMap.put(action, new ArrayList<>());
    }
    feedbackRecords.forEach(feedbackRecord -> {
      FeedbackAction actionTaken = feedbackRecord.getActionTaken();
      actionRecordMap.get(actionTaken).add(feedbackRecord);
    });
    return actionRecordMap;
  }

  @Override
  public boolean saveExperimentalLogAnalysisRecords(
      ExperimentalLogMLAnalysisRecord mlAnalysisResponse, StateType stateType, Optional<String> taskId) {
    mlAnalysisResponse.setStateType(stateType);

    // replace dots in test cluster
    if (mlAnalysisResponse.getControl_clusters() != null) {
      mlAnalysisResponse.setControl_clusters(getClustersWithDotsReplaced(mlAnalysisResponse.getControl_clusters()));
    }

    // replace dots in test cluster
    if (mlAnalysisResponse.getTest_clusters() != null) {
      mlAnalysisResponse.setTest_clusters(getClustersWithDotsReplaced(mlAnalysisResponse.getTest_clusters()));
    }

    // replace dots in test cluster
    if (mlAnalysisResponse.getUnknown_clusters() != null) {
      mlAnalysisResponse.setUnknown_clusters(getClustersWithDotsReplaced(mlAnalysisResponse.getUnknown_clusters()));
    }

    // replace dots in ignored cluster
    if (mlAnalysisResponse.getIgnore_clusters() != null) {
      mlAnalysisResponse.setIgnore_clusters(getClustersWithDotsReplaced(mlAnalysisResponse.getIgnore_clusters()));
    }

    if (mlAnalysisResponse.getLogCollectionMinute() == -1 || !isEmpty(mlAnalysisResponse.getControl_events())
        || !isEmpty(mlAnalysisResponse.getTest_events())) {
      wingsPersistence.delete(
          wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class, excludeAuthority)
              .filter(ExperimentalLogMLAnalysisRecordKeys.stateExecutionId, mlAnalysisResponse.getStateExecutionId())
              .filter(ExperimentalLogMLAnalysisRecordKeys.logCollectionMinute,
                  mlAnalysisResponse.getLogCollectionMinute()));

      wingsPersistence.saveIgnoringDuplicateKeys(Collections.singletonList(mlAnalysisResponse));
    }
    log.info("inserted ml LogMLAnalysisRecord to persistence layer for app: " + mlAnalysisResponse.getAppId()
        + " StateExecutionInstanceId: " + mlAnalysisResponse.getStateExecutionId());
    bumpClusterLevel(stateType, mlAnalysisResponse.getStateExecutionId(), mlAnalysisResponse.getAppId(),
        mlAnalysisResponse.getQuery(), emptySet(), mlAnalysisResponse.getLogCollectionMinute(),
        ClusterLevel.getHeartBeatLevel(L2), ClusterLevel.getFinal());
    if (taskId.isPresent()) {
      learningEngineService.markExpTaskCompleted(taskId.get());
    }
    return true;
  }

  private Map<String, Map<String, SplunkAnalysisCluster>> getClustersWithDotsReplaced(
      Map<String, Map<String, SplunkAnalysisCluster>> inputCluster) {
    if (inputCluster == null) {
      return null;
    }
    Map<String, Map<String, SplunkAnalysisCluster>> rv = new HashMap<>();
    for (Entry<String, Map<String, SplunkAnalysisCluster>> clusterEntry : inputCluster.entrySet()) {
      rv.put(clusterEntry.getKey(), new HashMap<>());
      for (Entry<String, SplunkAnalysisCluster> hostEntry : clusterEntry.getValue().entrySet()) {
        rv.get(clusterEntry.getKey()).put(Misc.replaceDotWithUnicode(hostEntry.getKey()), hostEntry.getValue());
      }
    }
    return rv;
  }

  @Override
  public LogMLAnalysisRecord getLogAnalysisRecords(
      String fieldName, String fieldValue, int analysisMinute, boolean isCompressed) {
    Query<LogMLAnalysisRecord> logMLAnalysisRecordQuery =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
            .filter(fieldName, fieldValue) // field will be either cvConfigId or stateExecutionId
            .field(LogMLAnalysisRecordKeys.logCollectionMinute)
            .lessThanOrEq(analysisMinute)
            .filter(LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE.name())
            .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute));

    if (fieldName.equals(LogMLAnalysisRecordKeys.cvConfigId)) {
      logMLAnalysisRecordQuery = logMLAnalysisRecordQuery.filter(LogMLAnalysisRecordKeys.deprecated, false);
    }
    LogMLAnalysisRecord logMLAnalysisRecord = logMLAnalysisRecordQuery.get();

    if (logMLAnalysisRecord != null) {
      // everything that has been json compressed should eventually move to protobuf compressed
      if (logMLAnalysisRecord.getAnalysisDetailsCompressedJson() != null) {
        logMLAnalysisRecord.decompressLogAnalysisRecord();
        logMLAnalysisRecord.compressLogAnalysisRecord();
        logMLAnalysisRecord.setAnalysisDetailsCompressedJson(null);
        wingsPersistence.save(logMLAnalysisRecord);
      }

      if (!isCompressed) {
        logMLAnalysisRecord.decompressLogAnalysisRecord();
      }
    }
    return logMLAnalysisRecord;
  }

  @Override
  public List<ExpAnalysisInfo> getExpAnalysisInfoList() {
    Query<ExperimentalLogMLAnalysisRecord> analysisRecords =
        wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class, excludeAuthority)
            .project(ExperimentalLogMLAnalysisRecordKeys.stateExecutionId, true)
            .project("appId", true)
            .project(ExperimentalLogMLAnalysisRecordKeys.stateType, true)
            .project(ExperimentalLogMLAnalysisRecordKeys.experiment_name, true)
            .project("createdAt", true)
            .project(ExperimentalLogMLAnalysisRecordKeys.envId, true)
            .project(ExperimentalLogMLAnalysisRecordKeys.workflowExecutionId, true);

    List<ExperimentalLogMLAnalysisRecord> experimentalLogMLAnalysisRecords = analysisRecords.asList();

    List<ExpAnalysisInfo> result = new ArrayList<>();
    experimentalLogMLAnalysisRecords.forEach(record -> {
      result.add(ExpAnalysisInfo.builder()
                     .stateExecutionId(record.getStateExecutionId())
                     .appId(record.getAppId())
                     .stateType(record.getStateType())
                     .createdAt(record.getCreatedAt())
                     .expName(record.getExperiment_name())
                     .envId(record.getEnvId())
                     .workflowExecutionId(record.getWorkflowExecutionId())
                     .build());
    });

    return result;
  }

  @Override
  public boolean reQueueExperimentalTask(String appId, String stateExecutionId) {
    Query<LearningEngineExperimentalAnalysisTask> tasks =
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.state_execution_id, stateExecutionId);
    return wingsPersistence
               .update(tasks,
                   wingsPersistence.createUpdateOperations(LearningEngineExperimentalAnalysisTask.class)
                       .set(LearningEngineExperimentalAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                       .set(LearningEngineExperimentalAnalysisTaskKeys.retry, 0))
               .getUpdatedCount()
        > 0;
  }

  @Override
  public boolean isAnalysisPresent(String stateExecutionId, String appId) {
    LogMLAnalysisRecord analysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                             .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
                                             .order(Sort.descending(LogDataRecordKeys.logCollectionMinute))
                                             .get();

    return analysisRecord != null;
  }

  @Override
  public boolean isAnalysisPresentForMinute(
      String cvConfigId, int analysisMinute, LogMLAnalysisStatus logMLAnalysisStatus) {
    LogMLAnalysisRecord analysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                             .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
                                             .filter(LogMLAnalysisRecordKeys.logCollectionMinute, analysisMinute)
                                             .filter(LogMLAnalysisRecordKeys.analysisStatus, logMLAnalysisStatus)
                                             .get();
    return analysisRecord != null;
  }

  @Override
  public void createAndSaveSummary(
      StateType stateType, String appId, String stateExecutionId, String query, String message, String accountId) {
    LogMLAnalysisRecord analysisRecord = LogMLAnalysisRecord.builder()
                                             .logCollectionMinute(-1)
                                             .stateType(stateType)
                                             .accountId(accountId)
                                             .appId(appId)
                                             .stateExecutionId(stateExecutionId)
                                             .query(query)
                                             .analysisSummaryMessage(message)
                                             .control_events(Collections.emptyMap())
                                             .test_events(Collections.emptyMap())
                                             .build();
    saveLogAnalysisRecords(analysisRecord, stateType, Optional.empty(), Optional.empty());
  }

  @Override
  public long getCollectionMinuteForLevel(String query, String appId, String stateExecutionId, StateType type,
      ClusterLevel clusterLevel, Set<String> nodes) {
    ClusterLevel heartBeat = ClusterLevel.getHeartBeatLevel(clusterLevel);

    while (true) {
      /**
       * Get the heartbeat records for L1.
       */
      try (HIterator<LogDataRecord> logHeartBeatRecordsIterator =
               new HIterator<>(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                   .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                   .filter(LogDataRecordKeys.clusterLevel, heartBeat)
                                   .field(LogDataRecordKeys.host)
                                   .in(nodes)
                                   .order(Sort.ascending(LogDataRecordKeys.logCollectionMinute))
                                   .fetch())) {
        if (!logHeartBeatRecordsIterator.hasNext()) {
          return -1;
        }

        long logCollectionMinute = -1;
        Set<String> hosts;

        {
          LogDataRecord logDataRecord = logHeartBeatRecordsIterator.next();
          logCollectionMinute = logDataRecord.getLogCollectionMinute();

          hosts = Sets.newHashSet(logDataRecord.getHost());
          while (logHeartBeatRecordsIterator.hasNext()) {
            LogDataRecord dataRecord = logHeartBeatRecordsIterator.next();
            if (dataRecord.getLogCollectionMinute() == logCollectionMinute) {
              hosts.add(dataRecord.getHost());
            }
          }
        }

        if (deleteIfStale(
                query, appId, stateExecutionId, type, hosts, logCollectionMinute, ClusterLevel.L1, heartBeat)) {
          continue;
        }

        Set<String> lookupNodes = new HashSet<>(nodes);

        for (String node : hosts) {
          lookupNodes.remove(node);
        }

        if (!lookupNodes.isEmpty()) {
          log.info("Still waiting for data for " + Arrays.toString(lookupNodes.toArray()) + " for " + stateExecutionId);
        }

        return lookupNodes.isEmpty() ? logCollectionMinute : -1;
      }
    }
  }

  @Override
  public boolean hasDataRecords(String query, String appId, String stateExecutionId, StateType type, Set<String> nodes,
      ClusterLevel level, long logCollectionMinute) {
    /**
     * Get the data records for the found heartbeat.
     */
    return wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
               .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
               .filter(LogDataRecordKeys.clusterLevel, level)
               .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
               .field(LogDataRecordKeys.host)
               .in(nodes)
               .count(upToOne)
        > 0;
  }

  @Override
  public Optional<LogDataRecord> getHearbeatRecordForL0(
      String appId, String stateExecutionId, StateType type, String host) {
    /**
     * Find heartbeat for L0 records. L0 heartbeat is H0.
     */
    Query<LogDataRecord> query =
        wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
            .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
            .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.getHeartBeatLevel(ClusterLevel.L0))
            .order(Sort.ascending(LogDataRecordKeys.logCollectionMinute));

    if (isNotEmpty(host)) {
      query = query.filter(LogDataRecordKeys.host, host);
    }

    LogDataRecord logDataRecords = query.get();

    // Nothing more to process. break.
    return logDataRecords == null ? Optional.empty() : Optional.of(logDataRecords);
  }

  @Override
  public int getEndTimeForLogAnalysis(AnalysisContext context) {
    if (isPerMinTaskWithAbsoluteTimestamp(context.getStateType(), context.getStateExecutionId())) {
      return (int) context.getStartDataCollectionMinute() + context.getTimeDuration() - 1;
    } else {
      return context.getTimeDuration() - 1;
    }
  }

  private long getLastProcessedMinute(String stateExecutionId) {
    LogDataRecord logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                       .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                       .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.getFinal())
                                       .order(Sort.descending(LogDataRecordKeys.logCollectionMinute))
                                       .get();
    return logDataRecords == null ? -1 : logDataRecords.getLogCollectionMinute();
  }

  @Override
  public boolean isProcessingComplete(String query, String appId, String stateExecutionId, StateType type,
      int timeDurationMins, long collectionMinute, String accountId) {
    if (isPerMinTaskWithAbsoluteTimestamp(type, stateExecutionId)) {
      return getLastProcessedMinute(stateExecutionId) - collectionMinute >= timeDurationMins - 1;
    } else {
      return getLastProcessedMinute(stateExecutionId) >= timeDurationMins - 1;
    }
  }

  private boolean isPerMinTaskWithAbsoluteTimestamp(StateType type, String stateExecutionId) {
    return PER_MINUTE_CV_STATES.contains(type) || GA_PER_MINUTE_CV_STATES.contains(type)
        || isCVTaskPerMinuteTaskEnabled(type, stateExecutionId);
  }

  // TODO: remove this once everything is moved to cvTask

  private boolean isCVTaskPerMinuteTaskEnabled(StateType stateType, String stateExecutionId) {
    if (StateType.SPLUNKV2 == stateType || StateType.ELK == stateType) {
      return true;
    }
    return false;
  }

  private boolean deleteIfStale(String query, String appId, String stateExecutionId, StateType type, Set<String> hosts,
      long logCollectionMinute, ClusterLevel clusterLevel, ClusterLevel heartBeat) {
    long lastProcessedMinute = getLastProcessedMinute(stateExecutionId);
    if (logCollectionMinute <= lastProcessedMinute) {
      log.info("deleting stale data for stateExecutionID = " + stateExecutionId + " logCollectionMinute "
          + logCollectionMinute);
      deleteClusterLevel(type, stateExecutionId, appId, query, hosts, logCollectionMinute, clusterLevel, heartBeat);
      return true;
    }
    return false;
  }

  @Override
  public long getMaxCVCollectionMinute(String appId, String cvConfigId) {
    LogDataRecord logDataRecord = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                      .filter(LogDataRecordKeys.cvConfigId, cvConfigId)
                                      .order(Sort.descending(LogDataRecordKeys.logCollectionMinute))
                                      .get();

    return logDataRecord == null ? -1 : logDataRecord.getLogCollectionMinute();
  }

  @Override
  public long getLogRecordMinute(String appId, String cvConfigId, ClusterLevel clusterLevel, OrderType orderType) {
    LogDataRecord logDataRecord =
        wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
            .filter(LogDataRecordKeys.cvConfigId, cvConfigId)
            .filter(LogDataRecordKeys.clusterLevel, clusterLevel)
            .order(orderType == OrderType.DESC ? Sort.descending(LogDataRecordKeys.logCollectionMinute)
                                               : Sort.ascending(LogDataRecordKeys.logCollectionMinute))
            .get();

    return logDataRecord == null ? -1 : logDataRecord.getLogCollectionMinute();
  }

  @Override
  public Set<String> getHostsForMinute(String appId, String fieldNameForQuery, String fieldValueForQuery,
      long logRecordMinute, ClusterLevel... clusterLevels) {
    Set<String> clusterLevelNames = new HashSet<>();
    for (ClusterLevel clusterLevel : clusterLevels) {
      clusterLevelNames.add(clusterLevel.name());
    }

    BasicDBObject logDataRecordQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(fieldNameForQuery, fieldValueForQuery));
    conditions.add(new BasicDBObject(LogDataRecordKeys.logCollectionMinute, logRecordMinute));
    conditions.add(new BasicDBObject(LogDataRecordKeys.clusterLevel, new BasicDBObject("$in", clusterLevelNames)));
    logDataRecordQuery.put("$and", conditions);

    return new HashSet<>(
        wingsPersistence.getCollection(LogDataRecord.class).distinct(LogDataRecordKeys.host, logDataRecordQuery));
  }

  @Override
  public long getMinuteForHost(String appId, String stateExecutionId, String hostName, ClusterLevel clusterLevel) {
    LogDataRecord record = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                               .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                               .filter(LogDataRecordKeys.host, hostName)
                               .filter(LogDataRecordKeys.clusterLevel, clusterLevel)
                               .order(Sort.ascending(LogDataRecordKeys.logCollectionMinute))
                               .get();
    if (record != null) {
      return record.getLogCollectionMinute();
    }
    return -1;
  }

  @Override
  public long getLastCVAnalysisMinute(String appId, String cvConfigId, LogMLAnalysisStatus status) {
    LogMLAnalysisRecord mlAnalysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                               .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
                                               .filter(LogMLAnalysisRecordKeys.analysisStatus, status)
                                               .project(LogMLAnalysisRecordKeys.logCollectionMinute, true)
                                               .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute))
                                               .get();
    return mlAnalysisRecord == null ? -1 : mlAnalysisRecord.getLogCollectionMinute();
  }

  @Override
  public long getLastWorkflowAnalysisMinute(String appId, String stateExecutionId, LogMLAnalysisStatus status) {
    LogMLAnalysisRecord mlAnalysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                               .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
                                               .filter(LogMLAnalysisRecordKeys.analysisStatus, status)
                                               .project(LogMLAnalysisRecordKeys.logCollectionMinute, true)
                                               .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute))
                                               .get();
    return mlAnalysisRecord == null ? -1 : mlAnalysisRecord.getLogCollectionMinute();
  }

  @Override
  public long getLastLogDataCollectedMinute(String query, String appId, String stateExecutionId, StateType type) {
    LogDataRecord logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                       .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                       .order(Sort.descending(LogDataRecordKeys.logCollectionMinute))
                                       .get();
    return logDataRecords == null ? -1 : logDataRecords.getLogCollectionMinute();
  }

  @Override
  public boolean createAndUpdateFeedbackAnalysis(String fieldName, String fieldValue, long analysisMinute) {
    Query<LogMLAnalysisRecord> query =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
            .filter(fieldName, fieldValue) // this will query with either stateExecutionId or cvConfigId
            .filter(LogMLAnalysisRecordKeys.logCollectionMinute, analysisMinute)
            .filter(LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);

    LogMLAnalysisRecord existingRecord = query.get();

    if (existingRecord == null) {
      log.error("Missing LE analysis record for " + fieldName + " and " + fieldValue
          + " and analysisMinute: " + analysisMinute);
      return false;
    }
    existingRecord.setUuid(generateUuid());
    existingRecord.setAnalysisStatus(LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);

    existingRecord.decompressLogAnalysisRecord();
    continuousVerificationService.triggerLogAnalysisAlertIfNecessary(
        existingRecord.getCvConfigId(), existingRecord, existingRecord.getLogCollectionMinute());
    existingRecord.compressLogAnalysisRecord();

    try {
      wingsPersistence.save(existingRecord);
      return true;

    } catch (Exception ex) {
      log.error("Error while creating a new Feedback Analysis for " + fieldName + " and " + fieldValue
              + " and analysisMinute: " + analysisMinute,
          ex);
    }
    return false;
  }

  @Override
  public Set<String> getCollectedNodes(AnalysisContext context, ClusterLevel level) {
    if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
      Set<String> nodes = Sets.newHashSet(context.getControlNodes().keySet());
      if (ClusterLevel.L2 == level) {
        nodes = new HashSet<>();
      }
      nodes.addAll(context.getTestNodes().keySet());
      return nodes;
    } else if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS
        && !context.isInspectHostsInLogs()) {
      // get the earliest H1 with hostname: NON_HOST_PREVIOUS_ANALYSIS
      // and get all the hosts that have the same logCollectionMinute as that
      long minute = getMinuteForHost(context.getAppId(), context.getStateExecutionId(), NON_HOST_PREVIOUS_ANALYSIS,
          ClusterLevel.getHeartBeatLevel(level));
      if (minute != -1) {
        return getHostsForMinute(context.getAppId(), LogDataRecordKeys.stateExecutionId, context.getStateExecutionId(),
            minute, ClusterLevel.getHeartBeatLevel(level));
      }
      return new HashSet<>();
    } else {
      return Sets.newHashSet(context.getTestNodes().keySet());
    }
  }

  public Optional<Long> getCreatedTimeOfLastCollection(CVConfiguration cvConfiguration) {
    LogDataRecord record = wingsPersistence.createQuery(LogDataRecord.class)
                               .filter(LogDataRecordKeys.cvConfigId, cvConfiguration.getUuid())
                               .field(LogDataRecordKeys.clusterLevel)
                               .in(ClusterLevel.getAllHeartbeatLevels())
                               .order(Sort.descending(LogDataRecord.BaseKeys.createdAt))
                               .get();
    return record == null ? Optional.empty() : Optional.of(record.getCreatedAt());
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(LogMLFeedbackRecord.class).filter(ACCOUNT_ID_KEY2, accountId));
  }
}
