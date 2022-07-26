/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.cache;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class YamlSchemaDetailsValue {
  String schema;
  String schemaClassName;
  ModuleType moduleType;
  YamlSchemaMetadata yamlSchemaMetadata;
  boolean isAvailableAtOrgLevel;
  boolean isAvailableAtAccountLevel;
  boolean isAvailableAtProjectLevel;
}
