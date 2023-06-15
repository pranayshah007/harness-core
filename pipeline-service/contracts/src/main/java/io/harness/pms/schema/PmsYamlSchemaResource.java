/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.schema;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.VERSION_FIELD;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.pms.yaml.YamlSchemaResponse;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/yaml-schema")
@Path("/yaml-schema")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(PIPELINE)
public interface PmsYamlSchemaResource {
  @GET
  @ApiOperation(value = "Get Yaml Schema", nickname = "getSchemaYaml")
  ResponseDTO<JsonNode> getYamlSchema(@QueryParam("entityType") @NotNull EntityType entityType,
      @QueryParam(PROJECT_KEY) String projectIdentifier, @QueryParam(ORG_KEY) String orgIdentifier,
      @QueryParam("scope") Scope scope, @QueryParam(IDENTIFIER_KEY) String identifier,
      @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier);

  @GET
  @Path("/static")
  @ApiOperation(value = "Get Static Yaml Schema", nickname = "getStaticSchemaYaml")
  @Hidden
  ResponseDTO<JsonNode> getStaticYamlSchema(@NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(ORG_KEY) String orgIdentifier, @QueryParam(PROJECT_KEY) String projectIdentifier,
      @QueryParam(IDENTIFIER_KEY) String identifier, @QueryParam("entityType") @NotNull EntityType entityType,
      @QueryParam("scope") Scope scope, @QueryParam(VERSION_FIELD) @DefaultValue("v0") String version);

  @POST
  @Path("/invalidate-cache")
  @ApiOperation(value = "Invalidate yaml schema cache", nickname = "invalidateYamlSchemaCache")
  ResponseDTO<Boolean> invalidateYamlSchemaCache();

  @GET
  @Path("/get")
  @ApiOperation(value = "Get step YAML schema", nickname = "getStepYamlSchema")
  ResponseDTO<YamlSchemaResponse> getIndividualYamlSchema(@NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(ORG_KEY) String orgIdentifier, @QueryParam(PROJECT_KEY) String projectIdentifier,
      @QueryParam("yamlGroup") String yamlGroup,
      @QueryParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType stepEntityType, @QueryParam("scope") Scope scope);

  @GET
  @ApiOperation(value = "dummy api for checking pms schema", nickname = "dummyApiForSwaggerSchemaCheck")
  @Path("/dummyApiForSwaggerSchemaCheck")
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  ResponseDTO<PipelineConfig> dummyApiForSwaggerSchemaCheck();
}
