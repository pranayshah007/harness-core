/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(PL)
@Value
@Builder
@FieldNameConstants(innerTypeName = "CacheEntityKeys")
@StoreIn(DbAliases.ALL)
@Entity(value = "cache")
@HarnessEntity(exportable = false)
public class CacheEntity implements PersistentEntity, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_commutative")
                 .unique(true)
                 .field("_id")
                 .field(CacheEntityKeys.contextValue)
                 .build())
        .build();
  }

  long contextValue;
  @Id String canonicalKey;

  byte[] entity;

  @FdTtlIndex Date validUntil;
  @FdIndex String accountId;
}
