/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import static io.harness.annotations.dev.HarnessTeam.IACM;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.intfc.IACMYamlSchemaService;
import io.harness.common.EntityTypeConstants;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.yaml.schema.YamlSchemaResource;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;

@OwnedBy(IACM)
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
public class IACMYamlSchemaResource implements YamlSchemaResource {
  IACMYamlSchemaService iacmYamlSchemaService;

  @GET
  @ApiOperation(value = "Get Partial Yaml Schema", nickname = "getPartialYamlSchema")
  public ResponseDTO<List<PartialSchemaDTO>> getYamlSchema(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope) {
    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(
        iacmYamlSchemaService.getStageYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier, scope));

    return ResponseDTO.newResponse(partialSchemaDTOList);
  }

  @GET
  @Path("/details")
  @ApiOperation(value = "Get Partial Yaml Schema with details", nickname = "getPartialYamlSchemaWithDetails")
  public ResponseDTO<YamlSchemaDetailsWrapper> getYamlSchemaWithDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("scope") Scope scope) {
    List<YamlSchemaWithDetails> ciSchemaWithDetails =
        iacmYamlSchemaService.getStageYamlSchemaWithDetails(accountIdentifier, orgIdentifier, projectIdentifier, scope);
    return ResponseDTO.newResponse(
        YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(ciSchemaWithDetails).build());
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
    PartialSchemaDTO iacmSchema = iacmYamlSchemaService.getMergedStageYamlSchema(accountIdentifier, projectIdentifier,
        orgIdentifier, scope, yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList());

    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(iacmSchema);
    return ResponseDTO.newResponse(partialSchemaDTOList);
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
      // Add more cases when ci module contains more stages.
      if (entityType.getYamlName().equals(EntityTypeConstants.IACM)) {
        return ResponseDTO.newResponse(
            iacmYamlSchemaService
                .getMergedStageYamlSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope,
                    yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList())
                .getSchema());
      } else {
        throw new InvalidRequestException(format("stage %s does not exist in module ci", entityType));
      }
    }
    return ResponseDTO.newResponse(
        iacmYamlSchemaService.getIndividualYamlSchema(entityType, orgIdentifier, projectIdentifier, scope));
  }
}
