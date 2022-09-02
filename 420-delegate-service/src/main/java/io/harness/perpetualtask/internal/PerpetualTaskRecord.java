/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentFibonacciIterable;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskClientContext.PerpetualTaskClientContextKeys;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "PerpetualTaskRecordKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "perpetualTask", noClassnameStored = true)
@HarnessEntity(exportable = false)
@Slf4j
@OwnedBy(DEL)
public class PerpetualTaskRecord implements PersistentEntity, UuidAware, PersistentRegularIterable,
                                            PersistentFibonacciIterable, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("assignerIterator")
                 .field(PerpetualTaskRecordKeys.state)
                 .field(PerpetualTaskRecordKeys.assignAfterMs)
                 .field(PerpetualTaskRecordKeys.assignerIterations)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("rebalanceIterator")
                 .field(PerpetualTaskRecordKeys.state)
                 .field(PerpetualTaskRecordKeys.rebalanceIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("assignerIterator_1")
                 .field(PerpetualTaskRecordKeys.state)
                 .field(PerpetualTaskRecordKeys.assignerIterations)
                 .build())
        .build();
  }

  @Id String uuid;
  @FdIndex String accountId;
  String perpetualTaskType;
  PerpetualTaskClientContext clientContext;
  long intervalSeconds;
  long timeoutMillis;
  @FdIndex String delegateId;
  String taskDescription;
  PerpetualTaskState state;
  PerpetualTaskUnassignedReason unassignedReason;
  long lastHeartbeat;

  List<Long> assignerIterations;
  long rebalanceIteration;

  int assignTryCount;
  long assignAfterMs;

  long lastUpdatedAt;
  long createdAt;
  long failedExecutionCount;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (PerpetualTaskRecordKeys.assignerIterations.equals(fieldName)) {
      return isEmpty(assignerIterations) ? null : assignerIterations.get(0);
    }
    if (PerpetualTaskRecordKeys.rebalanceIteration.equals(fieldName)) {
      return rebalanceIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    if (PerpetualTaskRecordKeys.assignerIterations.equals(fieldName)) {
      if (assignerIterations == null) {
        assignerIterations = new ArrayList<>();
      }
      if (recalculateTimestamps(assignerIterations, skipMissed, throttled, ofSeconds(30), ofMinutes(30))) {
        return assignerIterations;
      }
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (PerpetualTaskRecordKeys.rebalanceIteration.equals(fieldName)) {
      this.rebalanceIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public static final class PerpetualTaskRecordKeys {
    private PerpetualTaskRecordKeys() {}
    public static final String client_context_last_updated =
        clientContext + "." + PerpetualTaskClientContextKeys.lastContextUpdated;
    public static final String client_params = clientContext + "." + PerpetualTaskClientContextKeys.clientParams;
    public static final String task_parameters = clientContext + "." + PerpetualTaskClientContextKeys.executionBundle;
    public static final String client_id = clientContext + "." + PerpetualTaskClientContextKeys.clientId;
  }
}
