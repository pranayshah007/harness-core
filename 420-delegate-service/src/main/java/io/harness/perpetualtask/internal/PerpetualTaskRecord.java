/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.SecondaryStoreIn;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "PerpetualTaskRecordKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.HARNESS)
@SecondaryStoreIn(DbAliases.DMS)
@Entity(value = "perpetualTask", noClassnameStored = true)
@HarnessEntity(exportable = false)
@Slf4j
@OwnedBy(DEL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerpetualTaskRecord
    implements PersistentEntity, UuidAware, PersistentRegularIterable, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("assignIterator")
                 .field(PerpetualTaskRecordKeys.state)
                 .field(PerpetualTaskRecordKeys.assignAfterMs)
                 .field(PerpetualTaskRecordKeys.assignIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("state_assignIteration_assignAfterMs")
                 .field(PerpetualTaskRecordKeys.state)
                 .field(PerpetualTaskRecordKeys.assignIteration)
                 .field(PerpetualTaskRecordKeys.assignAfterMs)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("assigned_delegate")
                 .field(PerpetualTaskRecordKeys.accountId)
                 .field(PerpetualTaskRecordKeys.state)
                 .field(PerpetualTaskRecordKeys.delegateId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("clientContext_clientParams_infrastructureMappingId")
                 .field(PerpetualTaskRecordKeys.clientContext + "." + PerpetualTaskClientContextKeys.clientParams + "."
                     + "infrastructureMappingId")
                 .sparse(true)
                 .build())
        .build();
  }

  @Id String uuid;
  String accountId;
  String perpetualTaskType;
  PerpetualTaskClientContext clientContext;
  long intervalSeconds;
  long timeoutMillis;
  @FdIndex String delegateId;
  String taskDescription;
  PerpetualTaskState state;
  PerpetualTaskUnassignedReason unassignedReason;
  @Deprecated long lastHeartbeat;

  @Deprecated List<Long> assignerIterations;
  long assignIteration;
  long rebalanceIteration;

  int assignTryCount;
  long assignAfterMs;

  long lastUpdatedAt;
  long createdAt;
  long failedExecutionCount;

  String exception;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (PerpetualTaskRecordKeys.assignIteration.equals(fieldName)) {
      return assignIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (PerpetualTaskRecordKeys.assignIteration.equals(fieldName)) {
      this.assignIteration = nextIteration;
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
