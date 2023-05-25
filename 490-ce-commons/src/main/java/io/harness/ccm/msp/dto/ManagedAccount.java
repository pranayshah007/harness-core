/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.dto;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
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
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "ManagedAccountKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "managedAccount", noClassnameStored = true)
@Document("managedAccount")
@TypeAlias("managedAccount")
@OwnedBy(CE)
public class ManagedAccount implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
                                       CreatedByAware, UpdatedByAware {
  @Id String uuid;
  String accountId;
  String accountName;
  String mspAccountId;

  long createdAt;
  long lastUpdatedAt;
  private EmbeddedUser createdBy;
  private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId")
                 .unique(true)
                 .field(ManagedAccountKeys.accountId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_mspAccountId")
                 .unique(true)
                 .field(ManagedAccountKeys.accountId)
                 .field(ManagedAccountKeys.mspAccountId)
                 .build())
        .add(CompoundMongoIndex.builder().name("mspAccountId_1").field(ManagedAccountKeys.mspAccountId).build())
        .build();
  }
}
