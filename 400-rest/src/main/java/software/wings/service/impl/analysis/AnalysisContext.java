/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.Misc.replaceDotWithUnicode;
import static io.harness.logging.Misc.replaceUnicodeWithDot;

import static software.wings.common.VerificationConstants.ML_RECORDS_TTL_MONTHS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.version.ServiceApiVersion;

import software.wings.beans.Base;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.Nullable;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

@Data
@FieldNameConstants(innerTypeName = "AnalysisContextKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "verificationServiceTask", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class AnalysisContext extends Base implements PersistentRegularIterable, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("task_Unique_Idx")
                 .unique(true)
                 .field(AnalysisContextKeys.stateExecutionId)
                 .field(AnalysisContextKeys.executionStatus)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("timeSeriesAnalysisIterationIdx")
                 .field(AnalysisContextKeys.analysisType)
                 .field(AnalysisContextKeys.executionStatus)
                 .field(AnalysisContextKeys.timeSeriesAnalysisIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("logAnalysisIterationIdx")
                 .field(AnalysisContextKeys.analysisType)
                 .field(AnalysisContextKeys.executionStatus)
                 .field(AnalysisContextKeys.logAnalysisIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("workflowDataCollectionIdx")
                 .field(AnalysisContextKeys.stateType)
                 .field(AnalysisContextKeys.perMinCollectionFinished)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("cvTaskCreationIndex")
                 .field(AnalysisContextKeys.cvTasksCreated)
                 .field(AnalysisContextKeys.cvTaskCreationIteration)
                 .build())
        .build();
  }

  @FdIndex private String accountId;
  private String workflowId;
  private String workflowExecutionId;
  @FdIndex private String stateExecutionId;
  private String serviceId;
  private String predictiveCvConfigId;
  private int predictiveHistoryMinutes;
  private Map<String, String> controlNodes;
  private Map<String, String> testNodes;
  private String query;
  private boolean isSSL;
  private int appPort;
  private AnalysisComparisonStrategy comparisonStrategy;
  private int timeDuration;
  private StateType stateType;
  private String analysisServerConfigId;
  private String correlationId;
  private int smooth_window;
  private int tolerance;
  private String prevWorkflowExecutionId;
  private int minimumRequestsPerMinute;
  private int comparisonWindow;
  private int parallelProcesses;
  private Map<String, List<TimeSeries>> timeSeriesToCollect = new HashMap<>();
  private boolean runTillConvergence;
  private String delegateTaskId;
  private String envId;
  private String hostNameField;
  private MLAnalysisType analysisType;
  @FdIndex private ExecutionStatus executionStatus;
  private long startDataCollectionMinute;
  // Collection interval in minutes
  private int collectionInterval;
  private ServiceApiVersion version = ServiceApiVersion.values()[ServiceApiVersion.values().length - 1];
  private DataCollectionInfo dataCollectionInfo;
  private DataCollectionInfoV2 dataCollectionInfov2;
  private boolean cvTasksCreated;
  private String customThresholdRefId;

  private int retry;
  // This needs to be String to boolean map because FeatureFlags can be removed which can cause deserialization issue.
  private final Map<String, Boolean> featureFlags = new HashMap<>();
  @FdIndex private Long timeSeriesAnalysisIteration;
  @FdIndex private Long logAnalysisIteration;
  @FdIndex private Long logClusterIteration;
  @FdIndex private long cvTaskCreationIteration;
  @FdIndex private Long feedbackIteration;
  @FdIndex private Long workflowDataCollectionIteration;
  private int initialDelaySeconds;
  private int dataCollectionIntervalMins;
  private boolean isHistoricalDataCollection;
  private boolean inspectHostsInLogs;
  @Nullable private Integer newNodesTrafficShiftPercent;
  private boolean skipVerification;
  private boolean perMinCollectionFinished;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());

  private String managerVersion;

  @Builder
  private AnalysisContext(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath, boolean syncFromGit, String accountId, String workflowId,
      String workflowExecutionId, String stateExecutionId, String serviceId, String predictiveCvConfigId,
      int predictiveHistoryMinutes, Map<String, String> controlNodes, Map<String, String> testNodes, String query,
      boolean isSSL, int appPort, AnalysisComparisonStrategy comparisonStrategy, int timeDuration, StateType stateType,
      String analysisServerConfigId, String correlationId, int smooth_window, int tolerance,
      String prevWorkflowExecutionId, int minimumRequestsPerMinute, int comparisonWindow, int parallelProcesses,
      Map<String, List<TimeSeries>> timeSeriesToCollect, boolean runTillConvergence, String delegateTaskId,
      MLAnalysisType analysisType, ExecutionStatus executionStatus, String managerVersion, String envId,
      String hostNameField, int collectionInterval, long startDataCollectionMinute,
      DataCollectionInfo dataCollectionInfo, int initialDelaySeconds, int dataCollectionIntervalMins,
      boolean isHistoricalDataCollection, String customThresholdRefId, boolean inspectHostsInLogs,
      Integer newNodesTrafficShiftPercent, boolean skipVerification, boolean perMinCollectionFinished) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath, syncFromGit);
    this.accountId = accountId;
    this.workflowId = workflowId;
    this.workflowExecutionId = workflowExecutionId;
    this.stateExecutionId = stateExecutionId;
    this.serviceId = serviceId;
    this.predictiveHistoryMinutes = predictiveHistoryMinutes;
    this.predictiveCvConfigId = predictiveCvConfigId;
    setControlNodes(controlNodes);
    setTestNodes(testNodes);
    this.query = query;
    this.isSSL = isSSL;
    this.appPort = appPort;
    this.comparisonStrategy = comparisonStrategy;
    this.timeDuration = timeDuration;
    this.stateType = stateType;
    this.analysisServerConfigId = analysisServerConfigId;
    this.correlationId = correlationId;
    this.smooth_window = smooth_window;
    this.tolerance = tolerance;
    this.prevWorkflowExecutionId = prevWorkflowExecutionId;
    this.minimumRequestsPerMinute = minimumRequestsPerMinute;
    this.comparisonWindow = comparisonWindow;
    this.parallelProcesses = parallelProcesses;
    this.timeSeriesToCollect = timeSeriesToCollect == null ? new HashMap<>() : timeSeriesToCollect;
    this.runTillConvergence = runTillConvergence;
    this.delegateTaskId = delegateTaskId;
    this.executionStatus = executionStatus == null ? ExecutionStatus.QUEUED : executionStatus;
    this.analysisType = analysisType;
    this.validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
    this.version = ServiceApiVersion.values()[ServiceApiVersion.values().length - 1];
    this.retry = 0;
    this.managerVersion = managerVersion;
    this.envId = envId;
    this.hostNameField = hostNameField;
    this.collectionInterval = collectionInterval;
    this.startDataCollectionMinute = startDataCollectionMinute;
    this.dataCollectionInfo = dataCollectionInfo;
    this.initialDelaySeconds = initialDelaySeconds;
    this.dataCollectionIntervalMins = dataCollectionIntervalMins;
    this.isHistoricalDataCollection = isHistoricalDataCollection;
    this.customThresholdRefId = customThresholdRefId;
    this.inspectHostsInLogs = inspectHostsInLogs;
    this.newNodesTrafficShiftPercent = newNodesTrafficShiftPercent;
    this.skipVerification = skipVerification;
    this.perMinCollectionFinished = perMinCollectionFinished;
  }

  public LogClusterContext getClusterContext() {
    return LogClusterContext.builder()
        .appId(appId)
        .workflowId(workflowId)
        .workflowExecutionId(workflowExecutionId)
        .stateExecutionId(stateExecutionId)
        .serviceId(serviceId)
        .controlNodes(getControlNodes().keySet())
        .testNodes(getTestNodes().keySet())
        .query(query)
        .isSSL(isSSL)
        .appPort(appPort)
        .accountId(accountId)
        .stateType(stateType)
        .build();
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (AnalysisContextKeys.timeSeriesAnalysisIteration.equals(fieldName)) {
      this.timeSeriesAnalysisIteration = nextIteration;
      return;
    }
    if (AnalysisContextKeys.logAnalysisIteration.equals(fieldName)) {
      this.logAnalysisIteration = nextIteration;
      return;
    }
    if (AnalysisContextKeys.logClusterIteration.equals(fieldName)) {
      this.logClusterIteration = nextIteration;
      return;
    }
    if (AnalysisContextKeys.cvTaskCreationIteration.equals(fieldName)) {
      this.cvTaskCreationIteration = nextIteration;
      return;
    }
    if (AnalysisContextKeys.feedbackIteration.equals(fieldName)) {
      this.feedbackIteration = nextIteration;
      return;
    }
    if (AnalysisContextKeys.workflowDataCollectionIteration.equals(fieldName)) {
      this.workflowDataCollectionIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (AnalysisContextKeys.timeSeriesAnalysisIteration.equals(fieldName)) {
      return this.timeSeriesAnalysisIteration;
    }
    if (AnalysisContextKeys.logAnalysisIteration.equals(fieldName)) {
      return this.logAnalysisIteration;
    }
    if (AnalysisContextKeys.logClusterIteration.equals(fieldName)) {
      return this.logClusterIteration;
    }
    if (AnalysisContextKeys.cvTaskCreationIteration.equals(fieldName)) {
      return this.cvTaskCreationIteration;
    }
    if (AnalysisContextKeys.feedbackIteration.equals(fieldName)) {
      return this.feedbackIteration;
    }
    if (AnalysisContextKeys.workflowDataCollectionIteration.equals(fieldName)) {
      return this.workflowDataCollectionIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public final void setControlNodes(Map<String, String> controlNodes) {
    this.controlNodes = updateNodesReplaceDots(controlNodes);
  }

  public Map<String, String> getControlNodes() {
    if (controlNodes == null) {
      return new HashMap<>();
    }
    return updateNodesReplaceUniCode(controlNodes);
  }

  public final void setTestNodes(Map<String, String> testNodes) {
    this.testNodes = updateNodesReplaceDots(testNodes);
  }

  public Map<String, String> getTestNodes() {
    if (testNodes == null) {
      return new HashMap<>();
    }
    return updateNodesReplaceUniCode(testNodes);
  }

  private Map<String, String> updateNodesReplaceUniCode(Map<String, String> nodes) {
    Map<String, String> updatedNodes = new HashMap<>();
    nodes.forEach((host, groupName) -> updatedNodes.put(replaceUnicodeWithDot(host), groupName));
    return updatedNodes;
  }

  private Map<String, String> updateNodesReplaceDots(Map<String, String> nodes) {
    Map<String, String> updatedNodes = new HashMap<>();
    if (isEmpty(nodes)) {
      return updatedNodes;
    }
    nodes.forEach((host, groupName) -> updatedNodes.put(replaceDotWithUnicode(host), groupName));
    return updatedNodes;
  }

  public void setFeatureFlag(FeatureName featureFlag, Boolean enabled) {
    featureFlags.put(featureFlag.toString(), enabled);
  }

  public boolean isFeatureFlagEnabled(FeatureName featureFlag) {
    return featureFlags.getOrDefault(featureFlag.toString(), false);
  }
}
