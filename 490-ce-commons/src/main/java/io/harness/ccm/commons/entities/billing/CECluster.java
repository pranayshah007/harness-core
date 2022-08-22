/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.billing;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "ceCluster", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "CEClusterKeys")
@StoreIn(DbAliases.CENG)
public final class CECluster implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("no_dup")
                 .unique(true)
                 .field(CEClusterKeys.accountId)
                 .field(CEClusterKeys.infraAccountId)
                 .field(CEClusterKeys.region)
                 .field(CEClusterKeys.clusterName)
                 .build())
        .build();
  }
  @Id String uuid;
  String accountId;
  String clusterName;
  String clusterArn;
  String region;
  String infraAccountId;
  String infraMasterAccountId;
  String parentAccountSettingId; // setting id of ce connectors
  Map<String, String> labels;
  @FdIndex String hash;
  boolean isDeactivated;
  long lastReceivedAt;
  long createdAt;
  long lastUpdatedAt;

  @Builder(toBuilder = true)
  private CECluster(String accountId, String clusterName, String clusterArn, String region, String infraAccountId,
      String infraMasterAccountId, String parentAccountSettingId, Map<String, String> labels, boolean isDeactivated) {
    this.accountId = accountId;
    this.clusterName = clusterName;
    this.clusterArn = clusterArn;
    this.region = region;
    this.infraAccountId = infraAccountId;
    this.infraMasterAccountId = infraMasterAccountId;
    this.parentAccountSettingId = parentAccountSettingId;
    this.labels = labels;
    this.isDeactivated = isDeactivated;
    this.hash = hash(accountId, clusterName, region, infraAccountId);
  }

  public static String hash(String accountId, String clusterName, String region, String infraAccountId) {
    return Base64.getEncoder().encodeToString(Hashing.sha1()
                                                  .newHasher()
                                                  .putString(accountId, UTF_8)
                                                  .putString(clusterName, UTF_8)
                                                  .putString(region, UTF_8)
                                                  .putString(infraAccountId, UTF_8)
                                                  .hash()
                                                  .asBytes());
  }
}
