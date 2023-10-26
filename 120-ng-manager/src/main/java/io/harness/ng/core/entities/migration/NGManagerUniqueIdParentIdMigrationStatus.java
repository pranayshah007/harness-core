/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entities.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "NGManagerUniqueIdParentIdMigrationStatusKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "uniqueIdParentIdMigrationStatus", noClassnameStored = true)
@Document("uniqueIdParentIdMigrationStatus")
@TypeAlias("ngManagerUniqueIdParentIdMigrationStatus")
public class NGManagerUniqueIdParentIdMigrationStatus implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty String entityClassName;
  Boolean uniqueIdMigrationCompleted;
  Boolean parentIdMigrationCompleted;
  @NotEmpty @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
}
