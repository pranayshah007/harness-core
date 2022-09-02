/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotation.StoreIn;
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
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "GcpBillingAccountKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gcpBillingAccount", noClassnameStored = true)
@OwnedBy(CE)
public class GcpBillingAccount implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("no_dup")
                 .unique(true)
                 .field(GcpBillingAccountKeys.accountId)
                 .field(GcpBillingAccountKeys.organizationSettingId)
                 .build())
        .build();
  }
  @Id String uuid;
  String accountId;
  String organizationSettingId;
  String gcpBillingAccountId;
  String gcpBillingAccountName;
  boolean exportEnabled;
  String bqProjectId;
  String bqDatasetId;
  String bqDataSetRegion;

  long createdAt;
  long lastUpdatedAt;
}
