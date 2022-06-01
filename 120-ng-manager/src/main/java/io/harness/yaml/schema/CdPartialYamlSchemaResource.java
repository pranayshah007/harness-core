/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.cdng.yaml.CdYamlSchemaService;
import io.harness.common.EntityTypeConstants;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;

@Api("/partial-yaml-schema")
@Path("/partial-yaml-schema")
@Produces({"application/json", "text/yaml", "text/html", "text/plain"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class CdPartialYamlSchemaResource implements YamlSchemaResource {
  CdYamlSchemaService cdYamlSchemaService;

  @GET
  @ApiOperation(value = "Get Partial Yaml Schema", nickname = "getPartialYamlSchema")
  public ResponseDTO<List<PartialSchemaDTO>> getYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope) {
    List<PartialSchemaDTO> schema =
        cdYamlSchemaService.getDeploymentStageYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier, scope);
    return ResponseDTO.newResponse(schema);
  }

  @GET
  @Path("/details")
  @ApiOperation(value = "Get Partial Yaml Schema with details", nickname = "getPartialYamlSchemaWithDetails")
  public ResponseDTO<YamlSchemaDetailsWrapper> getYamlSchemaWithDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope) {
    List<YamlSchemaWithDetails> schemaWithDetails = cdYamlSchemaService.getDeploymentStageYamlSchemaWithDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, scope);
    return ResponseDTO.newResponse(
        YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(schemaWithDetails).build());
  }

  @POST
  @Path("/merged")
  @ApiOperation(value = "Get Merged Partial Yaml Schema", nickname = "getMergedPartialYamlSchema")
  public ResponseDTO<List<PartialSchemaDTO>> getMergedYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope,
      @RequestBody(required = true,
          description = "Step Schema with details") YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper) {
    PartialSchemaDTO schema = cdYamlSchemaService.getMergedDeploymentStageYamlSchema(accountIdentifier,
        projectIdentifier, orgIdentifier, scope, yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList());
    return ResponseDTO.newResponse(Collections.singletonList(schema));
  }

  @POST
  @ApiOperation(value = "Get step YAML schema", nickname = "getStepYamlSchema")
  public ResponseDTO<JsonNode> getStepYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope,
      @QueryParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType, @QueryParam("yamlGroup") String yamlGroup,
      @RequestBody(required = true,
          description = "Step Schema with details") YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper) {
    if (yamlGroup.equals(StepCategory.STAGE.toString())) {
      // Add more cases when cd module contains more stages.
      if (entityType.getYamlName().equals(EntityTypeConstants.DEPLOYMENT_STAGE)) {
        return ResponseDTO.newResponse(
            cdYamlSchemaService
                .getMergedDeploymentStageYamlSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope,
                    yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList())
                .getSchema());
      } else {
        throw new InvalidRequestException(format("stage %s does not exist in module cd", entityType));
      }
    }
    return ResponseDTO.newResponse(
        cdYamlSchemaService.getIndividualYamlSchema(entityType, orgIdentifier, projectIdentifier, scope));
  }

  @GET
  @Path("/entityYaml")
  @Hidden
  public ResponseDTO<JsonNode> getEntityYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType, Scope scope) {
    return ResponseDTO.newResponse(
        cdYamlSchemaService.getIndividualYamlSchema(entityType, orgIdentifier, projectIdentifier, scope));
  }
}
