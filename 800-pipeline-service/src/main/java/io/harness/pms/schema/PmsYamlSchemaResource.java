/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.schema;

import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.TRIGGERS;
import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.service.NGTriggerYamlSchemaService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.yaml.SchemaErrorResponse;
import io.harness.pms.yaml.YamlSchemaResponse;
import io.harness.yaml.schema.YamlSchemaResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;

@Api("/yaml-schema")
@Path("/yaml-schema")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@PipelineServiceAuth
@OwnedBy(PIPELINE)
public class PmsYamlSchemaResource implements YamlSchemaResource {
  private final PMSYamlSchemaService pmsYamlSchemaService;
  private final NGTriggerYamlSchemaService ngTriggerYamlSchemaService;

  @GET
  @ApiOperation(value = "Get Yaml Schema", nickname = "getSchemaYaml")
  public ResponseDTO<JsonNode> getYamlSchema(@QueryParam("entityType") @NotNull EntityType entityType,
      @QueryParam(PROJECT_KEY) String projectIdentifier, @QueryParam(ORG_KEY) String orgIdentifier,
      @QueryParam("scope") Scope scope, @QueryParam(IDENTIFIER_KEY) String identifier,
      @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier) {
    JsonNode schema = null;
    if (entityType == PIPELINES) {
      schema = pmsYamlSchemaService.getPipelineYamlSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope);
    } else if (entityType == TRIGGERS) {
      schema = ngTriggerYamlSchemaService.getTriggerYamlSchema(projectIdentifier, orgIdentifier, identifier, scope);
    } else {
      throw new NotSupportedException(String.format("Entity type %s is not supported", entityType.getYamlName()));
    }

    return ResponseDTO.newResponse(schema);
  }

  @POST
  @Path("/invalidate-cache")
  @ApiOperation(value = "Invalidate yaml schema cache", nickname = "invalidateYamlSchemaCache")
  public ResponseDTO<Boolean> invalidateYamlSchemaCache() {
    pmsYamlSchemaService.invalidateAllCache();
    return ResponseDTO.newResponse(true);
  }

  @GET
  @Path("/get")
  @ApiOperation(value = "Get step YAML schema", nickname = "getStepYamlSchema")
  public ResponseDTO<io.harness.pms.yaml.YamlSchemaResponse> getIndividualYamlSchema(
      @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier, @QueryParam(ORG_KEY) String orgIdentifier,
      @QueryParam(PROJECT_KEY) String projectIdentifier, @QueryParam("yamlGroup") String yamlGroup,
      @QueryParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType stepEntityType, @QueryParam("scope") Scope scope) {
    // TODO(Brijesh): write logic to handle empty schema when ff or feature restriction is off.
    JsonNode schema = pmsYamlSchemaService.getIndividualYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, scope, stepEntityType, yamlGroup);
    return ResponseDTO.newResponse(
        YamlSchemaResponse.builder().schema(schema).schemaErrorResponse(SchemaErrorResponse.builder().build()).build());
  }
}
