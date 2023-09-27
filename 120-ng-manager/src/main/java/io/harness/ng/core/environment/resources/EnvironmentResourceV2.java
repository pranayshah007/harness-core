/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.environment.resources.EnvironmentResourceConstants.UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.checkDuplicateConfigFilesIdentifiersWithIn;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.checkDuplicateManifestIdentifiersWithIn;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.validateNoMoreThanOneHelmOverridePresent;
import static io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_DELETE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceEntityValidationHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.ng.core.environment.beans.EnvironmentInputsetYamlAndServiceOverridesMetadataInput;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.serviceoverrides.resources.ServiceOverridesResource;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverridesMapperV2;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;
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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/environmentsV2")
@Path("/environmentsV2")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Environments", description = "This contains APIs related to Environments")
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
public class EnvironmentResourceV2 {
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final ServiceOverrideService serviceOverrideService;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final ServiceEntityValidationHelper serviceEntityValidationHelper;
  private final EnvironmentFilterHelper environmentFilterHelper;
  private final EnvironmentGroupService environmentGroupService;
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final NGFeatureFlagHelperService featureFlagHelperService;
  private final ScopeAccessHelper scopeAccessHelper;
  private final EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;
  private EnvironmentRbacHelper environmentRbacHelper;
  private NGSettingsClient settingsClient;
  private ServiceOverridesResource serviceOverridesResource;
  private final ServiceOverridesServiceV2 serviceOverridesServiceV2;

  public static final String ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "Lists of Environment Identifiers and service identifiers for the entities";

  public static final String ENVIRONMENT_PARAM_MESSAGE = "Environment Identifier for the entity";

  private static final String TOO_MANY_HELM_OVERRIDES_PRESENT_ERROR_MESSAGE =
      "You cannot configure multiple Helm Repo Overrides for the service. Overrides provided: [%s]";

