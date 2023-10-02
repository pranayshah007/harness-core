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
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.CustomDeploymentInfraResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentRefreshYamlDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.NextGenManagerAuth;
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
import java.util.Optional;
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
import retrofit2.http.Body;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
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
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;

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
    customDeploymentYamlHelper.validateTemplateYaml(customDeploymentYamlRequestDTO.getEntityYaml());
    List<EntityDetailProtoDTO> response = customDeploymentYamlHelper.getReferencesFromYaml(
        accountId, orgId, projectId, customDeploymentYamlRequestDTO.getEntityYaml());
    return ResponseDTO.newResponse(response);
  }

  @GET
  @Path("/validate-infrastructure/{infraIdentifier}")
  @ApiOperation(value = "This validates whether Infrastructure is valid or not",
      nickname = "validateInfrastructureForDeploymentTemplate")
  @Operation(operationId = "validateInfrastructureForDeploymentTemplate",
      summary = "This validates whether Infrastructure is valid or not",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns if the infra is valid or not.")
      })
  public ResponseDTO<CustomDeploymentInfraResponseDTO>
  validateInfrastructure(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Environment Identifier for the entity") @QueryParam(
          "envIdentifier") @ResourceIdentifier String envIdentifier,
      @Parameter(description = "Infrastructure Identifier for the entity") @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier) {
    Optional<InfrastructureEntity> infraEntityOptional =
        infrastructureEntityService.get(accountId, orgId, projectId, envIdentifier, infraIdentifier);
    if (!infraEntityOptional.isPresent()) {
      throw new InvalidRequestException("Infra/enviornment identifier provided is not a valid Indentifier");
    }
    InfrastructureEntity infraEntity = infraEntityOptional.get();
    CustomDeploymentInfraResponseDTO response =
        CustomDeploymentInfraResponseDTO.builder().obsolete(infraEntity.getObsolete()).build();
    return ResponseDTO.newResponse(response);
  }

  @POST
  @Path("/get-updated-Yaml/{infraIdentifier}")
  @ApiOperation(value = "Return the updated yaml for infrastructure based on Deployment template",
      nickname = "getUpdatedYamlForInfrastructure")
  @Operation(operationId = "getUpdatedYamlForInfrastructure",
      summary = "Return the updated yaml for infrastructure based on Deployment template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Return the updated yaml for infrastructure based on Deployment template")
      })
  public ResponseDTO<CustomDeploymentRefreshYamlDTO>
  getUpdatedYamlForInfra(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Infrastructure Identifier for the entity") @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = "YAML") @NotNull @Body CustomDeploymentYamlDTO refreshRequestDTO) {
    String infraYaml = refreshRequestDTO.getYaml();
    StepTemplateRef customDeploymentRef =
        customDeploymentInfrastructureHelper.getStepTemplateRefFromInfraYaml(infraYaml, accountId);
    TemplateResponseDTO responseTemplate = customDeploymentYamlHelper.getScopedTemplateResponseDTO(
        accountId, orgId, projectId, customDeploymentRef.getTemplateRef(), customDeploymentRef.getVersionLabel());
    if (!responseTemplate.getTemplateEntityType().equals(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)) {
      throw new InvalidRequestException("Template identifier provided is not a customDeployment template");
    }
    String updatedYaml = customDeploymentYamlHelper.getUpdatedYaml(responseTemplate.getYaml(), infraYaml, accountId);
    CustomDeploymentRefreshYamlDTO response =
        CustomDeploymentRefreshYamlDTO.builder().refreshedYaml(updatedYaml).build();
    return ResponseDTO.newResponse(response);
  }
}
