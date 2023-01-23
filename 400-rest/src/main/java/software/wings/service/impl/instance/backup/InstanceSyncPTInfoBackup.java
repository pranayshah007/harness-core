/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.backup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAccess;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAccess;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "InstanceSyncPTBackupKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instanceSyncPerpetualTasksInfoBackup", noClassnameStored = true)
public class InstanceSyncPTInfoBackup implements PersistentEntity, UuidAware, UuidAccess, AccountAccess,
                                                 CreatedAtAccess, CreatedAtAware, UpdatedAtAware, UpdatedAtAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_infrastructureMappingId_idx")
                 .field(InstanceSyncPTInfoBackup.InstanceSyncPTBackupKeys.accountId)
                 .field(InstanceSyncPTInfoBackup.InstanceSyncPTBackupKeys.infrastructureMappingId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("accountId_perpetualTaskId_idx")
                 .field(InstanceSyncPTInfoBackup.InstanceSyncPTBackupKeys.accountId)
                 .field(InstanceSyncPTBackupKeys.perpetualTaskRecordId)
                 .build())
        .build();
  }

  @Id String uuid;
  String accountId;
  String infrastructureMappingId;
  PerpetualTaskRecord perpetualTaskRecord;
  String perpetualTaskRecordId;

  long createdAt;
  long lastUpdatedAt;
}
