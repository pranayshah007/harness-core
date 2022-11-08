/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAccess;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAccess;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDP)
@Data
@Builder
@FieldNameConstants(innerTypeName = "InstanceSyncTaskDetailsKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instanceSyncTaskDetails", noClassnameStored = true)
public class InstanceSyncTaskDetails implements PersistentEntity, UuidAware, UuidAccess, AccountAccess, CreatedAtAccess,
                                                CreatedAtAware, UpdatedAtAware, UpdatedAtAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("accountId_infrastructureMappingId_idx")
                 .field(InstanceSyncTaskDetailsKeys.accountId)
                 .field(InstanceSyncTaskDetailsKeys.infraMappingId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_perpetualTaskId_idx")
                 .field(InstanceSyncTaskDetailsKeys.accountId)
                 .field(InstanceSyncTaskDetailsKeys.perpetualTaskId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_cloudProviderId_idx")
                 .field(InstanceSyncTaskDetailsKeys.accountId)
                 .field(InstanceSyncTaskDetailsKeys.cloudProviderId)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String accountId;
  private String appId;
  private String cloudProviderId;
  private String infraMappingId;
  private String perpetualTaskId;
  private Set<CgReleaseIdentifiers> releaseIdentifiers;
  long lastSuccessfulRun;
  long createdAt;
  long lastUpdatedAt;
}
