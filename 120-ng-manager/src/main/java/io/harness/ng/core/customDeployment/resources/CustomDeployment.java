/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelperImpl;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.remote.TemplateResourceClient;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@Api("/customDeployment")
@Path("/customDeployment")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "CustomDeployment", description = "This contains APIs related to Custom Deployment")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CustomDeployment {
  public static final String DEPLOYMENT_PACKAGE_PARAM_MESSAGE = "Custom Deployment Identifier for the entity";
  @Inject TemplateResourceClient templateResourceClient;
  private final CustomDeploymentYamlHelperImpl customDeploymentYamlHelper;

  @GET
  @Path("/variables/{templateIdentifier}")
  @ApiOperation(value = "Gets Infra variables from a Custom Deployment Template by identifier",
      nickname = "getCustomDeploymentInfraVariables")
  @Operation(operationId = "getCustomDeploymentInfraVariables",
      summary = "Gets Infra Variables from a Custom Deployment Template by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Custom Deployment Infra Variables")
      })
  public ResponseDTO<String>
  getInfraVariables(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = DEPLOYMENT_PACKAGE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "Specifies whether Template is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    log.info(
        String.format("Retrieving Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
            templateIdentifier, versionLabel, projectId, orgId, accountId));
    TemplateResponseDTO response = NGRestUtils.getResponse(
        templateResourceClient.get(templateIdentifier, accountId, orgId, projectId, versionLabel, deleted));
    if (!response.getTemplateEntityType().equals(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)) {
      throw new InvalidRequestException("Template identifier provided is not a customDeployment template");
    }
    return ResponseDTO.newResponse(customDeploymentYamlHelper.getVariables(response.getYaml()));
  }

  @GET
  @Path("/connectors/{templateIdentifier}")
  @ApiOperation(value = "Gets Infra connectors from a Custom Deployment Template by identifier",
      nickname = "getCustomDeploymentConnectors")
  @Operation(operationId = "getCustomDeploymentConnectors",
      summary = "Gets Infra connectors from a Custom Deployment Template by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Custom Deployment Infra connectors")
      })
  public ResponseDTO<String>
  getInfraConnectors(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = DEPLOYMENT_PACKAGE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "Specifies whether Template is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    log.info(
        String.format("Retrieving Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
            templateIdentifier, versionLabel, projectId, orgId, accountId));
    TemplateResponseDTO response = NGRestUtils.getResponse(
        templateResourceClient.get(templateIdentifier, accountId, orgId, projectId, versionLabel, deleted));
    if (!response.getTemplateEntityType().equals(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)) {
      throw new InvalidRequestException("Template identifier provided is not a customDeployment template");
    }
    return ResponseDTO.newResponse(customDeploymentYamlHelper.getConnectors(response.getYaml()));
  }

  @POST
  @Path("/expression-variables")
  @ApiOperation(
      value = "Gets Custom Deployment Expression Variables", nickname = "getCustomDeploymentExpressionVariables")
  @Operation(operationId = "getCustomDeploymentExpressionVariables",
      summary = "Gets Custom Deployment Expression Variables",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in template.")
      })
  public ResponseDTO<CustomDeploymentVariableResponseDTO>
  getExpressionVariables(
      @RequestBody(required = true, description = "Custom Deployment Yaml Request DTO containing entityYaml")
      @NotNull CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO) {
    log.info("Retrieving Variables for Custom Deployment Template");
    CustomDeploymentVariableResponseDTO response =
        customDeploymentYamlHelper.getVariablesFromYaml(customDeploymentYamlRequestDTO);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @Path("/get-references")
  @ApiOperation(value = "Gets Custom Deployment entity references", nickname = "getCustomDeploymentEntityReferences")
  @Operation(operationId = "getCustomDeploymentEntityReferences", summary = "Gets Custom Deployment Entity References",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns all entity references in the custom deployment template.")
      })
  public ResponseDTO<List<EntityDetailProtoDTO>>
  getEntityReferences(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true, description = "Custom Deployment Yaml Request DTO containing entityYaml")
      @NotNull CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO) {
    log.info("Retrieving entity references for Custom Deployment Template");
    List<EntityDetailProtoDTO> response = customDeploymentYamlHelper.getReferencesFromYaml(
        accountId, orgId, projectId, customDeploymentYamlRequestDTO.getEntityYaml());
    return ResponseDTO.newResponse(response);
  }
}
