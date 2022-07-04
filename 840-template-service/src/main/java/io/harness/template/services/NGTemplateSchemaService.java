/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.template.entity.TemplateEntity;

import com.fasterxml.jackson.databind.JsonNode;

@OwnedBy(CDC)
public interface NGTemplateSchemaService {
  JsonNode getTemplateSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope,
      String templateChildType, TemplateEntityType templateEntityType);

  void validateYamlSchemaInternal(TemplateEntity templateEntity);
}
