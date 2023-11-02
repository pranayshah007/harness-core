/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.datadeletion;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.INCOMPLETE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "DataDeletionRecordKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "dataDeletionRecords", noClassnameStored = true)
@OwnedBy(CE)
public final class DataDeletionRecord
    implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId")
                 .unique(true)
                 .field(DataDeletionRecordKeys.accountId)
                 .build())
        .build();
  }

  @Id String uuid;
  String accountId;
  @Builder.Default Boolean dryRun = true;
  @Builder.Default DataDeletionStatus status = INCOMPLETE;
  @Builder.Default Long retryCount = 0L;
  Long lastProcessedAt;
  Map<String, DataDeletionStepRecord> records;
  @Builder.Default DataDeletionStatus autoStoppingStatus = INCOMPLETE;
  @Builder.Default Long autoStoppingRetryCount = 0L;
  @Builder.Default DataDeletionStatus autoCudStatus = INCOMPLETE;
  @Builder.Default Long autoCudRetryCount = 0L;

  long createdAt;
  long lastUpdatedAt;
}
