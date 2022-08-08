/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static java.time.Duration.ofDays;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.sdk.core.events.OrchestrationEvent;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "OrchestrationEventLogKeys")
@Entity(value = "orchestrationEventLog", noClassnameStored = true)
@Document("orchestrationEventLog")
@HarnessEntity(exportable = false)
@TypeAlias("OrchestrationEventLog")
@StoreIn(DbAliases.PMS)
public class OrchestrationEventLog implements PersistentEntity {
  public static final Duration TTL = ofDays(5);

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_createdAt")
                 .unique(false)
                 .field(OrchestrationEventLogKeys.planExecutionId)
                 .field(OrchestrationEventLogKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("createdAt")
                 .unique(false)
                 .field(OrchestrationEventLogKeys.createdAt)
                 .build())
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  String planExecutionId;
  String nodeExecutionId;
  OrchestrationEventType orchestrationEventType;
  @Deprecated OrchestrationEvent event;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plus(TTL).toInstant());
  long createdAt;
}
