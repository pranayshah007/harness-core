/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;
import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;

import com.fasterxml.jackson.databind.JsonNode;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public interface PMSYamlSchemaService {
  JsonNode getPipelineYamlSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope);

  boolean validateYamlSchema(String accountId, String orgId, String projectId, JsonNode jsonNode);

  void validateUniqueFqn(String yaml);

  void invalidateAllCache();

  JsonNode getIndividualYamlSchema(String accountId, String orgIdentifier, String projectIdentifier, Scope scope,
      EntityType entityType, String yamlGroup);

  JsonNode getStaticSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, String identifier,
      EntityType entityType, Scope scope, String version);
}
