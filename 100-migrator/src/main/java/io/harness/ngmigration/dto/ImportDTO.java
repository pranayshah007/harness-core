/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationTrackReqPayload;
import io.harness.ngmigration.utils.CaseFormat;

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
public class ImportDTO extends MigrationTrackReqPayload {
  @Parameter(description = "The type of the entity that we wish to migrate")
  @NotNull
  private NGMigrationEntityType entityType;

  private DestinationDetails destinationDetails;

  @JsonTypeInfo(use = NAME, property = "entityType", include = EXTERNAL_PROPERTY, visible = true) private Filter filter;
  private BaseInputDTO inputs;
  private CaseFormat identifierCaseFormat;

  @Parameter(
      description =
          "If false the referenced entities of the entity to be migrate should already be migrated. If true the referenced entities will be migrated to the same scope as this entity if not already migrated to the same or parent scope")
  private boolean migrateReferencedEntities;

  @Parameter(description = "Required if trying migrate entities in an application") private String appId;

  private String accountIdentifier;
}
