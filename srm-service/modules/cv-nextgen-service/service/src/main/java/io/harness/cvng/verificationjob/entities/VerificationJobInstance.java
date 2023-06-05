/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static java.util.stream.Collectors.groupingBy;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.cvng.CVConstants;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.BaselineType;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob.CanaryVerificationJobKeys;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter.RuntimeParameterKeys;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "VerificationJobInstanceKeys")
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "verificationJobInstances", noClassnameStored = true)
@HarnessEntity(exportable = true)
@ChangeDataCapture(
    table = "verify_step_execution_cvng", dataStore = "cvng", fields = {}, handler = "VerifyStepExecutionHandler")
@ChangeDataCapture(table = "health_source_cvng", dataStore = "cvng", fields = {}, handler = "HealthSourceHandler")
public final class VerificationJobInstance
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(VerificationJobInstanceKeys.executionStatus)
                 .field(VerificationJobInstanceKeys.accountId)
                 .build())
        .build();
  }
  private static final Duration TIMEOUT = Duration.ofMinutes(30);
  public static final String VERIFICATION_JOB_TYPE_KEY =
      String.format("%s.%s", VerificationJobInstanceKeys.resolvedJob, CanaryVerificationJobKeys.type);
  public static String PROJECT_IDENTIFIER_KEY =
      String.format("%s.%s", VerificationJobInstanceKeys.resolvedJob, VerificationJobKeys.projectIdentifier);
  public static String ORG_IDENTIFIER_KEY =
      String.format("%s.%s", VerificationJobInstanceKeys.resolvedJob, VerificationJobKeys.orgIdentifier);
  public static String ENV_IDENTIFIER_KEY = String.format("%s.%s.%s", VerificationJobInstanceKeys.resolvedJob,
      VerificationJobKeys.envIdentifier, RuntimeParameterKeys.value);
  public static String SERVICE_IDENTIFIER_KEY = String.format("%s.%s.%s", VerificationJobInstanceKeys.resolvedJob,
      VerificationJobKeys.serviceIdentifier, RuntimeParameterKeys.value);
  public static String VERIFICATION_JOB_IDENTIFIER_KEY =
      String.format("%s.%s", VerificationJobInstanceKeys.resolvedJob, VerificationJobKeys.identifier);
  @Id private String uuid;
  private String name;
  @NotNull @FdIndex private String accountId;
  @FdIndex private long createdAt;
  @FdIndex private long lastUpdatedAt;
  private ExecutionStatus executionStatus;

  @Setter(AccessLevel.NONE) private Instant deploymentStartTime;
  @Setter(AccessLevel.PRIVATE) private Instant startTime;
  @FdIndex private Long dataCollectionTaskIteration;
  @FdIndex private Long timeoutTaskIteration;

  // TODO: Refactor and Split into separate job instances

  // this stuff is only required for deployment verification
  @Setter(AccessLevel.NONE) private Duration dataCollectionDelay;

  private Set<String> oldVersionHosts;
  private Set<String> newVersionHosts;
  private Integer newHostsTrafficSplitPercentage;
  private ActivityVerificationStatus verificationStatus;

  private List<ProgressLog> progressLogs;

  private VerificationJob resolvedJob;
  private Map<String, CVConfig> cvConfigMap;
  private Map<String, AppliedDeploymentAnalysisType> appliedDeploymentAnalysisTypeMap;
  private String planExecutionId;
  private String stageStepId;
  private String nodeExecutionId;
  private MonitoredServiceSpecType monitoredServiceType;
  private Boolean isBaseline;
  private BaselineType baselineType;

  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());

  public static class VerificationJobInstanceBuilder {
    public VerificationJobInstanceBuilder deploymentStartTime(Instant deploymentStartTime) {
      this.deploymentStartTime = DateTimeUtils.roundDownTo1MinBoundary(deploymentStartTime);
      return this;
    }

    public VerificationJobInstanceBuilder startTime(Instant startTime) {
      this.startTime = DateTimeUtils.roundDownTo1MinBoundary(startTime);
      return this;
    }
    public VerificationJobInstance build() {
      VerificationJobInstance unsafeVerificationJobInstance = unsafeBuild();
      Instant deploymentStartTime = unsafeVerificationJobInstance.getDeploymentStartTime();
      Preconditions.checkState(startTime.compareTo(deploymentStartTime) >= 0,
          "Deployment start time should be before verification start time.");
      unsafeVerificationJobInstance.setStartTime(
          getResolvedJob().roundToClosestBoundary(unsafeVerificationJobInstance.getDeploymentStartTime(), startTime));
      return unsafeVerificationJobInstance;
    }
    public VerificationJob getResolvedJob() {
      return resolvedJob;
    }
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (VerificationJobInstanceKeys.dataCollectionTaskIteration.equals(fieldName)) {
      this.dataCollectionTaskIteration = nextIteration;
      return;
    }
    if (VerificationJobInstanceKeys.timeoutTaskIteration.equals(fieldName)) {
      this.timeoutTaskIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (VerificationJobInstanceKeys.dataCollectionTaskIteration.equals(fieldName)) {
      return this.dataCollectionTaskIteration;
    }

    if (VerificationJobInstanceKeys.timeoutTaskIteration.equals(fieldName)) {
      return this.timeoutTaskIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public Instant getEndTime() {
    return getStartTime().plus(getResolvedJob().getDuration());
  }

  @JsonIgnore
  public int getVerificationTasksCount() {
    return cvConfigMap.size();
  }

  public List<ProgressLog> getProgressLogs() {
    if (progressLogs == null) {
      return Collections.emptyList();
    }
    return progressLogs;
  }

  @Data
  @SuperBuilder
  public abstract static class ProgressLog {
    Instant startTime;
    Instant endTime;
    boolean isFinalState;
    @Accessors(fluent = true) @Getter boolean shouldTerminate;
    String log;
    private String verificationTaskId;
    private Instant createdAt;
    public void validate() {
      Preconditions.checkNotNull(verificationTaskId);
      Preconditions.checkNotNull(startTime);
      Preconditions.checkNotNull(endTime);
      Preconditions.checkNotNull(log);
      Preconditions.checkNotNull(createdAt);
    }
    public abstract ExecutionStatus getVerificationJobExecutionStatus();
    public boolean shouldUpdateJobStatus() {
      return isFailedStatus();
    }
    public boolean isLastProgressLog(VerificationJobInstance verificationJobInstance) {
      return (getEndTime().equals(verificationJobInstance.getEndTime()) && isFinalState()) || shouldTerminate();
    }
    public Duration getTimeTakenToFinish() {
      return Duration.between(getStartTime(), getEndTime());
    }

    public abstract boolean isFailedStatus();
  }
  @Value
  @EqualsAndHashCode(callSuper = true)
  @SuperBuilder
  public static class AnalysisProgressLog extends ProgressLog {
    AnalysisStatus analysisStatus;

    @Override
    public ExecutionStatus getVerificationJobExecutionStatus() {
      return AnalysisStatus.mapToVerificationJobExecutionStatus(analysisStatus);
    }

    @Override
    public boolean isFailedStatus() {
      return AnalysisStatus.getFailedStatuses().contains(analysisStatus);
    }
  }
  @Value
  @EqualsAndHashCode(callSuper = true)
  @SuperBuilder
  public static class DataCollectionProgressLog extends ProgressLog {
    DataCollectionExecutionStatus executionStatus;

    @Override
    public boolean isFailedStatus() {
      return DataCollectionExecutionStatus.getFailedStatuses().contains(executionStatus);
    }

    @Override
    @JsonIgnore // used in debug api
    public ExecutionStatus getVerificationJobExecutionStatus() {
      Preconditions.checkState(DataCollectionExecutionStatus.getFailedStatuses().contains(executionStatus),
          "Final status can only be set for failed status: " + executionStatus);
      return ExecutionStatus.FAILED;
    }
  }

  public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    FAILED,
    SUCCESS,
    TIMEOUT,
    ABORTED;
    public static List<ExecutionStatus> nonFinalStatuses() {
      return Lists.newArrayList(QUEUED, RUNNING);
    }
    public static List<ExecutionStatus> noAnalysisStatuses() {
      return Lists.newArrayList(QUEUED, TIMEOUT);
    }
    public static List<ExecutionStatus> finalStatuses() {
      return Lists.newArrayList(SUCCESS, FAILED, TIMEOUT);
    }
  }
  private List<ProgressLog> getFinalStateProgressLogs() {
    return getProgressLogs().stream().filter(progressLog -> progressLog.isFinalState).collect(Collectors.toList());
  }
  public int getProgressPercentage() {
    // TODO: Reexamine this logic. This will return 0 for anything that's not marked as a final state.
    if (getExecutionStatus() == ExecutionStatus.SUCCESS) {
      // This is done for demo instance as it does not have progress logs yet.
      return 100;
    }
    List<ProgressLog> finalStateLogs = getFinalStateProgressLogs();
    if (finalStateLogs.isEmpty()) {
      return 0;
    }
    Map<String, List<ProgressLog>> groupByVerificationTaskId =
        finalStateLogs.stream().collect(groupingBy(ProgressLog::getVerificationTaskId));
    int progressPercentageSum = groupByVerificationTaskId.values()
                                    .stream()
                                    .map(progressLogsGroup -> getProgressLogPercentage(progressLogsGroup))
                                    .mapToInt(Integer::intValue)
                                    .sum();
    return progressPercentageSum / getVerificationTasksCount();
  }

  private int getProgressLogPercentage(List<ProgressLog> group) {
    if (group.isEmpty()) {
      return 0;
    }
    ProgressLog lastProgressLog = Collections.max(group, Comparator.comparing(ProgressLog::getEndTime));
    Instant endTime = lastProgressLog.getEndTime();
    Duration total = getResolvedJob().getExecutionDuration();
    Duration completedTillNow = Duration.between(getResolvedJob().getAnalysisStartTime(startTime), endTime);
    return (int) (completedTillNow.get(ChronoUnit.SECONDS) * 100.0 / total.get(ChronoUnit.SECONDS));
  }

  @JsonIgnore
  public Duration getRemainingTime(Instant currentTime) {
    if (isFinalStatus()) {
      return Duration.ZERO;
    } else if (executionStatus == ExecutionStatus.QUEUED) {
      return getResolvedJob().getDuration().plus(Duration.ofMinutes(5));
    } else {
      int percentage = getProgressPercentage();
      if (percentage == 0) {
        return getResolvedJob().getDuration().plus(Duration.ofMinutes(5));
      }
      Instant startTimeForDuration = getResolvedJob().eligibleToStartAnalysisTime(
          getStartTime(), getDataCollectionDelay(), Instant.ofEpochMilli(createdAt));
      Duration durationTillNow = Duration.between(startTimeForDuration, currentTime);
      Duration durationFor1Percent = Duration.ofMillis(durationTillNow.toMillis() / percentage);
      return Duration.ofMillis((100 - percentage) * durationFor1Percent.toMillis());
    }
  }

  private boolean isFinalStatus() {
    return ExecutionStatus.finalStatuses().contains(executionStatus);
  }

  public boolean isExecutionTimedOut(Instant now) {
    Instant cutoff =
        Collections.max(Arrays.asList(Instant.ofEpochMilli(createdAt).plus(TIMEOUT), getEndTime().plus(TIMEOUT)));
    return now.isAfter(cutoff);
  }
  // Just to support existing tests. We should never use this for newer API
  @Deprecated
  public void setStartTimeFromTest(Instant startTime) {
    this.setStartTime(startTime);
  }

  public Duration getExtraTimeTakenToFinish(Instant currentTime) {
    return Duration.between(getEndTime().plus(getDataCollectionDelay()), currentTime);
  }
}
