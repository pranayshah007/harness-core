/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cimanager.yamlschema.api;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(CI)
@Api("/partial-yaml-schema")
@Path("/partial-yaml-schema")
@Produces({"application/json", "text/yaml", "text/html", "text/plain"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface CIYamlSchemaResource {
  @GET
  @ApiOperation(value = "Get Partial Yaml Schema", nickname = "getPartialYamlSchema")
  ResponseDTO<List<PartialSchemaDTO>> getYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope);
  @GET
  @ApiOperation(value = "dummy api for checking integration stage", nickname = "dummyApiForSwaggerStageSchemaCheck")
  @Path("/dummyApiForSwaggerStageSchemaCheck")
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  ResponseDTO<StageElementConfig> dummyApiForSwaggerSchemaCheckForStage();

  @GET
  @ApiOperation(value = "dummy api for checking integration stage", nickname = "dummyApiForSwaggerStepSchemaCheck")
  @Path("/dummyApiForSwaggerStepSchemaCheck")
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  ResponseDTO<StepElementConfig> dummyApiForSwaggerSchemaCheckForStep();
  @GET
  @ApiOperation(value = "dummy api for checking CIPipelineModuleInfo",
      nickname = "dummyApiForSwaggerCIPipelineModuleInfoSchemaCheck")
  @Path("/dummyApiForSwaggerCIPipelineModuleInfoSchemaCheck")
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  ResponseDTO<CIPipelineModuleInfo>
  dummyApiForSwaggerSchemaCheckForCIPipelineModuleInfo();
  @GET
  @Path("/details")
  @ApiOperation(value = "Get Partial Yaml Schema with details", nickname = "getPartialYamlSchemaWithDetails")
  ResponseDTO<YamlSchemaDetailsWrapper> getYamlSchemaWithDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope);
  @POST
  @Path("/merged")
  @ApiOperation(value = "Get Merged Partial Yaml Schema", nickname = "getMergedPartialYamlSchema")
  ResponseDTO<List<PartialSchemaDTO>> getMergedYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope,
      @RequestBody(
          required = true, description = "Step Schema with details") YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper);
  @POST
  @ApiOperation(value = "Get step YAML schema", nickname = "getStepYamlSchema")
  ResponseDTO<JsonNode> getStepYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope,
      @QueryParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType, @QueryParam("yamlGroup") String yamlGroup,
      @RequestBody(
          required = true, description = "Step Schema with details") YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper);
}
