package io.harness.template.resources;

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
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
    JsonNode schema = null;
    schema = ngTemplateSchemaService.getTemplateSchema(
        accountIdentifier, projectIdentifier, orgIdentifier, scope, templateChildType, templateEntityType);
    return ResponseDTO.newResponse(schema);
  }
}
