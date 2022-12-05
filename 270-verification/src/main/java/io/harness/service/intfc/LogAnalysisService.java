/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.beans.SortOrder.OrderType;
import io.harness.validation.Create;

import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.ExpAnalysisInfo;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord;
import software.wings.service.impl.analysis.FeedbackAction;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisStatus;
import software.wings.service.impl.analysis.LogMLFeedbackRecord;
import software.wings.service.impl.analysis.LogRequest;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

public interface LogAnalysisService extends OwnedByAccount {
  @ValidationGroups(Create.class)
  Boolean saveLogData(@NotNull StateType stateType, String accountId, @NotNull String appId, String cvConfigId,
      String stateExecutionId, String workflowId, String workflowExecutionId, String serviceId,
      ClusterLevel clusterLevel, String delegateTaskId, @Valid List<LogElement> logData);

  boolean saveClusteredLogData(String appId, String cvConfigId, ClusterLevel clusterLevel, int logCollectionMinute,
      String host, List<LogElement> logData);

  @ValidationGroups(Create.class)
  Set<LogDataRecord> getLogData(LogRequest logRequest, boolean compareCurrent, String workflowExecutionId,
      ClusterLevel clusterLevel, StateType stateType, String accountId);

  Set<LogDataRecord> getLogData(String appId, String cvConfigId, ClusterLevel clusterLevel, int logCollectionMinute,
      int startMinute, int endMinute, LogRequest logRequest);

  boolean saveLogAnalysisRecords(LogMLAnalysisRecord mlAnalysisResponse, StateType stateType, Optional<String> taskId,
      Optional<Boolean> isFeedbackAnalysis);

  boolean save24X7LogAnalysisRecords(String appId, String cvConfigId, int analysisMinute,
      LogMLAnalysisRecord mlAnalysisResponse, Optional<String> taskId, Optional<Boolean> isFeedbackAnalysis);

  boolean save24X7ExpLogAnalysisRecords(String appId, String cvConfigId, int analysisMinute,
      AnalysisComparisonStrategy comparisonStrategy, ExperimentalLogMLAnalysisRecord mlAnalysisResponse,
      Optional<String> taskId, Optional<Boolean> isFeedbackAnalysis);

  LogMLAnalysisRecord getLogAnalysisRecords(
      String fieldName, String fieldValue, int analysisMinute, boolean isCompressed);

  List<ExpAnalysisInfo> getExpAnalysisInfoList();

  boolean reQueueExperimentalTask(String appId, String stateExecutionId);

  void deleteClusterLevel(StateType stateType, String stateExecutionId, String appId, String searchQuery,
      Set<String> host, long logCollectionMinute, ClusterLevel... clusterLevels);

  String getLastSuccessfulWorkflowExecutionIdWithLogs(
      StateType stateType, String appId, String serviceId, String workflowId, String query);

  boolean saveExperimentalLogAnalysisRecords(
      ExperimentalLogMLAnalysisRecord mlAnalysisResponse, StateType stateType, Optional<String> taskId);

  List<LogMLFeedbackRecord> getMLFeedback(
      String appId, String serviceId, String workflowId, String workflowExecutionId);

  boolean deleteFeedback(String feedbackId);

  boolean isProcessingComplete(String query, String appId, String stateExecutionId, StateType type,
      int timeDurationMins, long collectionMinute, String accountId);

  long getCollectionMinuteForLevel(String query, String appId, String stateExecutionId, StateType type,
      ClusterLevel clusterLevel, Set<String> testNodes);

  boolean hasDataRecords(String query, String appId, String stateExecutionId, StateType type, Set<String> nodes,
      ClusterLevel level, long logCollectionMinute);

  void bumpClusterLevel(StateType stateType, String stateExecutionId, String appId, String searchQuery,
      Set<String> host, long logCollectionMinute, ClusterLevel fromLevel, ClusterLevel toLevel);

  boolean isLogDataCollected(
      String appId, String stateExecutionId, String query, long logCollectionMinute, StateType splunkv2);

  boolean isAnalysisPresent(String stateExecutionId, String appId);

  boolean isAnalysisPresentForMinute(String cvConfigId, int analysisMinute, LogMLAnalysisStatus analysisStatus);

  void createAndSaveSummary(
      StateType stateType, String appId, String stateExecutionId, String query, String message, String accountId);

  Optional<LogDataRecord> getHearbeatRecordForL0(String appId, String stateExecutionId, StateType type, String host);

  long getMaxCVCollectionMinute(String appId, String cvConfigId);

  long getLogRecordMinute(String appId, String cvConfigId, ClusterLevel clusterLevel, OrderType orderType);

  Set<String> getHostsForMinute(String appId, String fieldNameForQuery, String fieldValueForQuery, long logRecordMinute,
      ClusterLevel... clusterLevels);

  long getMinuteForHost(String appId, String stateExecutionId, String hostName, ClusterLevel clusterLevel);

  long getLastCVAnalysisMinute(String appId, String cvConfigId, LogMLAnalysisStatus status);

  long getLastWorkflowAnalysisMinute(String appId, String stateExecutionId, LogMLAnalysisStatus status);

  long getLastLogDataCollectedMinute(String query, String appId, String stateExecutionId, StateType type);

  Map<FeedbackAction, List<CVFeedbackRecord>> getUserFeedback(String cvConfigId, String stateExecutionId, String appId);

  boolean createAndUpdateFeedbackAnalysis(String fieldName, String fieldValue, long analysisMinute);
  int getEndTimeForLogAnalysis(AnalysisContext context);
  Set<String> getCollectedNodes(AnalysisContext context, ClusterLevel level);
  Optional<Long> getCreatedTimeOfLastCollection(CVConfiguration cvConfiguration);
}
