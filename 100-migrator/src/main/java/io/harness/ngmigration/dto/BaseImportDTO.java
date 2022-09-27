/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class BaseImportDTO {
  @Parameter(description = "The type of the entity that we wish to migrate")
  @NotNull
  private NGMigrationEntityType type;

  @Parameter(description = "Provide the orgId if you wish to migrate the entity to either org or project")
  private String orgIdentifier;

  @Parameter(description = "Provide the projectId if you wish to migrate the entity to project")
  private String projectIdentifier;

  @Parameter(
      description =
          "If false the referenced entities of the entity to be migrate should already be migrated. If true the referenced entities will be migrated to the same scope as this entity if not already migrated to the same or parent scope")
  private boolean migrateReferencedEntities;

  @Parameter(description = "Required if trying migrate entities in an application") private String appId;

  private String accountIdentifier;
}
