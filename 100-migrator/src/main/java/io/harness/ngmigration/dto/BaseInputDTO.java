/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.InputDefaults;
import io.harness.ngmigration.beans.MigrationInputSettings;
import io.harness.ngmigration.serializer.CgEntityIdDeserializer;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
public class BaseInputDTO {
  private Map<NGMigrationEntityType, InputDefaults> defaults;
  @JsonDeserialize(keyUsing = CgEntityIdDeserializer.class) private Map<CgEntityId, BaseProvidedInput> overrides;
  private Map<String, Object> expressions;

  private List<MigrationInputSettings> settings;
}
