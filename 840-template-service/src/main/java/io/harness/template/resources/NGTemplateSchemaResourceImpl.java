/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.services.NGTemplateSchemaService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ENTITY_TYPE;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGTemplateSchemaResourceImpl implements NGTemplateSchemaResource {
  private final NGTemplateSchemaService ngTemplateSchemaService;

  public ResponseDTO<JsonNode>
  getTemplateSchema(@QueryParam("templateEntityType") @NotNull TemplateEntityType templateEntityType,
      @QueryParam(PROJECT_KEY) String projectIdentifier, @QueryParam(ORG_KEY) String orgIdentifier,
      @QueryParam("scope") Scope scope, @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(ENTITY_TYPE) String templateChildType) {
    JsonNode schema = null;
    schema = ngTemplateSchemaService.getTemplateSchema(
        accountIdentifier, projectIdentifier, orgIdentifier, scope, templateChildType, templateEntityType);
    return ResponseDTO.newResponse(schema);
  }
}
