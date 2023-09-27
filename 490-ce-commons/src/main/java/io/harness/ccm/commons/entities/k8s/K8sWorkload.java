/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.k8s;

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

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.PostLoad;
import dev.morphia.annotations.PrePersist;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "k8sWorkload", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "K8sWorkloadKeys")
@OwnedBy(CE)
public final class K8sWorkload implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("no_dup_cluster_uid")
                 .unique(true)
                 .field(K8sWorkloadKeys.accountId)
                 .field(K8sWorkloadKeys.clusterId)
                 .field(K8sWorkloadKeys.uid)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_clusterId_namespace_name_lastUpdatedAt")
                 .field(K8sWorkloadKeys.accountId)
                 .field(K8sWorkloadKeys.clusterId)
                 .field(K8sWorkloadKeys.namespace)
                 .field(K8sWorkloadKeys.name)
                 .descSortField(K8sWorkloadKeys.lastUpdatedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_name_labels")
                 .field(K8sWorkloadKeys.accountId)
                 .field(K8sWorkloadKeys.name)
                 .field(K8sWorkloadKeys.labels)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_lastUpdatedAt")
                 .field(K8sWorkloadKeys.accountId)
                 .field(K8sWorkloadKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_clusterId_kind_name_namespace_decreasing_lastUpdatedAt")
                 .fields(Arrays.asList(K8sWorkloadKeys.accountId, K8sWorkloadKeys.clusterId, K8sWorkloadKeys.kind,
                     K8sWorkloadKeys.name, K8sWorkloadKeys.namespace))
                 .descSortField(K8sWorkloadKeys.lastUpdatedAt)
                 .build())
        .build();
  }

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotEmpty String accountId;
  @NotEmpty String clusterId;
  @NotEmpty String settingId;

  @NotEmpty String name;
  @NotEmpty String namespace;
  @NotEmpty String uid;
  @NotEmpty String kind;
  Map<String, String> labels;

  @FdTtlIndex private Date ttl;

  // Mongo has problems for values having dot/period ('.') character. We replace dot with tilde
  // which is not an allowed k8s label character.
  @PrePersist
  public void prePersist() {
    this.labels = Optional.ofNullable(labels).map(K8sWorkload::encodeDotsInKey).orElse(null);
  }

  @PostLoad
  public void postLoad() {
    this.labels = Optional.ofNullable(labels).map(K8sWorkload::decodeDotsInKey).orElse(null);
  }

  public static Map<String, String> encodeDotsInKey(@NonNull Map<String, String> labels) {
    return labels.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().replace('.', '~'), Map.Entry::getValue));
  }

  public static Map<String, String> decodeDotsInKey(@NonNull Map<String, String> labels) {
    return labels.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().replace('~', '.'), Map.Entry::getValue));
  }
}
