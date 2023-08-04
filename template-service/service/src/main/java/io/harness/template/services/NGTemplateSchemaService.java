/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity;

import com.fasterxml.jackson.databind.JsonNode;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(CDC)
public interface NGTemplateSchemaService {
  JsonNode getTemplateSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope,
      String templateChildType, TemplateEntityType templateEntityType);

  JsonNode getStaticYamlSchema(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateChildType, TemplateEntityType entityType, Scope scope, String version);

  void validateYamlSchemaInternal(TemplateEntity templateEntity);
  void validateYamlSchemaInternal(GlobalTemplateEntity templateEntity);
}
