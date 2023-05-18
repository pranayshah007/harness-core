/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.interrupts;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.UuidAccess;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Value
@Builder
@StoreIn(DbAliases.PMS)
@Entity(value = "interrupts", noClassnameStored = true)
@Document(value = "interrupts")
@FieldNameConstants(innerTypeName = "InterruptKeys")
@TypeAlias("interrupt")
@ChangeDataCapture(table = "verify_step_interrupt_cvng", dataStore = "pms-harness", fields = {},
    handler = "VerifyStepInterruptCDCHandler`")
public class Interrupt implements PersistentRegularIterable, UuidAccess {
  public static final long TTL_MONTHS = 4;
  public enum State { REGISTERED, PROCESSING, PROCESSED_SUCCESSFULLY, PROCESSED_UNSUCCESSFULLY, DISCARDED }

  @Wither @Id @dev.morphia.annotations.Id @NotNull String uuid;
  @NonNull InterruptType type;
  @NotNull InterruptConfig interruptConfig;
  @NonNull String planExecutionId;
  String nodeExecutionId;
  Map<String, String> metadata;
  @Wither @LastModifiedDate Long lastUpdatedAt;
  @Wither @CreatedDate Long createdAt;
  @NonFinal @Setter @Builder.Default State state = State.REGISTERED;
  @Wither @Version Long version;

  @Getter @NonFinal @Setter Long nextIteration;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  Boolean fromMonitor;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  public AutoLogContext autoLogContext() {
    return new AutoLogContext(logContextMap(), OVERRIDE_NESTS);
  }

  private Map<String, String> logContextMap() {
    Map<String, String> logContext = new HashMap<>();
    logContext.put("interruptId", uuid);
    logContext.put(InterruptKeys.planExecutionId, planExecutionId);
    logContext.put(InterruptKeys.type, type.name());
    logContext.put(InterruptKeys.nodeExecutionId, nodeExecutionId);
    return logContext;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_state_type_createdAt_idx")
                 .field(InterruptKeys.planExecutionId)
                 .field(InterruptKeys.state)
                 .field(InterruptKeys.type)
                 .descSortField(InterruptKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_createdAt_idx")
                 .field(InterruptKeys.planExecutionId)
                 .descSortField(InterruptKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_nodeExecutionId_createdAt_idx")
                 .field(InterruptKeys.planExecutionId)
                 .field(InterruptKeys.nodeExecutionId)
                 .descSortField(InterruptKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_nodeExecutionId_type_createdAt_idx")
                 .field(InterruptKeys.planExecutionId)
                 .field(InterruptKeys.nodeExecutionId)
                 .field(InterruptKeys.type)
                 .descSortField(InterruptKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("state_type_monitoring_createdAt_idx")
                 .field(InterruptKeys.state)
                 .field(InterruptKeys.type)
                 .descSortField(InterruptKeys.createdAt)
                 .build())
        .build();
  }

  public Boolean isFromMonitor() {
    return fromMonitor != null && fromMonitor;
  }
}
