/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.customstage;

import io.harness.encryption.Scope;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import java.util.List;

public interface CustomStageYamlSchemaService {
  PartialSchemaDTO getCustomStageYamlSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier,
      Scope scope, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList);
}
