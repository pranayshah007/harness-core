/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import io.harness.EntityType;
import io.harness.encryption.Scope;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface SchemaGetter {
  List<PartialSchemaDTO> getSchema(List<YamlSchemaWithDetails> yamlSchemaWithDetailsLis);
  YamlSchemaDetailsWrapper getSchemaDetails();
  JsonNode fetchStepYamlSchema(String orgIdentifier, String projectIdentifier, Scope scope, EntityType entityType,
      String yamlGroup, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList);
}
