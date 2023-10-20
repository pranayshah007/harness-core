/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.dependency.ServiceDependencyMetadata;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "ServiceDependencyKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CVNG)
@Entity(value = "serviceDependencies")
@HarnessEntity(exportable = true)
@OwnedBy(CV)
public class ServiceDependency implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_edge_idx")
                 .unique(true)
                 .field(ServiceDependencyKeys.accountId)
                 .field(ServiceDependencyKeys.orgIdentifier)
                 .field(ServiceDependencyKeys.projectIdentifier)
                 .field(ServiceDependencyKeys.fromMonitoredServiceIdentifier)
                 .field(ServiceDependencyKeys.toMonitoredServiceIdentifier)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("list_all_edges_idx")
                 .unique(false)
                 .field(ServiceDependencyKeys.accountId)
                 .field(ServiceDependencyKeys.orgIdentifier)
                 .field(ServiceDependencyKeys.projectIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("list_dependent_svc_idx")
                 .unique(false)
                 .field(ServiceDependencyKeys.accountId)
                 .field(ServiceDependencyKeys.orgIdentifier)
                 .field(ServiceDependencyKeys.projectIdentifier)
                 .field(ServiceDependencyKeys.toMonitoredServiceIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("list_dependencies_idx")
                 .unique(false)
                 .field(ServiceDependencyKeys.accountId)
                 .field(ServiceDependencyKeys.orgIdentifier)
                 .field(ServiceDependencyKeys.projectIdentifier)
                 .field(ServiceDependencyKeys.fromMonitoredServiceIdentifier)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotNull String accountId;
  @EntityIdentifier String orgIdentifier;
  @EntityIdentifier String projectIdentifier;
  String fromMonitoredServiceIdentifier;
  String toMonitoredServiceIdentifier;
  ServiceDependencyMetadata serviceDependencyMetadata;
  private long lastUpdatedAt;
  private long createdAt;
  private String agentIdentifier;

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Key {
    String accountId;
    String orgIdentifier;
    String projectIdentifier;
    String fromMonitoredServiceIdentifier;
    String toMonitoredServiceIdentifier;
    ServiceDependencyMetadata serviceDependencyMetadata;
  }

  @JsonIgnore
  public Key getKey() {
    return Key.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .fromMonitoredServiceIdentifier(fromMonitoredServiceIdentifier)
        .toMonitoredServiceIdentifier(toMonitoredServiceIdentifier)
        .serviceDependencyMetadata(serviceDependencyMetadata)
        .build();
  }
}
