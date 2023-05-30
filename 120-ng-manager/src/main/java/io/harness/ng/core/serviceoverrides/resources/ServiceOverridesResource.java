/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.resources;

import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideCriteriaHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideV2MigrationService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverridesServiceV2;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverridesMapperV2;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;

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
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.Max;
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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@NextGenManagerAuth
@Api("/serviceOverrides")
@Hidden
@Path("/serviceOverrides")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "ServiceOverrides", description = "This contains APIs related to Service Overrides V2")
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
public class ServiceOverridesResource {
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject ServiceOverrideV2MigrationService serviceOverrideV2MigrationService;
  @Inject private EnvironmentService environmentService;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ServiceOverrideValidatorService overrideValidatorService;

  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private static final int MAX_LIMIT = 1000;

  @GET
  @Path("/{identifier}")
  @ApiOperation(value = "Gets Service Overrides by Identifier", nickname = "getServiceOverridesV2")
  @Operation(operationId = "getServiceOverrides", summary = "Gets Service Overrides by Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description =
                "Returns the Service Overrides by the identifier and scope derived from accountId, org identifier and project identifier")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  get(@Parameter(description = NGCommonEntityConstants.SERVICE_OVERRIDES_IDENTIFIER) @PathParam(
          "identifier") @ResourceIdentifier @NotNull String identifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NonNull String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier) {
    Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional =
        serviceOverridesServiceV2.get(accountId, orgIdentifier, projectIdentifier, identifier);
    if (serviceOverridesEntityOptional.isEmpty()) {
      throw new NotFoundException(
          String.format("ServiceOverrides entity with identifier [%s] in project [%s], org [%s] not found", identifier,
              projectIdentifier, orgIdentifier));
    }
    NGServiceOverridesEntity serviceOverridesEntity = serviceOverridesEntityOptional.get();

    IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        serviceOverridesEntity.getEnvironmentRef(), accountId, orgIdentifier, projectIdentifier);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
            envIdentifierRef.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, envIdentifierRef.getIdentifier()), ENVIRONMENT_VIEW_PERMISSION,
        String.format(
            "Unauthorized to view environment %s referred in serviceOverrideEntity", envIdentifierRef.getIdentifier()));

    return ResponseDTO.newResponse(
        serviceOverridesEntityOptional.map(entity -> ServiceOverridesMapperV2.toResponseDTO(entity, false))
            .orElse(null));
  }

  @POST
  @ApiOperation(value = "Create an ServiceOverride Entity", nickname = "createServiceOverrideV2")
  @Operation(operationId = "createServiceOverride", summary = "Create an ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created ServiceOverride")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the ServiceOverride to be created") @NonNull
      @Valid ServiceOverrideRequestDTOV2 requestDTOV2) {
    overrideValidatorService.validateRequestOrThrow(requestDTOV2, accountId);

    NGServiceOverridesEntity serviceOverride = ServiceOverridesMapperV2.toEntity(accountId, requestDTOV2);
    NGServiceOverridesEntity createdServiceOverride = serviceOverridesServiceV2.create(serviceOverride);
    return ResponseDTO.newResponse(ServiceOverridesMapperV2.toResponseDTO(createdServiceOverride, true));
  }

  @PUT
  @ApiOperation(value = "Update an ServiceOverride Entity", nickname = "updateServiceOverrideV2")
  @Operation(operationId = "updateServiceOverride", summary = "Update an ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated ServiceOverride")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the ServiceOverride to be updated")
      @Valid ServiceOverrideRequestDTOV2 requestDTOV2) {
    overrideValidatorService.validateRequestOrThrow(requestDTOV2, accountId);

    NGServiceOverridesEntity requestedServiceOverride = ServiceOverridesMapperV2.toEntity(accountId, requestDTOV2);
    NGServiceOverridesEntity updatedServiceOverride = serviceOverridesServiceV2.update(requestedServiceOverride);
    return ResponseDTO.newResponse(ServiceOverridesMapperV2.toResponseDTO(updatedServiceOverride, false));
  }

  @POST
  @Path("/upsert")
  @ApiOperation(value = "Upsert an ServiceOverride Entity", nickname = "upsertServiceOverrideV2")
  @Operation(operationId = "upsertServiceOverrideV2", summary = "Upsert an ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created/updated ServiceOverride")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  upsert(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the ServiceOverride to be updated")
      @Valid ServiceOverrideRequestDTOV2 requestDTOV2) {
    overrideValidatorService.validateRequestOrThrow(requestDTOV2, accountId);

    NGServiceOverridesEntity requestedServiceOverride = ServiceOverridesMapperV2.toEntity(accountId, requestDTOV2);
    Pair<NGServiceOverridesEntity, Boolean> upsertResult = serviceOverridesServiceV2.upsert(requestedServiceOverride);
    return ResponseDTO.newResponse(
        ServiceOverridesMapperV2.toResponseDTO(upsertResult.getLeft(), upsertResult.getRight()));
  }

  @DELETE
  @Path("/{identifier}")
  @ApiOperation(value = "Delete a Service Override entity", nickname = "deleteServiceOverrideV2")
  @Operation(operationId = "deleteServiceOverride", summary = "Delete a ServiceOverride entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Service Override is deleted")
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = NGCommonEntityConstants.SERVICE_OVERRIDES_IDENTIFIER) @PathParam(
             "identifier") @ResourceIdentifier @NotNull String identifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    Optional<NGServiceOverridesEntity> ngServiceOverridesEntityOptional =
        serviceOverridesServiceV2.get(accountId, orgIdentifier, projectIdentifier, identifier);
    if (ngServiceOverridesEntityOptional.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Service Override [%s], Project[%s], Organization [%s] does not exist", identifier,
              projectIdentifier, orgIdentifier));
    }
    NGServiceOverridesEntity ngServiceOverridesEntity = ngServiceOverridesEntityOptional.get();
    overrideValidatorService.validateEnvWithRBACOrThrow(
        accountId, orgIdentifier, projectIdentifier, ngServiceOverridesEntity.getEnvironmentRef());

    return ResponseDTO.newResponse(serviceOverridesServiceV2.delete(accountId, orgIdentifier, projectIdentifier,
        ngServiceOverridesEntity.getIdentifier(), ngServiceOverridesEntity));
  }

  @GET
  @Path("/list")
  @Hidden
  @ApiOperation(value = "Gets Service Override List", nickname = "getServiceOverrideListV2")
  @Operation(operationId = "getServiceOverrideListV2", summary = "Gets Service Override List",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Services for a Project")
      })
  public ResponseDTO<PageResponse<ServiceOverridesResponseDTOV2>>
  listServiceOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is service override type which is based on override source") @QueryParam(
          "type") ServiceOverridesType type) {
    Criteria criteria =
        ServiceOverrideCriteriaHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, type);
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.lastModifiedAt));
    Page<NGServiceOverridesEntity> serviceOverridesEntities = serviceOverridesServiceV2.list(criteria, pageRequest);

    return ResponseDTO.newResponse(getNGPageResponse(
        serviceOverridesEntities.map(entity -> ServiceOverridesMapperV2.toResponseDTO(entity, false))));
  }

  @POST
  @Path("/migrate")
  @Hidden
  @ApiOperation(value = "Migrate ServiceOverride to V2", nickname = "migrateServiceOverride")
  @Operation(operationId = "migrateServiceOverride", summary = "Migrate ServiceOverride to V2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  migrateServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, false, false);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  @POST
  @Path("/migrateScope")
  @Hidden
  @ApiOperation(value = "Migrate ServiceOverride to V2 at one scope", nickname = "migrateServiceOverrideScoped")
  @Operation(operationId = "migrateServiceOverrideScoped", summary = "Migrate ServiceOverride to V2 at one scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  migrateServiceOverrideAtScope(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, true, false);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  @POST
  @Path("/revertMigration")
  @Hidden
  @ApiOperation(value = "Revert ServiceOverride V2 Migration", nickname = "revertMigrationServiceOverride")
  @Operation(operationId = "revertMigrationServiceOverride", summary = "Revert ServiceOverride V2 Migration",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Revert Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  revertMigrationServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, false, true);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  @POST
  @Path("/revertMigrationScope")
  @Hidden
  @ApiOperation(
      value = "Revert ServiceOverride V2 Migration at one scope", nickname = "revertMigrationServiceOverrideScoped")
  @Operation(operationId = "revertMigrationServiceOverrideScoped",
      summary = "Revert ServiceOverride V2 Migration at one scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Revert Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  revertMigrationServiceOverrideAtScope(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                        @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, true, true);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }
}
