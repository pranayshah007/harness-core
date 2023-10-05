/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitopsprovider.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.constraint.Consumer;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "GitRestraintInstanceKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitRestraintInstances")
@Document("gitRestraintInstances")
@TypeAlias("gitRestraintInstance")
public class GitRestraintInstance implements PersistentEntity, UuidAccess {
  public static final long TTL = 6;

  @Id @dev.morphia.annotations.Id String uuid;
  String claimant;

  String resourceUnit;
  int order;

  Consumer.State state;
  int permits;

  String releaseEntityId;

  long acquireAt;

  // audit fields
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  @Version Long version;

  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL).toInstant());

  public AutoLogContext autoLogContext() {
    Map<String, String> logContext = new HashMap<>();
    logContext.put("restraintInstanceId", uuid);
    logContext.put(GitRestraintInstanceKeys.resourceUnit, resourceUnit);
    logContext.put(GitRestraintInstanceKeys.releaseEntityId, releaseEntityId);
    logContext.put(GitRestraintInstanceKeys.permits, String.valueOf(permits));
    logContext.put(GitRestraintInstanceKeys.order, String.valueOf(order));
    logContext.put("restraintType", "github");
    return new AutoLogContext(logContext, OVERRIDE_NESTS);
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("resourceUnit_order_idx")
                 .unique(true)
                 .field(GitRestraintInstanceKeys.resourceUnit)
                 .field(GitRestraintInstanceKeys.order)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("resourceUnit_state_idx")
                 .field(GitRestraintInstanceKeys.resourceUnit)
                 .field(GitRestraintInstanceKeys.state)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("releaseEntityId_state_idx")
                 .field(GitRestraintInstanceKeys.releaseEntityId)
                 .field(GitRestraintInstanceKeys.state)
                 .build())
        .build();
  }
}
