/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance.stats;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.beans.EntityType;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.apache.commons.collections4.CollectionUtils;
import org.mongodb.morphia.annotations.Entity;

@Value
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instanceStats", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "InstanceStatsSnapshotKeys")
public class InstanceStatsSnapshot extends Base implements AccountAccess {
  public static final long TTL_MONTHS = 6;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_timestamp")
                 .unique(true)
                 .field(InstanceStatsSnapshotKeys.accountId)
                 .field(InstanceStatsSnapshotKeys.timestamp)
                 .build())
        .build();
  }

  private static final List<EntityType> ENTITY_TYPES_TO_AGGREGATE_ON =
      Collections.singletonList(EntityType.APPLICATION);

  @NonFinal @FdIndex Instant timestamp;
  @NonFinal String accountId;
  @NonFinal List<AggregateCount> aggregateCounts = new ArrayList<>();
  @NonFinal int total;
  @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
  public InstanceStatsSnapshot(Instant timestamp, String accountId, List<AggregateCount> aggregateCounts) {
    validate(aggregateCounts);

    this.timestamp = timestamp;
    this.accountId = accountId;
    this.aggregateCounts = aggregateCounts;

    if (CollectionUtils.isEmpty(aggregateCounts)) {
      this.total = 0;
    } else {
      // Only calculating the total of applications. The total of instance count by services might be different from
      // total of Applications. That is because applications are under RBAC and services are not. For an admin, who had
      // access to everything, those counts should always be equal.
      this.total = aggregateCounts.stream()
                       .filter(ac -> ac.entityType == EntityType.APPLICATION)
                       .mapToInt(AggregateCount::getCount)
                       .sum();
    }
  }

  private void validate(List<AggregateCount> aggregateCounts) {
    for (AggregateCount aggregateCount : aggregateCounts) {
      if (!ENTITY_TYPES_TO_AGGREGATE_ON.contains(aggregateCount.entityType)) {
        throw new IllegalArgumentException(
            "Unexpected entity type. See ENTITY_TYPES_TO_AGGREGATE_ON for allowed types. Type: "
            + aggregateCount.entityType);
      }
    }
  }

  public List<AggregateCount> getAggregateCounts() {
    return ImmutableList.copyOf(aggregateCounts);
  }

  @Value
  public static class AggregateCount {
    EntityType entityType;
    String name;
    String id;
    @NonFinal int count;

    public void incrementCount(int diff) {
      this.count = count + diff;
    }
  }
}
