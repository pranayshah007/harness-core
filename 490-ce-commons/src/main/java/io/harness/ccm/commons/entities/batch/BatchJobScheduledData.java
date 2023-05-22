/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.batch;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.persistence.ValidUntilAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@StoreIn(DbAliases.CENG)
@Entity(value = "batchJobScheduledData", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "BatchJobScheduledDataKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CE)
public final class BatchJobScheduledData
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, ValidUntilAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_batchJobType_validRun_endAt")
                 .field(BatchJobScheduledDataKeys.accountId)
                 .field(BatchJobScheduledDataKeys.batchJobType)
                 .field(BatchJobScheduledDataKeys.validRun)
                 .descSortField(BatchJobScheduledDataKeys.endAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_batchJobType_startAt")
                 .field(BatchJobScheduledDataKeys.accountId)
                 .field(BatchJobScheduledDataKeys.batchJobType)
                 .field(BatchJobScheduledDataKeys.startAt)
                 .build())
        .build();
  }

  @Id String uuid;
  String accountId;
  String batchJobType;
  String comments;
  long jobRunTimeMillis;
  boolean validRun;
  Instant startAt;
  Instant endAt;
  long createdAt;
  long lastUpdatedAt;

  @JsonIgnore
  @FdTtlIndex
  @SchemaIgnore
  @Builder.Default
  @EqualsAndHashCode.Exclude
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(3).toInstant());

  public BatchJobScheduledData(
      String accountId, String batchJobType, long jobRunTimeMillis, Instant startAt, Instant endAt) {
    this.accountId = accountId;
    this.batchJobType = batchJobType;
    this.jobRunTimeMillis = jobRunTimeMillis;
    this.startAt = startAt;
    this.endAt = endAt;
    this.validRun = true;
  }
}