  @GET
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Gets a Environment by identifier", nickname = "getEnvironmentV2")
  @Operation(operationId = "getEnvironmentV2", summary = "Gets an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  get(@Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify whether Environment is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch) {
    Optional<Environment> environment =
        environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, deleted);
    if (environment.isPresent()) {
      if (EmptyPredicate.isEmpty(environment.get().getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment.get());
        environment.get().setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    } else {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }

    checkForAccessOrThrow(getEnvironmentAttributesMap(environment.get().getType().toString()),
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), environmentIdentifier,
        ENVIRONMENT_VIEW_PERMISSION);
    return ResponseDTO.newResponse(environment.map(EnvironmentMapper::toResponseWrapper).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create an Environment", nickname = "createEnvironmentV2")
  @Operation(operationId = "createEnvironmentV2", summary = "Create an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be created")
      @Valid EnvironmentRequestDTO environmentRequestDTO) throws IOException {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null, getEnvironmentAttributesMap(environmentRequestDTO.getType().toString())),
        ENVIRONMENT_CREATE_PERMISSION);
    environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());
    Environment environmentEntity = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    if (isEmpty(environmentRequestDTO.getYaml())) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentEntity.getYaml());
    }
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(environmentEntity.getOrgIdentifier(),
        environmentEntity.getProjectIdentifier(), environmentEntity.getAccountId());
    Environment createdEnvironment = environmentService.create(environmentEntity);

    if (isOverridesV2Enabled(
            accountId, environmentEntity.getOrgIdentifier(), environmentEntity.getProjectIdentifier())) {
      log.warn(format("Using environment v2 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
          environmentEntity.getProjectIdentifier(), environmentEntity.getOrgIdentifier(), accountId));
      updateEnvSpecificOverrideV2(accountId, environmentEntity);
    }

    return ResponseDTO.newResponse(EnvironmentMapper.toResponseWrapper(createdEnvironment));
  }

  private void updateEnvSpecificOverrideV2(String accountId, Environment environmentEntity) throws IOException {
    String envGlobalOverrideIdentifier =
        generateEnvGlobalOverrideIdentifier(accountId, environmentEntity.getOrgIdentifier(),
            environmentEntity.getProjectIdentifier(), environmentEntity.getIdentifier());
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environmentEntity);

    Optional<ServiceOverrideRequestDTOV2> requestDTOV2 =
        ServiceOverridesMapperV2.toRequestDTOV2(ngEnvironmentConfig, accountId);

    if (requestDTOV2.isPresent()) {
      Optional<NGServiceOverridesEntity> envGlobalOverridesEntity = serviceOverridesServiceV2.get(accountId,
          environmentEntity.getOrgIdentifier(), environmentEntity.getProjectIdentifier(), envGlobalOverrideIdentifier);

      if (envGlobalOverridesEntity.isPresent()) {
        serviceOverridesResource.update(accountId, requestDTOV2.get());
      } else {
        serviceOverridesResource.create(accountId, requestDTOV2.get());
      }
    }
  }

  private boolean checkFeatureFlagForOverridesV2(String accountId) {
    return featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0);
  }

  @DELETE
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Delete en environment by identifier", nickname = "deleteEnvironmentV2")
  @Operation(operationId = "deleteEnvironmentV2", summary = "Delete an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if the Environment is deleted")
      })
  public ResponseDTO<Boolean>
  delete(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE) @DefaultValue(
          "false") boolean forceDelete) {
    Optional<Environment> environmentOptional =
        environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, false);
    if (environmentOptional.isEmpty()) {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentOptional.get().getType() != null) {
      environmentAttributes.put("type", environmentOptional.get().getType().toString());
    }
    checkForAccessOrThrow(environmentAttributes, ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        environmentIdentifier, ENVIRONMENT_DELETE_PERMISSION);
    return ResponseDTO.newResponse(environmentService.delete(accountId, orgIdentifier, projectIdentifier,
        environmentIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null, forceDelete));
  }

  @PUT
  @ApiOperation(value = "Update an environment by identifier", nickname = "updateEnvironmentV2")
  @Operation(operationId = "updateEnvironmentV2", summary = "Update an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be updated")
      @Valid EnvironmentRequestDTO environmentRequestDTO) throws IOException {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO);
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentRequestDTO.getType() != null) {
      environmentAttributes = getEnvironmentAttributesMap(environmentRequestDTO.getType().toString());
    }
    checkForAccessOrThrow(environmentAttributes,
        ResourceScope.of(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier()),
        environmentRequestDTO.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    if (isEmpty(environmentRequestDTO.getYaml())) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, requestEnvironment.getYaml());
    }
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Environment updatedEnvironment = environmentService.update(requestEnvironment);

    if (isOverridesV2Enabled(
            accountId, requestEnvironment.getOrgIdentifier(), requestEnvironment.getProjectIdentifier())) {
      log.warn(format("Using environment v2 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
          requestEnvironment.getProjectIdentifier(), requestEnvironment.getOrgIdentifier(), accountId));
      updateEnvSpecificOverrideV2(accountId, requestEnvironment);
    }
    return ResponseDTO.newResponse(EnvironmentMapper.toResponseWrapper(updatedEnvironment));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert an environment by identifier", nickname = "upsertEnvironmentV2")
  @Operation(operationId = "upsertEnvironmentV2", summary = "Upsert an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be updated")
      @Valid EnvironmentRequestDTO environmentRequestDTO) throws IOException {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO);
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentRequestDTO.getType() != null) {
      environmentAttributes = getEnvironmentAttributesMap(environmentRequestDTO.getType().toString());
    }
    checkForAccessOrThrow(environmentAttributes,
        ResourceScope.of(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier()),
        environmentRequestDTO.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    if (isEmpty(environmentRequestDTO.getYaml())) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, requestEnvironment.getYaml());
    }
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(requestEnvironment.getOrgIdentifier(),
        requestEnvironment.getProjectIdentifier(), requestEnvironment.getAccountId());
    Environment upsertEnvironment = environmentService.upsert(requestEnvironment, UpsertOptions.DEFAULT);

    if (isOverridesV2Enabled(
            accountId, requestEnvironment.getOrgIdentifier(), requestEnvironment.getProjectIdentifier())) {
      log.warn(format("Using environment v2 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
          requestEnvironment.getProjectIdentifier(), requestEnvironment.getOrgIdentifier(), accountId));
      updateEnvSpecificOverrideV2(accountId, requestEnvironment);
    }
    return ResponseDTO.newResponse(EnvironmentMapper.toResponseWrapper(upsertEnvironment));
  }

  @GET
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentList")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      })
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironment(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, false, searchTerm);
    Pageable pageRequest;

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    return getEnvironmentsPageByCriteria(criteria, pageRequest);
  }

  @GET
  @Path("list/scoped")
  @Hidden
  @ApiOperation(value = "Gets environment list filtered by scoped env refs", nickname = "getEnvironmentListFiltered")
  @Operation(operationId = "getEnvironmentListFiltered", summary = "Gets Environment list filtered by scoped env refs",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Gets Environment list filtered by scoped env refs")
      })
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  getEnvironmentsFilteredByRefs(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                                    NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers) {
    checkAccessForListingAtScope(accountId, orgIdentifier, projectIdentifier, envIdentifiers);
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, envIdentifiers, false);
    Pageable pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));

    return getEnvironmentsPageByCriteria(criteria, pageRequest);
  }

  @NotNull
  private ResponseDTO<PageResponse<EnvironmentResponse>> getEnvironmentsPageByCriteria(
      Criteria criteria, Pageable pageRequest) {
    Page<Environment> environmentEntities = environmentService.list(criteria, pageRequest);
    environmentEntities.forEach(environment -> {
      if (EmptyPredicate.isEmpty(environment.getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    });
    return ResponseDTO.newResponse(getNGPageResponse(environmentEntities.map(EnvironmentMapper::toResponseWrapper)));
  }

  @GET
  @Path("/getActiveServiceInstancesForEnvironment")
  @ApiOperation(value = "Get list of instances grouped by service for particular environment",
      nickname = "getActiveServiceInstancesForEnvironment")
  @Hidden
  public ResponseDTO<InstanceGroupedByServiceList>
  getActiveServiceInstancesForEnvironment(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) String serviceIdentifier,
      @QueryParam(NGCommonEntityConstants.BUILD_KEY) String buildId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedByServiceList(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier, buildId));
  }

  @POST
  @Path("/listV2")
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentListV2")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project with filters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      },
      hidden = true)
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironmentsV2(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(
          description =
              "Specify true if all accessible environments are to be included. Returns environments at account/org/project level.")
      @QueryParam(NGResourceFilterConstants.INCLUDE_ALL_ACCESSIBLE_AT_SCOPE) @DefaultValue(
          "false") boolean includeAllAccessibleAtScope) {
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier,
        false, searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope);

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    final Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    final Page<Environment> environmentPage =
        getRBACFilteredEnvironments(accountId, orgIdentifier, projectIdentifier, criteria, pageRequest);

    environmentPage.forEach(environment -> {
      if (EmptyPredicate.isEmpty(environment.getYaml())) {
        environment.setYaml(environment.fetchNonEmptyYaml());
      }
    });
    return ResponseDTO.newResponse(getNGPageResponse(environmentPage.map(EnvironmentMapper::toResponseWrapper)));
  }

  @POST
  @Path("/listV2/access")
  @ApiOperation(value = "Gets environment access list", nickname = "getEnvironmentAccessListV2")
  @Operation(operationId = "getEnvironmentAccessListV2",
      summary = "Gets Environment Access list for a project with filters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project that are accessible")
      },
      hidden = true)
  public ResponseDTO<List<EnvironmentResponse>>
  listAccessEnvironmentsV2(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(
          description =
              "Specify true if all accessible environments are to be included. Returns environments at account/org/project level.")
      @QueryParam(NGResourceFilterConstants.INCLUDE_ALL_ACCESSIBLE_AT_SCOPE) @DefaultValue(
          "false") boolean includeAllAccessibleAtScope) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier,
        false, searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope);

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    List<EnvironmentResponse> environmentList = environmentService.listAccess(criteria)
                                                    .stream()
                                                    .map(EnvironmentMapper::toResponseWrapper)
                                                    .collect(Collectors.toList());

    List<PermissionCheckDTO> permissionCheckDTOS = environmentList.stream()
                                                       .map(CDNGRbacUtility::environmentResponseToPermissionCheckDTO)
                                                       .collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();

    return ResponseDTO.newResponse(filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList));
  }

  @GET
  @Path("/list/access")
  @ApiOperation(value = "Gets environment access list", nickname = "getEnvironmentAccessList")
  @Operation(operationId = "getEnvironmentAccessList", summary = "Gets Environment Access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Environments that are accessible")
      })
  public ResponseDTO<List<EnvironmentResponse>>
  listAccessEnvironment(@Parameter(description = NGCommonEntityConstants.PAGE) @QueryParam(
                            NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue(
          "100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Environment group identifier") @QueryParam(
          "envGroupIdentifier") String envGroupIdentifier,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);

    Criteria criteria;
    if (isEmpty(envIdentifiers) && isNotEmpty(envGroupIdentifier)) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity =
          environmentGroupService.get(accountId, orgIdentifier, projectIdentifier, envGroupIdentifier, false);
      IdentifierRef envGroupIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(envGroupIdentifier, accountId, orgIdentifier, projectIdentifier);
      environmentGroupEntity.ifPresentOrElse(
          groupEntity -> envIdentifiers.addAll(groupEntity.getEnvIdentifiers()), () -> {
            throw new InvalidRequestException(
                format("Could not find environment group with identifier: %s", envGroupIdentifier));
          });
      // fetch environments from the same scope as of env group
      criteria = environmentFilterHelper.createCriteriaForGetList(envGroupIdentifierRef.getAccountIdentifier(),
          envGroupIdentifierRef.getOrgIdentifier(), envGroupIdentifierRef.getProjectIdentifier(), false, searchTerm);
    } else {
      criteria = environmentFilterHelper.createCriteriaForGetList(
          accountId, orgIdentifier, projectIdentifier, false, searchTerm);
    }

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }

    List<EnvironmentResponse> environmentList = environmentService.listAccess(criteria)
                                                    .stream()
                                                    .map(EnvironmentMapper::toResponseWrapper)
                                                    .collect(Collectors.toList());

    List<PermissionCheckDTO> permissionCheckDTOS = environmentList.stream()
                                                       .map(CDNGRbacUtility::environmentResponseToPermissionCheckDTO)
                                                       .collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return ResponseDTO.newResponse(filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList));
  }

  @GET
  @Path("/dummy-env-api")
  @ApiOperation(value = "This is dummy api to expose NGEnvironmentConfig", nickname = "dummyNGEnvironmentConfigApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<NGEnvironmentConfig> getNGEnvironmentConfig() {
    return ResponseDTO.newResponse(NGEnvironmentConfig.builder().build());
  }

  @POST
  @Path("/mergeEnvironmentInputs/{environmentIdentifier}")
  @ApiOperation(value = "This api merges old and new environment inputs YAML", nickname = "mergeEnvironmentInputs")
  @Hidden
  public ResponseDTO<EnvironmentInputsMergedResponseDto> mergeEnvironmentInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      String oldEnvironmentInputsYaml) {
    return ResponseDTO.newResponse(environmentService.mergeEnvironmentInputs(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, oldEnvironmentInputsYaml));
  }

  @POST
  @Path("/serviceOverrides")
  @ApiOperation(value = "upsert a Service Override for an Environment", nickname = "upsertServiceOverride")
  @Operation(operationId = "upsertServiceOverride", summary = "upsert a Service Override for an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Upsert ( Create/Update )  a Service Override in an Environment.")
      })
  public ResponseDTO<io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO>
  upsertServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Service Override to be upserted")
      @Valid ServiceOverrideRequestDTO serviceOverrideRequestDTO) throws IOException {
    throwExceptionForInvalidRequestDTO(serviceOverrideRequestDTO);
    validateServiceOverrideScope(serviceOverrideRequestDTO);

    NGServiceOverridesEntity overridesEntity =
        ServiceOverridesMapper.toServiceOverridesEntity(accountId, serviceOverrideRequestDTO);

    boolean overridesV2Enabled = isOverridesV2Enabled(
        accountId, serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier());
    if (overridesV2Enabled) {
      log.warn(format(
          "Using service override v1 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
          serviceOverrideRequestDTO.getProjectIdentifier(), serviceOverrideRequestDTO.getOrgIdentifier(), accountId));

      ServiceOverridesResponseDTOV2 responseDTOV2 = upsertByOverrideV2Resource(accountId, overridesEntity);
      return ResponseDTO.newResponse(
          ServiceOverridesMapperV2.toResponseDTOV1(responseDTOV2, overridesEntity.getYaml()));
    }

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        overridesEntity.getOrgIdentifier(), overridesEntity.getProjectIdentifier(), overridesEntity.getAccountId());
    environmentValidationHelper.checkThatEnvExists(overridesEntity.getAccountId(), overridesEntity.getOrgIdentifier(),
        overridesEntity.getProjectIdentifier(), overridesEntity.getEnvironmentRef());
    serviceEntityValidationHelper.checkThatServiceExists(overridesEntity.getAccountId(),
        overridesEntity.getOrgIdentifier(), overridesEntity.getProjectIdentifier(), overridesEntity.getServiceRef());
    checkForServiceOverrideUpdateAccess(accountId, overridesEntity.getOrgIdentifier(),
        overridesEntity.getProjectIdentifier(), overridesEntity.getEnvironmentRef(), overridesEntity.getServiceRef());
    validateServiceOverrides(overridesEntity);

    NGServiceOverridesEntity createdServiceOverride = serviceOverrideService.upsert(overridesEntity);
    return ResponseDTO.newResponse(ServiceOverridesMapper.toResponseWrapper(createdServiceOverride, false));
  }

  private ServiceOverridesResponseDTOV2 upsertByOverrideV2Resource(
      String accountId, NGServiceOverridesEntity overridesEntity) throws IOException {
    ServiceOverrideRequestDTOV2 requestV2 = ServiceOverridesMapperV2.toRequestV2(overridesEntity);
    // Assumption
    // 1: Type and Yaml field will not be null/empty as from previous migration -
    // AddServiceOverrideV2RelatedFieldsMigration
    // 2: Only one entity either v1 or v2 will exist for given criteria
    Optional<NGServiceOverridesEntity> overrideEntityInDB = serviceOverrideService.getForV1AndV2(accountId,
        overridesEntity.getOrgIdentifier(), overridesEntity.getProjectIdentifier(), overridesEntity.getEnvironmentRef(),
        overridesEntity.getServiceRef());
    ResponseDTO<ServiceOverridesResponseDTOV2> apiResponseV2 = null;
    if (overrideEntityInDB.isPresent()) {
      apiResponseV2 = serviceOverridesResource.update(accountId, requestV2);
    } else {
      apiResponseV2 = serviceOverridesResource.create(accountId, requestV2);
    }
    return apiResponseV2.getData();
  }

  @POST
  @Path("/environmentInputYamlAndServiceOverridesMetadata")
  @ApiOperation(value = "This api returns environments runtime input YAML and serviceOverrides Yaml",
      nickname = "getEnvironmentsInputYamlAndServiceOverrides")
  @Hidden
  public ResponseDTO<EnvironmentInputSetYamlAndServiceOverridesMetadataDTO>
  getEnvironmentsInputYamlAndServiceOverrides(
      @Parameter(description = ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
      @NotNull EnvironmentInputsetYamlAndServiceOverridesMetadataInput environmentYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    List<String> envIdentifiers = new ArrayList<>();
    if (isNotEmpty(environmentYamlMetadata.getEnvIdentifiers())) {
      envIdentifiers.addAll(environmentYamlMetadata.getEnvIdentifiers());
    }
    if (isNotEmpty(environmentYamlMetadata.getEnvGroupIdentifier())
        && !EngineExpressionEvaluator.hasExpressions(environmentYamlMetadata.getEnvGroupIdentifier())) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity = environmentGroupService.get(
          accountId, orgIdentifier, projectIdentifier, environmentYamlMetadata.getEnvGroupIdentifier(), false);
      environmentGroupEntity.ifPresent(groupEntity -> envIdentifiers.addAll(groupEntity.getEnvIdentifiers()));
    }
    boolean isServiceOverrideV2Enabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0);
    EnvironmentInputSetYamlAndServiceOverridesMetadataDTO environmentInputsetYamlandServiceOverridesMetadataDTO =
        environmentService.getEnvironmentsInputYamlAndServiceOverridesMetadata(accountId, orgIdentifier,
            projectIdentifier, envIdentifiers, environmentYamlMetadata.getServiceIdentifiers(),
            isServiceOverrideV2Enabled);

    return ResponseDTO.newResponse(environmentInputsetYamlandServiceOverridesMetadataDTO);
  }

  private void validateServiceOverrides(NGServiceOverridesEntity serviceOverridesEntity) {
    final NGServiceOverrideConfig serviceOverrideConfig = toNGServiceOverrideConfig(serviceOverridesEntity);
    if (serviceOverrideConfig.getServiceOverrideInfoConfig() != null) {
      final NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          serviceOverrideConfig.getServiceOverrideInfoConfig();

      if (isEmpty(serviceOverrideInfoConfig.getManifests()) && isEmpty(serviceOverrideInfoConfig.getConfigFiles())
          && isEmpty(serviceOverrideInfoConfig.getVariables())
          && serviceOverrideInfoConfig.getApplicationSettings() == null
          && serviceOverrideInfoConfig.getConnectionStrings() == null) {
        final Optional<NGServiceOverridesEntity> optionalNGServiceOverrides =
            serviceOverrideService.get(serviceOverridesEntity.getAccountId(), serviceOverridesEntity.getOrgIdentifier(),
                serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef(),
                serviceOverridesEntity.getServiceRef());
        if (optionalNGServiceOverrides.isEmpty()) {
          throw new InvalidRequestException("No overrides found in request");
        }
      }

      checkDuplicateManifestIdentifiersWithIn(serviceOverrideInfoConfig.getManifests());
      validateNoMoreThanOneHelmOverridePresent(
          serviceOverrideInfoConfig.getManifests(), TOO_MANY_HELM_OVERRIDES_PRESENT_ERROR_MESSAGE);
      checkDuplicateConfigFilesIdentifiersWithIn(serviceOverrideInfoConfig.getConfigFiles());
    }
  }

  @DELETE
  @Path("/serviceOverrides")
  @ApiOperation(value = "Delete a Service Override entity", nickname = "deleteServiceOverride")
  @Operation(operationId = "deleteServiceOverride", summary = "Delete a ServiceOverride entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Service Override is deleted")
      })
  public ResponseDTO<Boolean>
  deleteServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    serviceEntityValidationHelper.checkThatServiceExists(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    // check access for service and env
    checkForServiceOverrideUpdateAccess(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);

    boolean overridesV2Enabled = isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);

    if (overridesV2Enabled) {
      log.warn(
          format("Using service override v1 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
              projectIdentifier, orgIdentifier, accountId));
    }

    return overridesV2Enabled
        ? serviceOverridesResource.delete(generateServiceOverrideIdentifier(environmentIdentifier, serviceIdentifier),
            accountId, orgIdentifier, projectIdentifier)
        : ResponseDTO.newResponse(serviceOverrideService.delete(
            accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier));
  }

  @GET
  @Path("/dummy-api-for-exposing-objects")
  @ApiOperation(value = "This is dummy api to expose objects to swagger", nickname = "dummyNGServiceOverrideConfig")
  @Hidden
  // do not delete this.
  public ResponseDTO<EnvSwaggerObjectWrapper> exposeSwaggerObjects() {
    return ResponseDTO.newResponse(EnvSwaggerObjectWrapper.builder().build());
  }

  @GET
  @Path("/serviceOverrides")
  @ApiOperation(value = "Gets Service Overrides list ", nickname = "getServiceOverridesList")
  @Operation(operationId = "getServiceOverridesList", summary = "Gets Service Overrides list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Service Overrides for an Environment."
                + "serviceIdentifier, if passed, can be used to get the overrides for that particular Service in the Environment")
      })
  public ResponseDTO<PageResponse<ServiceOverrideResponseDTO>>
  listServiceOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
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
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier @NotNull String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    if (isNotEmpty(serviceIdentifier)) {
      serviceEntityValidationHelper.checkThatServiceExists(
          accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_VIEW_PERMISSION,
        "Unauthorized to view environment");

    Criteria criteria = environmentFilterHelper.createCriteriaForGetServiceOverrides(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    Pageable pageRequest;

    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<NGServiceOverridesEntity> serviceOverridesEntities = serviceOverrideService.list(criteria, pageRequest);
    boolean overridesV2Enabled = isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);
    if (overridesV2Enabled) {
      log.warn(
          format("Using service override v1 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
              projectIdentifier, orgIdentifier, accountId));
    }
    return ResponseDTO.newResponse(getNGPageResponse(serviceOverridesEntities.map(serviceOverridesEntity
        -> ServiceOverridesMapper.toResponseWrapper(serviceOverridesEntity, overridesV2Enabled))));
  }

  @GET
  @Path("/runtimeInputs")
  @ApiOperation(value = "This api returns Environment inputs YAML", nickname = "getEnvironmentInputs")
  @Hidden
  public ResponseDTO<NGEntityTemplateResponseDTO> getEnvironmentInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    String environmentInputsYaml = environmentService.createEnvironmentInputsYaml(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(environmentInputsYaml).build());
  }

  @GET
  @Path("/serviceOverrides/runtimeInputs")
  @ApiOperation(value = "This api returns Service Override inputs YAML", nickname = "getServiceOverrideInputs")
  @Hidden
  public ResponseDTO<NGEntityTemplateResponseDTO> getServiceOverrideInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier) {
    String serviceOverrideInputsYaml = serviceOverrideService.createServiceOverrideInputsYaml(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(serviceOverrideInputsYaml).build());
  }

  @GET
  @Hidden
  @Path("/attributes")
  @ApiOperation(hidden = true, value = "Get Environments Attributes", nickname = "getEnvironmentsAttributes")
  @InternalApi
  public ResponseDTO<List<Map<String, String>>> getEnvironmentsAttributes(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("envIdentifiers") List<String> envIdentifiers) {
    return ResponseDTO.newResponse(
        environmentService.getAttributes(accountId, orgIdentifier, projectIdentifier, envIdentifiers));
  }

  private void checkAccessForListingAtScope(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers) {
    if (isEmpty(envIdentifiers)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
      return;
    }

    boolean checkProjectLevelList = false;
    boolean checkOrgLevelList = false;
    boolean checkAccountLevelList = false;

    if (isNotEmpty(envIdentifiers)) {
      for (String envRef : envIdentifiers) {
        if (isNotEmpty(envRef) && !EngineExpressionEvaluator.hasExpressions(envRef)) {
          IdentifierRef envIdentifierRef =
              IdentifierRefHelper.getIdentifierRef(envRef, accountId, orgIdentifier, projectIdentifier);
          if (io.harness.encryption.Scope.PROJECT.equals(envIdentifierRef.getScope())) {
            checkProjectLevelList = true;
          } else if (io.harness.encryption.Scope.ORG.equals(envIdentifierRef.getScope())) {
            checkOrgLevelList = true;
          } else if (io.harness.encryption.Scope.ACCOUNT.equals(envIdentifierRef.getScope())) {
            checkAccountLevelList = true;
          }
        }
      }
    }

    // listing without scoped refs
    if (checkProjectLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }

    if (checkOrgLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, null),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }

    if (checkAccountLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, null, null), Resource.of(ENVIRONMENT, null),
          ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }
  }

  private void checkForServiceOverrideUpdateAccess(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    final List<PermissionCheckDTO> permissionCheckDTOList = new ArrayList<>();
    String[] envRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                     .resourceIdentifier(environmentRef)
                                     .resourceType(ENVIRONMENT)
                                     .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                     .build());
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(environmentRef, accountId, orgIdentifier, projectIdentifier);
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                     .resourceIdentifier(envIdentifierRef.getIdentifier())
                                     .resourceType(ENVIRONMENT)
                                     .resourceScope(ResourceScope.of(envIdentifierRef.getAccountIdentifier(),
                                         envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier()))
                                     .build());
    }
    String[] serviceRefSplit = StringUtils.split(serviceRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (serviceRefSplit == null || serviceRefSplit.length == 1) {
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .permission(SERVICE_UPDATE_PERMISSION)
                                     .resourceIdentifier(serviceRef)
                                     .resourceType(SERVICE)
                                     .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                     .build());
    } else {
      IdentifierRef serviceIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier);
      permissionCheckDTOList.add(
          PermissionCheckDTO.builder()
              .permission(SERVICE_UPDATE_PERMISSION)
              .resourceIdentifier(serviceIdentifierRef.getIdentifier())
              .resourceType(SERVICE)
              .resourceScope(ResourceScope.of(serviceIdentifierRef.getAccountIdentifier(),
                  serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier()))
              .build());
    }

    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccess(permissionCheckDTOList);
    accessCheckResponse.getAccessControlList().forEach(accessControlDTO -> {
      if (!accessControlDTO.isPermitted()) {
        String errorMessage;
        errorMessage = format("Missing permission %s on %s", accessControlDTO.getPermission(),
            accessControlDTO.getResourceType().toLowerCase());
        if (!StringUtils.isEmpty(accessControlDTO.getResourceIdentifier())) {
          errorMessage = errorMessage.concat(format(" with identifier %s", accessControlDTO.getResourceIdentifier()));
        }
        throw new InvalidRequestException(errorMessage, WingsException.USER);
      }
    });
  }

  private List<EnvironmentResponse> filterEnvironmentResponseByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<EnvironmentResponse> environmentList) {
    List<EnvironmentResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      EnvironmentResponse environmentResponse = environmentList.get(i);
      if (accessControlDTO.isPermitted()
          && environmentResponse.getEnvironment().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(environmentResponse);
      }
    }
    return filteredAccessControlDtoList;
  }

  private void throwExceptionForNoRequestDTO(EnvironmentRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void throwExceptionForInvalidRequestDTO(ServiceOverrideRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException("No request body for Service overrides");
    }
    if (isEmpty(dto.getServiceIdentifier())) {
      throw new InvalidRequestException("No service identifier for Service Overrides request");
    }

    if (isBlank(dto.getYaml())) {
      throw new InvalidRequestException("No yaml is provided in Service Overrides request");
    }
  }

  private void validateEnvironmentScope(EnvironmentRequestDTO requestDTO) {
    try {
      if (isNotEmpty(requestDTO.getProjectIdentifier())) {
        Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
            "org identifier must be specified when project identifier is specified. Environments can be created at Project/Org/Account scope");
      }
    } catch (Exception ex) {
      log.error("failed to validate environment scope", ex);

      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private void validateServiceOverrideScope(ServiceOverrideRequestDTO requestDTO) {
    try {
      if (isNotEmpty(requestDTO.getProjectIdentifier())) {
        Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
            "org identifier must be specified when project identifier is specified. Service Overrides can be created at Project/Org/Account scope");
      }
    } catch (Exception ex) {
      log.error("failed to validate service override scope", ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }
  private void checkForAccessOrThrow(
      Map<String, String> environmentAttributes, ResourceScope resourceScope, String identifier, String permission) {
    List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(permission)
                             .resourceIdentifier(identifier)
                             .resourceScope(resourceScope)
                             .resourceType(ENVIRONMENT)
                             .build());
    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(permission)
                             .resourceAttributes(environmentAttributes)
                             .resourceScope(resourceScope)
                             .resourceType(ENVIRONMENT)
                             .build());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);
    List<AccessControlDTO> accessControlDTOList = accessCheckResponse.getAccessControlList();

    final boolean isActionAllowed =
        CollectionUtils.emptyIfNull(accessControlDTOList).stream().anyMatch(AccessControlDTO::isPermitted);
    if (!isActionAllowed) {
      throw new NGAccessDeniedException(
          format("Missing permission %s on %s with identifier %s", permission, ENVIRONMENT, identifier), USER,
          permissionChecks);
    }
  }
  private Map<String, String> getEnvironmentAttributesMap(String environmentType) {
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", environmentType);
    return environmentAttributes;
  }

  private boolean hasViewPermissionForAllEnvironments(
      String accountId, String orgIdentifier, String projectIdentifier) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION);
  }
  private Page<Environment> getRBACFilteredEnvironments(
      String accountId, String orgId, String projectId, Criteria criteria, Pageable pageRequest) {
    if (!hasViewPermissionForAllEnvironments(accountId, orgId, projectId)) {
      Page<Environment> environments = environmentService.list(criteria, Pageable.unpaged());
      if (environments == null || EmptyPredicate.isEmpty(environments)) {
        return Page.empty();
      }
      final List<Environment> environmentList =
          environmentRbacHelper.getPermittedEnvironmentsList(environments.getContent());
      if (isEmpty(environmentList)) {
        return Page.empty();
      }
      populateInFilter(criteria, EnvironmentKeys.identifier,
          environmentList.stream().map(Environment::getIdentifier).collect(toList()));
    }
    return environmentService.list(criteria, pageRequest);
  }

  private boolean isOverridesV2Enabled(String accountId, String orgIdentifier, String projectIdentifier) {
    return featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0)
        && parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER,
                                accountId, orgIdentifier, projectIdentifier))
                            .getValue());
  }

  private String generateServiceOverrideIdentifier(String envRef, String serviceRef) {
    return String.join("_", envRef, serviceRef).replace(".", "_");
  }

  private String generateEnvGlobalOverrideIdentifier(
      String accountId, String orgId, String projectId, String envIdentifier) {
    String envQualifiedRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projectId, envIdentifier);
    return envQualifiedRef.replace(".", "_");
  }
}
