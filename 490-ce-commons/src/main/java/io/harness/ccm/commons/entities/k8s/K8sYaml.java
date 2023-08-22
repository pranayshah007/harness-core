/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.k8s;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "k8sYaml", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "K8sYamlKeys")
@OwnedBy(CE)
public final class K8sYaml implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_hash")
                 .field(K8sYamlKeys.accountId)
                 .field(K8sYamlKeys.hash)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_createdAt")
                 .field(K8sYamlKeys.accountId)
                 .field(K8sYamlKeys.createdAt)
                 .build())
        .build();
  }

  @Id private String uuid;
  long createdAt;

  private String accountId;
  private String clusterId;
  private String uid;

  private String resourceVersion;
  private String yaml;

  @EqualsAndHashCode.Exclude @FdTtlIndex Instant ttl;

  @FdUniqueIndex @Setter(AccessLevel.NONE) private String hash;

  @Builder(toBuilder = true)
  private K8sYaml(String accountId, String clusterId, String uid, String resourceVersion, String yaml, Instant ttl) {
    this.accountId = accountId;
    this.clusterId = clusterId;
    this.uid = uid;
    this.resourceVersion = resourceVersion;
    this.yaml = yaml;
    this.ttl = ttl;
    this.hash = hash(accountId, clusterId, uid, yaml);
  }

  public static String hash(String accountId, String clusterId, String uid, String yaml) {
    return Base64.getEncoder().encodeToString(Hashing.sha1()
                                                  .newHasher()
                                                  .putString(accountId, UTF_8)
                                                  .putString(clusterId, UTF_8)
                                                  .putString(uid, UTF_8)
                                                  .putString(yaml, UTF_8)
                                                  .hash()
                                                  .asBytes());
  }
}
