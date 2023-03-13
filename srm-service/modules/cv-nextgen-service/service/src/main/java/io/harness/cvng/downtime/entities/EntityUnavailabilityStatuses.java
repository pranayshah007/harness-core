/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.downtime.beans.EntitiesRule;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "EntityUnavailabilityStatusesKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "entityUnavailabilityStatuses")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class EntityUnavailabilityStatuses implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware {
  @Id private String uuid;
  @NotNull @EntityIdentifier String accountId;
  @NotNull @EntityIdentifier String orgIdentifier;
  @NotNull @EntityIdentifier String projectIdentifier;
  @NotNull @EntityIdentifier private String entityIdentifier;
  @NotNull @EntityIdentifier private EntityType entityType;
  @NotNull private long startTime;
  @NotNull private long endTime;
  @NotNull private EntityUnavailabilityStatus status;
  @NotNull private long createdAt;
  @NotNull private long lastUpdatedAt;

  @NotNull EntitiesRule entitiesRule;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("entity_unavailability_start_end_idx")
                 .field(EntityUnavailabilityStatusesKeys.accountId)
                 .field(EntityUnavailabilityStatusesKeys.orgIdentifier)
                 .field(EntityUnavailabilityStatusesKeys.projectIdentifier)
                 .field(EntityUnavailabilityStatusesKeys.endTime)
                 .field(EntityUnavailabilityStatusesKeys.startTime)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project_idx")
                 .field(EntityUnavailabilityStatusesKeys.accountId)
                 .field(EntityUnavailabilityStatusesKeys.orgIdentifier)
                 .field(EntityUnavailabilityStatusesKeys.projectIdentifier)
                 .field(EntityUnavailabilityStatusesKeys.entityIdentifier)
                 .build())
        .build();
  }
}
