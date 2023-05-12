/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.serializer.CgEntityIdDeserializer;
import io.harness.ngmigration.utils.CaseFormat;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationInputDTO {
  private String destinationAuthToken;
  private String destinationAccountIdentifier;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private List<DiscoverEntityInput> entities;
  private Map<NGMigrationEntityType, InputDefaults> defaults;
  private boolean migrateReferencedEntities;
  @JsonDeserialize(keyUsing = CgEntityIdDeserializer.class) private Map<CgEntityId, BaseProvidedInput> overrides;
  private Map<String, Object> customExpressions;
  private CaseFormat identifierCaseFormat;
}
