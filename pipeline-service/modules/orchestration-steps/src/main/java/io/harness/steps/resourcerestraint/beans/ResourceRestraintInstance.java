/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.constraint.Consumer;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ResourceRestraintInstanceKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "resourceRestraintInstances")
@Document("resourceRestraintInstances")
@TypeAlias("resourceRestraintInstance")
public class ResourceRestraintInstance implements PersistentEntity, UuidAccess, PersistentRegularIterable {
  public static final long TTL = 6;

  @Id @org.mongodb.morphia.annotations.Id String uuid;
  String claimant;

  String resourceRestraintId;
  String resourceUnit;
  int order;

  Consumer.State state;
  int permits;

  String releaseEntityType; // scope
  String releaseEntityId;

  long acquireAt;

  Long nextIteration;

  // audit fields
  @Wither @FdIndex @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastUpdatedAt;
  @Version Long version;

  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL).toInstant());

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("resourceRestraintId_resourceUnit_order_idx")
                 .unique(true)
                 .field(ResourceRestraintInstanceKeys.resourceRestraintId)
                 .field(ResourceRestraintInstanceKeys.resourceUnit)
                 .field(ResourceRestraintInstanceKeys.order)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("resourceRestraintId_order_idx")
                 .field(ResourceRestraintInstanceKeys.resourceRestraintId)
                 .field(ResourceRestraintInstanceKeys.order)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("next_iteration_idx")
                 .field(ResourceRestraintInstanceKeys.state)
                 .field(ResourceRestraintInstanceKeys.nextIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("releaseEntityType_releaseEntityId_idx")
                 .field(ResourceRestraintInstanceKeys.releaseEntityType)
                 .field(ResourceRestraintInstanceKeys.releaseEntityId)
                 .build())
        .build();
  }

  public AutoLogContext autoLogContext() {
    Map<String, String> logContext = new HashMap<>();
    logContext.put("restraintInstanceId", uuid);
    logContext.put(ResourceRestraintInstanceKeys.resourceRestraintId, resourceRestraintId);
    logContext.put(ResourceRestraintInstanceKeys.resourceUnit, resourceUnit);
    logContext.put(ResourceRestraintInstanceKeys.releaseEntityId, releaseEntityId);
    logContext.put(ResourceRestraintInstanceKeys.releaseEntityType, releaseEntityType);
    logContext.put(ResourceRestraintInstanceKeys.permits, String.valueOf(permits));
    logContext.put(ResourceRestraintInstanceKeys.order, String.valueOf(order));
    return new AutoLogContext(logContext, OVERRIDE_NESTS);
  }
}
