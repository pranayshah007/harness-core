/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Getter
@Builder
@FieldNameConstants(innerTypeName = "OutboxEventKeys")
@Entity(value = "outboxEvents", noClassnameStored = true)
@Document("outboxEvents")
@TypeAlias("outboxEvents")
@StoreIn(DbAliases.NG_MANAGER)
@StoreIn(DbAliases.PMS)
@StoreIn(DbAliases.TEMPLATE)
@StoreIn(DbAliases.CIMANAGER)
@StoreIn(DbAliases.ACCESS_CONTROL)
@StoreIn(DbAliases.RESOURCEGROUP)
@StoreIn(DbAliases.CENG)
public class OutboxEvent implements PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id String id;

  @NotNull ResourceScope resourceScope;
  @NotNull @Valid Resource resource;

  @NotNull String eventType;
  @NotNull String eventData;

  @CreatedDate Long createdAt;
  @Setter @Builder.Default Boolean blocked = Boolean.FALSE;
  @Setter Instant nextUnblockAttemptAt;

  GlobalContext globalContext;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("blocked_createdAt_nextUnblockAttemptAt_outbox_Idx")
                 .field(OutboxEventKeys.blocked)
                 .field(OutboxEventKeys.createdAt)
                 .field(OutboxEventKeys.nextUnblockAttemptAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("eventType_blocked_outbox_Idx")
                 .field(OutboxEventKeys.eventType)
                 .field(OutboxEventKeys.blocked)
                 .build())
        .build();
  }
}
