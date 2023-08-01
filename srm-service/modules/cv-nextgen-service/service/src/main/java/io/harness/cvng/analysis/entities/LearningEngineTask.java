/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.cvng.analysis.entities.LearningEngineTask.TaskPriority.P1;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.core.entities.VerificationTaskExecutionInstance;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/**
 * @author praveensugavanam
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "LearningEngineTaskKeys")
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "learningEngineTasks")
@HarnessEntity(exportable = true)
public abstract class LearningEngineTask extends VerificationTaskBase
    implements PersistentEntity, UuidAware, AccountAccess, VerificationTaskExecutionInstance {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .unique(false)
                 .field(LearningEngineTaskKeys.taskStatus)
                 .field(LearningEngineTaskKeys.taskPriority)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("taskFetchNextTaskPriorityIdx")
                 .field(LearningEngineTaskKeys.taskPriority)
                 .field(LearningEngineTaskKeys.taskStatus)
                 .ascSortField(VerificationTaskBaseKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("trend_idx")
                 .unique(false)
                 .field(LearningEngineTaskKeys.verificationTaskId)
                 .field(LearningEngineTaskKeys.analysisEndTime)
                 .field(LearningEngineTaskKeys.analysisType)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String verificationTaskId;

  private Instant pickedAt;
  @FdIndex private String accountId;
  private LearningEngineTaskType analysisType;
  private int taskPriority = P1.getValue();
  private String analysisSaveUrl;
  private String failureUrl;
  private Instant analysisStartTime;
  private Instant analysisEndTime;
  private long analysisEndEpochMinute; // This is temporary. LE needs it for now.
  private ExecutionStatus taskStatus;
  public abstract LearningEngineTaskType getType();
  private String exception;
  private String stackTrace;
  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(31).toInstant());

  public enum LearningEngineTaskType {
    SERVICE_GUARD_TIME_SERIES,
    LOG_CLUSTER,
    SERVICE_GUARD_LOG_ANALYSIS,
    CANARY_LOG_ANALYSIS,
    TEST_LOG_ANALYSIS,
    TIME_SERIES_CANARY,
    CANARY_DEPLOYMENT_TIME_SERIES,
    BEFORE_AFTER_DEPLOYMENT_TIME_SERIES,
    SERVICE_GUARD_FEEDBACK_ANALYSIS,
    BEFORE_AFTER_DEPLOYMENT_LOG,
    CANARY_DEPLOYMENT_LOG,
    LOG_ANALYSIS,
    TIME_SERIES_LOAD_TEST,
    LOG_FEEDBACK,
    CV_LOG_CLUSTER;

    public static List<LearningEngineTaskType> getDeploymentTaskTypes() {
      return Arrays.asList(TIME_SERIES_CANARY, TIME_SERIES_LOAD_TEST, CANARY_LOG_ANALYSIS, TEST_LOG_ANALYSIS);
    }
  }

  public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    FAILED,
    SUCCESS,
    TIMEOUT;
    public static List<ExecutionStatus> getNonFinalStatues() {
      return Arrays.asList(QUEUED, RUNNING);
    }
  }

  public Duration totalTime(Instant currentTime) {
    return Duration.between(Instant.ofEpochMilli(getCreatedAt()), currentTime);
  }

  public Duration runningTime(Instant currentTime) {
    return Duration.between(pickedAt, currentTime);
  }

  public Duration waitTime() {
    return Duration.between(Instant.ofEpochMilli(getCreatedAt()), pickedAt);
  }

  public enum TaskPriority {
    P0(0),
    P1(1),
    P2(2);
    int value;
    TaskPriority(int value) {
      this.value = value;
    }
    public int getValue() {
      return value;
    }
  }

  @Override
  public Instant getStartTime() {
    return analysisStartTime;
  }

  @Override
  public Instant getEndTime() {
    return analysisEndTime;
  }

  public LogLevel getLogLevel() {
    if (ExecutionStatus.FAILED.equals(taskStatus) || ExecutionStatus.TIMEOUT.equals(taskStatus)) {
      return LogLevel.ERROR;
    } else if (ExecutionStatus.QUEUED.equals(taskStatus)) {
      return LogLevel.WARN;
    } else {
      return LogLevel.INFO;
    }
  }
}
