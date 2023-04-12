/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.resources.EnvironmentResourceV2.ENVIRONMENT_PARAM_MESSAGE;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.beans.DocumentationConstants;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureInputsMergedResponseDto;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadata;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadataApiInput;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadataDTO;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@NextGenManagerAuth
@Api("/infrastructures")
@Path("/infrastructures")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Infrastructures", description = "This contains APIs related to Infrastructure Definitions")
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
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class InfrastructureResource {
  public static final String INFRASTRUCTURE_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "List of Infrastructure Identifiers for the entities";
  @Inject private final InfrastructureEntityService infrastructureEntityService;
  @Inject private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private final EnvironmentValidationHelper environmentValidationHelper;
  @Inject private final AccessControlClient accessControlClient;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Inject private final SshEntityHelper sshEntityHelper;
  private final NGFeatureFlagHelperService featureFlagHelperService;
  private InfrastructureYamlSchemaHelper infrastructureYamlSchemaHelper;

  public static final String INFRA_PARAM_MESSAGE = "Infrastructure Identifier for the entity";

  @GET
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Gets an Infrastructure by identifier", nickname = "getInfrastructure")
  @Operation(operationId = "getInfrastructure", summary = "Gets an Infrastructure by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "The saved Infrastructure") })
  public ResponseDTO<InfrastructureResponse>
  get(@Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = "Specify whether Infrastructure is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");

    Optional<InfrastructureEntity> infraEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);

    if (infraEntity.isPresent()) {
      if (isEmpty(infraEntity.get().getYaml())) {
        InfrastructureConfig infrastructureConfig =
            InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity.get());
        infraEntity.get().setYaml(InfrastructureEntityConfigMapper.toYaml(infrastructureConfig));
      }
    } else {
      throw new NotFoundException(
          format("Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
              infraIdentifier, projectIdentifier, orgIdentifier, envIdentifier));
    }
    return ResponseDTO.newResponse(infraEntity.map(InfrastructureMapper::toResponseWrapper).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create an Infrastructure in an Environment", nickname = "createInfrastructure")
  @Operation(operationId = "createInfrastructure", summary = "Create an Infrastructure in an Environment",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the created Infrastructure") })
  public ResponseDTO<InfrastructureResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be created", content = {
        @Content(
            examples = @ExampleObject(name = "Create", summary = "Sample Infrastructure create payload",
                value = DocumentationConstants.infrastructureRequestDTO, description = "Sample Infrastructure payload"))
      }) @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    infrastructureYamlSchemaHelper.validateSchema(accountId, infrastructureRequestDTO.getYaml());
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    validateProjectLevelInfraScope(infrastructureEntity, accountId);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier());
    // access for updating Environment
    checkForAccessOrThrow(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    InfrastructureEntity createdInfrastructure = infrastructureEntityService.create(infrastructureEntity);
    return ResponseDTO.newResponse(InfrastructureMapper.toResponseWrapper(createdInfrastructure));
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Create Infrastructures", nickname = "createInfrastructures")
  @Operation(operationId = "createInfrastructures", summary = "Create Infrastructures",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the created Infrastructures") },
      hidden = true)
  public ResponseDTO<PageResponse<InfrastructureResponse>>
  createInfrastructures(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Infrastructures to be created")
      @Valid List<InfrastructureRequestDTO> infrastructureRequestDTOS) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTOS);
    boolean isInfraScoped = checkFeatureFlagForEnvOrgAccountLevel(accountId);
    infrastructureRequestDTOS.forEach(dto -> infrastructureYamlSchemaHelper.validateSchema(accountId, dto.getYaml()));
    List<InfrastructureEntity> entities = infrastructureRequestDTOS.stream()
                                              .map(dto -> InfrastructureMapper.toInfrastructureEntity(accountId, dto))
                                              .collect(Collectors.toList());
    entities.forEach(entity -> {
      if (isInfraScoped) {
        validateProjectLevelInfraScope(entity);
      } else {
        mustBeAtProjectLevel(entity);
      }
      orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
          entity.getOrgIdentifier(), entity.getProjectIdentifier(), accountId);
      environmentValidationHelper.checkThatEnvExists(
          accountId, entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getEnvIdentifier());
    });

    checkForAccessBatch(accountId, entities, ENVIRONMENT_UPDATE_PERMISSION);
    Page<InfrastructureEntity> createdInfrastructures = infrastructureEntityService.bulkCreate(accountId, entities);
    return ResponseDTO.newResponse(
        getNGPageResponse(createdInfrastructures.map(InfrastructureMapper::toResponseWrapper)));
  }

  @DELETE
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Delete an infrastructure by identifier", nickname = "deleteInfrastructure")
  @Operation(operationId = "deleteInfrastructure", summary = "Delete an Infrastructure by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Infrastructure is deleted")
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
             "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE) @DefaultValue(
          "false") boolean forceDelete) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "delete");

    return ResponseDTO.newResponse(infrastructureEntityService.delete(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier, forceDelete));
  }

  @PUT
  @ApiOperation(value = "Update an Infrastructure by identifier", nickname = "updateInfrastructure")
  @Operation(operationId = "updateInfrastructure", summary = "Update an Infrastructure by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Infrastructure") })
  public ResponseDTO<InfrastructureResponse>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be updated", content = {
        @Content(
            examples = @ExampleObject(name = "Update", summary = "Sample Infrastructure update payload",
                value = DocumentationConstants.infrastructureRequestDTO, description = "Sample Infrastructure payload"))
      }) @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    infrastructureYamlSchemaHelper.validateSchema(accountId, infrastructureRequestDTO.getYaml());
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    validateProjectLevelInfraScope(infrastructureEntity, accountId);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier());

    checkForAccessOrThrow(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier(),
        ENVIRONMENT_UPDATE_PERMISSION, "update");
    validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    return ResponseDTO.newResponse(
        InfrastructureMapper.toResponseWrapper(infrastructureEntityService.update(infrastructureEntity)));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert an Infrastructure by identifier", nickname = "upsertInfrastructure")
  @Operation(operationId = "upsertInfrastructure", summary = "Upsert an Infrastructure by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the upserted Infrastructure") },
      hidden = true)
  public ResponseDTO<InfrastructureResponse>
  upsert(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be upsert", content = {
        @Content(
            examples = @ExampleObject(name = "Upsert", summary = "Sample Infrastructure upsert payload",
                value = DocumentationConstants.infrastructureRequestDTO, description = "Sample Infrastructure payload"))
      }) @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    infrastructureYamlSchemaHelper.validateSchema(accountId, infrastructureRequestDTO.getYaml());
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    validateProjectLevelInfraScope(infrastructureEntity, accountId);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier());

    checkForAccessOrThrow(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier(),
        ENVIRONMENT_UPDATE_PERMISSION, "upsert");
    validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    return ResponseDTO.newResponse(InfrastructureMapper.toResponseWrapper(
        infrastructureEntityService.upsert(infrastructureEntity, UpsertOptions.DEFAULT)));
  }

  @GET
  @ApiOperation(value = "Gets Infrastructure list ", nickname = "getInfrastructureList")
  @Operation(operationId = "getInfrastructureList", summary = "Gets Infrastructure list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Infrastructure for an Environment")
      })
  public ResponseDTO<PageResponse<InfrastructureResponse>>
  listInfrastructures(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String envIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of InfrastructureIds") @QueryParam("infraIdentifiers")
      List<String> infraIdentifiers, @QueryParam("deploymentType") ServiceDefinitionType deploymentType,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "list");

    Criteria criteria = InfrastructureFilterHelper.createListCriteria(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm, infraIdentifiers, deploymentType);
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<InfrastructureEntity> infraEntities = infrastructureEntityService.list(criteria, pageRequest);
    if (ServiceDefinitionType.CUSTOM_DEPLOYMENT == deploymentType && !isEmpty(deploymentTemplateIdentifier)
        && !isEmpty(versionLabel)) {
      infraEntities = customDeploymentYamlHelper.getFilteredInfraEntities(
          page, size, sort, deploymentTemplateIdentifier, versionLabel, infraEntities);
    }
    return ResponseDTO.newResponse(getNGPageResponse(infraEntities.map(InfrastructureMapper::toResponseWrapper)));
  }

  @GET
  @Path("/dummy-infraConfig-api")
  @ApiOperation(value = "This is dummy api to expose infraConfig", nickname = "dummyInfraConfigApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<InfrastructureConfig> getInfraConfig() {
    return ResponseDTO.newResponse(InfrastructureConfig.builder().build());
  }

  @GET
  @Path("/runtimeInputs")
  @ApiOperation(value = "This api returns Infrastructure Definition inputs YAML", nickname = "getInfrastructureInputs")
  @Hidden
  public ResponseDTO<NGEntityTemplateResponseDTO> getInfrastructureInputs(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = "List of Infrastructure Identifiers") @QueryParam(
          NGCommonEntityConstants.INFRA_IDENTIFIERS) List<String> infraIdentifiers,
      @Parameter(description = "Specify whether Deploy to all infrastructures in the environment") @QueryParam(
          NGCommonEntityConstants.DEPLOY_TO_ALL) @DefaultValue("false") boolean deployToAll) {
    String infrastructureInputsYaml =
        infrastructureEntityService.createInfrastructureInputsFromYaml(accountId, orgIdentifier, projectIdentifier,
            environmentIdentifier, infraIdentifiers, deployToAll, NoInputMergeInputAction.RETURN_EMPTY);
    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(infrastructureInputsYaml).build());
  }

  private void throwExceptionForNoRequestDTO(InfrastructureRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier,envIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(List<InfrastructureRequestDTO> dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier, envIdentifier, tags, description");
    }
  }

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s infrastructure(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }

  private void checkForAccessBatch(
      String accountId, List<InfrastructureEntity> infrastructureRequestDTOList, String permission) {
    Map<String, Boolean> accessMap = new HashMap<>();
    for (InfrastructureEntity entity : infrastructureRequestDTOList) {
      StringJoiner joiner = new StringJoiner("|");
      joiner.add(entity.getOrgIdentifier()).add(entity.getProjectIdentifier()).add(entity.getEnvIdentifier());
      String key = joiner.toString();

      accessMap.computeIfAbsent(key,
          k
          -> accessControlClient.hasAccess(
              ResourceScope.of(accountId, entity.getOrgIdentifier(), entity.getProjectIdentifier()),
              Resource.of(NGResourceType.ENVIRONMENT, entity.getEnvIdentifier()), permission));

      if (Boolean.FALSE.equals(accessMap.get(key))) {
        throw new NGAccessDeniedException(
            format("Missing permissions %s on %s", permission, key), WingsException.USER, null);
      }
    }
  }

  @POST
  @Path("/infrastructureYamlMetadata")
  @ApiOperation(value = "This api returns infrastructure YAML and runtime input YAML",
      nickname = "getInfrastructureYamlAndRuntimeInputs")
  @Hidden
  public ResponseDTO<InfrastructureYamlMetadataDTO>
  getInfrastructureYamlAndRuntimeInputs(@Parameter(description = INFRASTRUCTURE_YAML_METADATA_INPUT_PARAM_MESSAGE)
                                        @Valid @NotNull InfrastructureYamlMetadataApiInput infrastructureYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier) {
    List<InfrastructureYamlMetadata> infrastructureYamlMetadataList =
        infrastructureEntityService.createInfrastructureYamlMetadata(accountId, orgIdentifier, projectIdentifier,
            environmentIdentifier, infrastructureYamlMetadata.getInfrastructureIdentifiers());
    return ResponseDTO.newResponse(
        InfrastructureYamlMetadataDTO.builder().infrastructureYamlMetadataList(infrastructureYamlMetadataList).build());
  }

  @POST
  @Path("/mergeInfrastructureInputs/{infraIdentifier}")
  @ApiOperation(value = "This api merges old and new infrastructure inputs YAML", nickname = "mergeInfraInputs")
  @Hidden
  public ResponseDTO<InfrastructureInputsMergedResponseDto> mergeInfrastructureInputs(
      @Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY, required = true) @NotNull
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      String oldInfrastructureInputsYaml) {
    return ResponseDTO.newResponse(infrastructureEntityService.mergeInfraStructureInputs(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier, oldInfrastructureInputsYaml));
  }

  private void validateDeploymentTypeSpecificInfrastructureYaml(InfrastructureEntity infrastructureEntity) {
    ServiceDefinitionType deploymentType = infrastructureEntity.getDeploymentType();
    if (deploymentType == ServiceDefinitionType.CUSTOM_DEPLOYMENT
        && infrastructureEntity.getType() == InfrastructureType.CUSTOM_DEPLOYMENT
        && (customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity))) {
      throw new InvalidRequestException(
          "Infrastructure yaml is not valid, template variables and infra variables doesn't match");
    }

    if (deploymentType == ServiceDefinitionType.SSH || deploymentType == ServiceDefinitionType.WINRM) {
      sshEntityHelper.validateInfrastructureYaml(infrastructureEntity);
    }
  }

  private void validateProjectLevelInfraScope(InfrastructureEntity entity, String accountId) {
    try {
      if (checkFeatureFlagForEnvOrgAccountLevel(accountId)) {
        if (isNotEmpty(entity.getProjectIdentifier())) {
          Preconditions.checkArgument(isNotEmpty(entity.getOrgIdentifier()),
              "org identifier must be specified when project identifier is specified. Infra can be created at Project/Org/Account scope");
        }
      } else {
        Preconditions.checkArgument(isNotEmpty(entity.getOrgIdentifier()),
            "org identifier must be specified. Infrastructure Definitions can only be created at Project scope");
        Preconditions.checkArgument(isNotEmpty(entity.getProjectIdentifier()),
            "project identifier must be specified. Infrastructure Definitions can only be created at Project scope");
      }
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private void validateProjectLevelInfraScope(InfrastructureEntity entity) {
    try {
      if (isNotEmpty(entity.getProjectIdentifier())) {
        Preconditions.checkArgument(isNotEmpty(entity.getOrgIdentifier()),
            "org identifier must be specified when project identifier is specified. Infra can be created at Project/Org/Account scope");
      }
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private void mustBeAtProjectLevel(InfrastructureEntity requestDTO) {
    try {
      Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
          "org identifier must be specified. Infrastructure Definitions can only be created at Project scope");
      Preconditions.checkArgument(isNotEmpty(requestDTO.getProjectIdentifier()),
          "project identifier must be specified. Infrastructure Definitions can only be created at Project scope");
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private boolean checkFeatureFlagForEnvOrgAccountLevel(String accountId) {
    return featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_OrgAccountLevelServiceEnvEnvGroup);
  }
}
