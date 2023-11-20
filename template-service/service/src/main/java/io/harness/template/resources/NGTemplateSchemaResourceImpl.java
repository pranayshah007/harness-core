package io.harness.template.resources;
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.services.NGTemplateSchemaService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGTemplateSchemaResourceImpl implements NGTemplateSchemaResource {
  private final NGTemplateSchemaService ngTemplateSchemaService;

  @Override
  public ResponseDTO<JsonNode> getTemplateSchema(@NotNull TemplateEntityType templateEntityType,
      String projectIdentifier, String orgIdentifier, Scope scope, @NotNull String accountIdentifier,
      String templateChildType) {
    JsonNode schema = ngTemplateSchemaService.getIndividualStaticSchema(
        templateEntityType.getRootYamlName(), templateChildType, "v0");
    // TODO (Shalini): remove this once ui and schema changes are also done
    if (schema == null) {
      schema = ngTemplateSchemaService.getIndividualStaticSchema(
          templateEntityType.getYamlTypeV1(), templateChildType, "v0");
    }
    return ResponseDTO.newResponse(schema);
  }
}
