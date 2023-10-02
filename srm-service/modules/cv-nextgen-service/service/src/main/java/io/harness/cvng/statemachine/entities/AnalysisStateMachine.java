/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.entities;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.analysis.entities.VerificationTaskBase;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.core.entities.VerificationTaskExecutionInstance;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AnalysisStateMachineKeys")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "analysisStateMachines", noClassnameStored = true)
@HarnessEntity(exportable = true)
public final class AnalysisStateMachine extends VerificationTaskBase
    implements PersistentEntity, UuidAware, AccountAccess, VerificationTaskExecutionInstance {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("execute_query_idx")
                 .field(AnalysisStateMachineKeys.verificationTaskId)
                 .field(AnalysisStateMachineKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("verificationTaskIdQueryIdx")
                 .unique(false)
                 .field(AnalysisStateMachineKeys.verificationTaskId)
                 .descSortField(VerificationTaskBaseKeys.createdAt)
                 .build())
        .build();
  }

  private static final List<Duration> RETRY_WAIT_DURATIONS = Lists.newArrayList(Duration.ofMinutes(1),
      Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(3));

  @Id private String uuid;
  @FdIndex private String accountId;

  private Instant analysisStartTime;
  private Instant analysisEndTime;
  private String verificationTaskId;
  private AnalysisState currentState;
  private List<AnalysisState> completedStates;
  @FdIndex private AnalysisStatus status;

  private long nextAttemptTime;
  private Instant firstPickedAt;
  private int totalRetryCount;

  @NotNull private Integer stateMachineIgnoreMinutes;

  public int incrementTotalRetryCount() {
    totalRetryCount = totalRetryCount + 1;
    return totalRetryCount;
  }

  public void setNextAttemptTimeUsingRetryCount(Instant now) {
    nextAttemptTime =
        now.plus(RETRY_WAIT_DURATIONS.get(Math.min(totalRetryCount, RETRY_WAIT_DURATIONS.size() - 1))).toEpochMilli();
  }

  public int getTotalRetryCountToBePropagated() {
    if (status.equals(AnalysisStatus.SUCCESS) || status.equals(AnalysisStatus.COMPLETED)) {
      return 0;
    }
    return totalRetryCount;
  }

  public Integer getStateMachineIgnoreMinutes() {
    if (stateMachineIgnoreMinutes == null) {
      stateMachineIgnoreMinutes = STATE_MACHINE_IGNORE_MINUTES;
    }
    return stateMachineIgnoreMinutes;
  }

  @FdTtlIndex @Builder.Default private Date validUntil = Date.from(OffsetDateTime.now().plusDays(14).toInstant());

  @Override
  public Instant getStartTime() {
    return analysisStartTime;
  }

  @Override
  public Instant getEndTime() {
    return analysisEndTime;
  }

  public LogLevel getLogLevel() {
    if (AnalysisStatus.getFailedStatuses().contains(status)) {
      return LogLevel.ERROR;
    } else if (AnalysisStatus.IGNORED.equals(status)) {
      return LogLevel.WARN;
    } else {
      return LogLevel.INFO;
    }
  }
}
