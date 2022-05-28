/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@OwnedBy(HarnessTeam.PIPELINE)
public class InfrastructureResource {
  @Inject private final InfrastructureEntityService infrastructureEntityService;
  @Inject private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private final EnvironmentValidationHelper environmentValidationHelper;
  @Inject private final AccessControlClient accessControlClient;

  public static final String INFRA_PARAM_MESSAGE = "Infrastructure Identifier for the entity";

  @GET
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Gets an Infrastructure by identifier", nickname = "getInfrastructure")
  @NGAccessControlCheck(resourceType = NGResourceType.ENVIRONMENT, permission = "core_environment_view")
  @Operation(operationId = "getInfrastructure", summary = "Gets an Infrastructure by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "The saved Infrastructure") },
      hidden = true)
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
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @AccountIdentifier String envIdentifier,
      @Parameter(description = "Specify whether Infrastructure is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");

    Optional<InfrastructureEntity> infraEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);

    if (infraEntity.isPresent()) {
      // todo: why do we need this?
      if (isEmpty(infraEntity.get().getYaml())) {
        InfrastructureConfig infrastructureConfig =
            InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity.get());
        infraEntity.get().setYaml(InfrastructureEntityConfigMapper.toYaml(infrastructureConfig));
      }
    } else {
      throw new NotFoundException(
          String.format("Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
              infraIdentifier, projectIdentifier, orgIdentifier, envIdentifier));
    }
    return ResponseDTO.newResponse(infraEntity.map(InfrastructureMapper::toResponseWrapper).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create an Infrastructure in an Environment", nickname = "createInfrastructure")
  @Operation(operationId = "createInfrastructure", summary = "Create an Infrastructure in an Environment",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the created Infrastructure") },
      hidden = true)
  public ResponseDTO<InfrastructureResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Infrastructure to be created")
      @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureRequestDTO.getOrgIdentifier(), infrastructureRequestDTO.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureRequestDTO.getOrgIdentifier(),
        infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef());
    // access for updating Environment
    checkForAccessOrThrow(accountId, infrastructureRequestDTO.getOrgIdentifier(),
        infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);

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
    infrastructureRequestDTOS.forEach(infrastructureRequestDTO -> {
      orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
          infrastructureRequestDTO.getOrgIdentifier(), infrastructureRequestDTO.getProjectIdentifier(), accountId);
      environmentValidationHelper.checkThatEnvExists(accountId, infrastructureRequestDTO.getOrgIdentifier(),
          infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef());
    });

    checkForAccessBatch(accountId, infrastructureRequestDTOS, ENVIRONMENT_UPDATE_PERMISSION);
    List<InfrastructureEntity> infrastructureEntities =
        infrastructureRequestDTOS.stream()
            .map(infrastructureRequestDTO
                -> InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO))
            .collect(Collectors.toList());

    Page<InfrastructureEntity> createdInfrastructures =
        infrastructureEntityService.bulkCreate(accountId, infrastructureEntities);
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
      },
      hidden = true)
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
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ProjectIdentifier String envIdentifier) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "delete");

    return ResponseDTO.newResponse(infrastructureEntityService.delete(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier));
  }

  @PUT
  @ApiOperation(value = "Update an Infrastructure by identifier", nickname = "updateInfrastructure")
  @Operation(operationId = "updateInfrastructure", summary = "Update an Infrastructure by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Infrastructure") },
      hidden = true)
  public ResponseDTO<InfrastructureResponse>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Infrastructure to be updated")
      @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureRequestDTO.getOrgIdentifier(), infrastructureRequestDTO.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureRequestDTO.getOrgIdentifier(),
        infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef());

    checkForAccessOrThrow(accountId, infrastructureRequestDTO.getOrgIdentifier(),
        infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "update");

    InfrastructureEntity requestInfrastructure =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    InfrastructureEntity updatedInfra = infrastructureEntityService.update(requestInfrastructure);
    return ResponseDTO.newResponse(InfrastructureMapper.toResponseWrapper(updatedInfra));
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
      @Parameter(description = "Details of the Infrastructure to be updated")
      @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureRequestDTO.getOrgIdentifier(), infrastructureRequestDTO.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureRequestDTO.getOrgIdentifier(),
        infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef());

    checkForAccessOrThrow(accountId, infrastructureRequestDTO.getOrgIdentifier(),
        infrastructureRequestDTO.getProjectIdentifier(), infrastructureRequestDTO.getEnvironmentRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "upsert");

    InfrastructureEntity requestInfra =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    InfrastructureEntity upsertInfra = infrastructureEntityService.upsert(requestInfra);
    return ResponseDTO.newResponse(InfrastructureMapper.toResponseWrapper(upsertInfra));
  }

  @GET
  @ApiOperation(value = "Gets Infrastructure list ", nickname = "getInfrastructureList")
  @Operation(operationId = "getInfrastructureList", summary = "Gets Infrastructure list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Infrastructure for an Environment")
      },
      hidden = true)
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
      @Parameter(description = "List of InfrastructureIds") @QueryParam(
          "infraIdentifiers") List<String> infraIdentifiers,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "list");

    Criteria criteria = InfrastructureFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm);
    Pageable pageRequest;
    if (isNotEmpty(infraIdentifiers)) {
      criteria.and(InfrastructureEntityKeys.identifier).in(infraIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<InfrastructureEntity> infraEntities = infrastructureEntityService.list(criteria, pageRequest);

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
    String exceptionMessage = String.format("unable to %s infrastructure(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }

  private void checkForAccessBatch(
      String accountId, List<InfrastructureRequestDTO> infrastructureRequestDTOList, String permission) {
    Map<String, Boolean> accessMap = new HashMap<>();
    for (InfrastructureRequestDTO infrastructureRequestDTO : infrastructureRequestDTOList) {
      StringJoiner joiner = new StringJoiner("|");
      joiner.add(infrastructureRequestDTO.getOrgIdentifier())
          .add(infrastructureRequestDTO.getProjectIdentifier())
          .add(infrastructureRequestDTO.getEnvironmentRef());
      String key = joiner.toString();

      accessMap.computeIfAbsent(key,
          k
          -> accessControlClient.hasAccess(ResourceScope.of(accountId, infrastructureRequestDTO.getOrgIdentifier(),
                                               infrastructureRequestDTO.getProjectIdentifier()),
              Resource.of(NGResourceType.ENVIRONMENT, infrastructureRequestDTO.getEnvironmentRef()), permission));

      if (!accessMap.get(key)) {
        throw new NGAccessDeniedException(
            String.format("Missing permissions %s on %s", permission, key), WingsException.USER, null);
      }
    }
  }
}
