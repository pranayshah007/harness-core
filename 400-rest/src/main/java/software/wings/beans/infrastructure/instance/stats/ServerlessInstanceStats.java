/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance.stats;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "serverless-instance-stats", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "ServerlessInstanceStatsKeys")
@OwnedBy(CDP)
public class ServerlessInstanceStats implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                                UpdatedAtAware, UpdatedByAware, AccountAccess {
  public static final long TTL_MONTHS = 6;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_timestamp_unique_idx")
                 .unique(true)
                 .field(ServerlessInstanceStatsKeys.accountId)
                 .field(ServerlessInstanceStatsKeys.timestamp)
                 .build())
        .build();
  }
  public static final InvocationCountKey DEFAULT_INVOCATION_COUNT_KEY = InvocationCountKey.LAST_30_DAYS;
  @Id @NotNull(groups = {Update.class}) private String uuid;

  private EmbeddedUser createdBy;

  @FdIndex private long createdAt;

  private EmbeddedUser lastUpdatedBy;

  @NotNull private long lastUpdatedAt;

  @NonFinal private Instant timestamp;
  @NonFinal private String accountId;
  @NonFinal private Collection<AggregateInvocationCount> aggregateCounts = new ArrayList<>();
  @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  public ServerlessInstanceStats(
      Instant timestamp, String accountId, Collection<AggregateInvocationCount> aggregateCounts) {
    this.timestamp = timestamp;
    this.accountId = accountId;
    this.aggregateCounts = aggregateCounts;
  }

  public Collection<AggregateInvocationCount> getAggregateCounts() {
    return ImmutableList.copyOf(aggregateCounts);
  }

  @Value
  @Builder
  public static class AggregateInvocationCount {
    private EntityType entityType;
    private String name;
    private String id;
    @NonFinal private long invocationCount;
    private InvocationCountKey invocationCountKey;
  }
}
