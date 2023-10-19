/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static software.wings.beans.Service.ServiceKeys;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
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
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.bean.yaml.ArtifactSourceConfig;
import io.harness.cdng.artifact.utils.ArtifactSourceTemplateHelper;
import io.harness.cdng.deploymentmetadata.DeploymentMetadataServiceHelper;
import io.harness.cdng.hooks.ServiceHookAction;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.kinds.KustomizeCommandFlagType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.artifact.ArtifactSourceYamlRequestDTO;
import io.harness.ng.core.beans.DocumentationConstants;
import io.harness.ng.core.beans.EntityWithGitInfo;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.beans.ServiceV2YamlMetadata;
import io.harness.ng.core.beans.ServicesV2YamlMetadataDTO;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInput;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInputV2;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.RepoListResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.utils.GitXUtils;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableSet;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/servicesV2")
@Path("/servicesV2")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Services", description = "This contains APIs related to Services")
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
public class ServiceResourceV2 {
  private final ServiceEntityService serviceEntityService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final AccessControlClient accessControlClient;
  private final ServiceEntityManagementService serviceEntityManagementService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject ArtifactSourceTemplateHelper artifactSourceTemplateHelper;
  private ServiceEntityYamlSchemaHelper serviceSchemaHelper;
  private ScopeAccessHelper scopeAccessHelper;
  @Inject private DeploymentMetadataServiceHelper deploymentMetadataServiceHelper;
  private ServiceRbacHelper serviceRbacHelper;
  private final NGFeatureFlagHelperService featureFlagService;
  public static final String SERVICE_PARAM_MESSAGE = "Service Identifier for the entity";
  public static final String SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "List of Service Identifiers for the entities, maximum size of list is 1000.";
  private static final int MAX_LIMIT = 1000;
  private static final Set<String> allowedServiceSpecs =
      ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES);

  @GET
  @Path("{serviceIdentifier}")
  @ApiOperation(value = "Gets a Service by identifier", nickname = "getServiceV2")
  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_view")
  @Operation(operationId = "getServiceV2", summary = "Gets a Service by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Service")
      })
  public ResponseDTO<ServiceResponse>
  get(@Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify whether Service is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "Specify true for fetching resolved service yaml", hidden = true) @QueryParam(
          "fetchResolvedYaml") @DefaultValue("false") boolean fetchResolvedYaml,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch) {
    Optional<ServiceEntity> serviceEntity = serviceEntityService.get(accountId, orgIdentifier, projectIdentifier,
        serviceIdentifier, deleted, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    if (serviceEntity.isPresent()) {
      if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity.get());
        serviceEntity.get().setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }
    } else {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }

    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_ARTIFACTORY_REPOSITORY_URL_MANDATORY)) {
      ServiceEntity service = serviceEntityService.updateArtifactoryRegistryUrlIfEmpty(
          serviceEntity.get(), accountId, orgIdentifier, projectIdentifier);
      Optional<ServiceEntity> serviceResponse = Optional.ofNullable(service);
      if (fetchResolvedYaml) {
        serviceEntity.get().setYaml(serviceEntityService.resolveArtifactSourceTemplateRefs(
            accountId, orgIdentifier, projectIdentifier, serviceEntity.get().getYaml()));
      }

      return ResponseDTO.newResponse(serviceResponse.map(ServiceElementMapper::toResponseWrapper).orElse(null));
    }
    if (fetchResolvedYaml) {
      serviceEntity.get().setYaml(serviceEntityService.resolveArtifactSourceTemplateRefs(
          accountId, orgIdentifier, projectIdentifier, serviceEntity.get().getYaml()));
    }
    return ResponseDTO.newResponse(serviceEntity.map(ServiceElementMapper::toResponseWrapper).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create a Service", nickname = "createServiceV2")
  @Operation(operationId = "createServiceV2", summary = "Create a Service",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Service")
      })
  public ResponseDTO<ServiceResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service to be created",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service create payload",
                         value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
          }) @Valid ServiceRequestDTO serviceRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());
    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, serviceEntity.getYaml());
    }
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getAccountId());
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);

    return ResponseDTO.newResponse(ServiceElementMapper.toResponseWrapper(createdService));
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Create Services", nickname = "createServicesV2")
  @Operation(operationId = "createServicesV2", summary = "Create Services",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Services")
      })
  public ResponseDTO<PageResponse<ServiceResponse>>
  createServices(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Services to be created, maximum 1000 services can be created.") @Valid
      @Size(max = MAX_LIMIT) List<ServiceRequestDTO> serviceRequestDTOs) {
    throwExceptionForNoRequestDTO(serviceRequestDTOs);
    for (ServiceRequestDTO serviceRequestDTO : serviceRequestDTOs) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
          Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    }
    serviceRequestDTOs.forEach(
        serviceRequestDTO -> serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml()));
    List<ServiceEntity> serviceEntities =
        serviceRequestDTOs.stream()
            .map(serviceRequestDTO -> ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO))
            .collect(Collectors.toList());

    for (int i = 0; i < serviceRequestDTOs.size(); i++) {
      if (isEmpty(serviceRequestDTOs.get(i).getYaml())) {
        serviceSchemaHelper.validateSchema(accountId, serviceEntities.get(i).getYaml());
      }
    }
    serviceEntities.forEach(serviceEntity
        -> orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
            serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getAccountId()));
    Page<ServiceEntity> createdServices = serviceEntityService.bulkCreate(accountId, serviceEntities);
    return ResponseDTO.newResponse(getNGPageResponse(createdServices.map(ServiceElementMapper::toResponseWrapper)));
  }

  @DELETE
  @Path("{serviceIdentifier}")
  @ApiOperation(value = "Delete a service by identifier", nickname = "deleteServiceV2")
  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_delete")
  @Operation(operationId = "deleteServiceV2", summary = "Delete a Service by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Service is deleted") })
  public ResponseDTO<Boolean>
  delete(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE) @DefaultValue(
          "false") boolean forceDelete) {
    return ResponseDTO.newResponse(serviceEntityManagementService.deleteService(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, ifMatch, forceDelete));
  }

  @PUT
  @ApiOperation(value = "Update a service by identifier", nickname = "updateServiceV2")
  @Operation(operationId = "updateServiceV2", summary = "Update a Service by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Service") })
  public ResponseDTO<ServiceResponse>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service update payload",
                         value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
          }) @Valid ServiceRequestDTO serviceRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated",
          hidden = true) @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, requestService.getYaml());
    }
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceEntity updatedService = serviceEntityService.update(requestService);
    return ResponseDTO.newResponse(ServiceElementMapper.toResponseWrapper(updatedService));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert a service by identifier", nickname = "upsertServiceV2")
  @Operation(operationId = "upsertServiceV2", summary = "Upsert a Service by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Service") })
  public ResponseDTO<ServiceResponse>
  upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service to be upserted", content = {
        @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service upsert payload",
                     value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
      }) @Valid ServiceRequestDTO serviceRequestDTO) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, requestService.getYaml());
    }
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestService.getOrgIdentifier(), requestService.getProjectIdentifier(), requestService.getAccountId());
    ServiceEntity upsertService = serviceEntityService.upsert(requestService, UpsertOptions.DEFAULT);
    return ResponseDTO.newResponse(ServiceElementMapper.toResponseWrapper(upsertService));
  }

  @GET
  @ApiOperation(value = "Gets Service list", nickname = "getServiceList")
  @Operation(operationId = "getServiceList", summary = "Gets Service list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Services for a Project")
      })
  public ResponseDTO<PageResponse<ServiceResponse>>
  listServices(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @QueryParam("type") ServiceDefinitionType type, @QueryParam("gitOpsEnabled") Boolean gitOpsEnabled,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @Parameter(description = "Specify true if all accessible Services are to be included") @QueryParam(
          "includeAllServicesAccessibleAtScope") @DefaultValue("false") boolean includeAllServicesAccessibleAtScope,
      @Parameter(description = "Specify true if services' version info need to be included", hidden = true) @QueryParam(
          "includeVersionInfo") @DefaultValue("false") boolean includeVersionInfo,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          "repoName") String repoName) {
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false,
        searchTerm, type, gitOpsEnabled, includeAllServicesAccessibleAtScope, repoName);
    Pageable pageRequest;
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }

    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<ServiceEntity> serviceEntities =
        getRBACFilteredServices(accountId, orgIdentifier, projectIdentifier, criteria, pageRequest);

    if (ServiceDefinitionType.CUSTOM_DEPLOYMENT == type && !isEmpty(deploymentTemplateIdentifier)
        && !isEmpty(versionLabel)) {
      serviceEntities = customDeploymentYamlHelper.getFilteredServiceEntities(
          page, size, sort, deploymentTemplateIdentifier, versionLabel, serviceEntities);
    }

    serviceEntities.forEach(serviceEntity -> {
      if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
        serviceEntity.setYaml(serviceEntity.fetchNonEmptyYaml());
      }
    });

    return ResponseDTO.newResponse(getNGPageResponse(
        serviceEntities.map(entity -> ServiceElementMapper.toResponseWrapper(entity, includeVersionInfo))));
  }

  @GET
  @Hidden
  @Path("/list/all-services")
  @ApiOperation(value = "Get all services list", nickname = "getAllServicesList")
  @Operation(operationId = "getAllServicesList",
      summary = "Get all services list across organizations and projects within account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of all Services across organizations and projects within account")
      },
      hidden = true)
  @InternalApi
  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_view")
  public ResponseDTO<PageResponse<ServiceResponse>>
  getAllServicesList(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGResourceFilterConstants.SEARCH_TERM) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(1000) int size,
      @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SORT) List<String> sort) {
    Criteria criteria = ServiceFilterHelper.createCriteriaForListingAllServices(
        accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, false);

    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt))
        : PageUtils.getPageRequest(page, size, sort);
    Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);
    return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(ServiceElementMapper::toResponseWrapper)));
  }

  @GET
  @Path("/list/scoped")
  @Hidden
  @ApiOperation(value = "Gets Service list filtered by service refs", nickname = "getServiceListFiltered")
  @Operation(operationId = "getServiceList", summary = "Gets Service list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Services filtered by scoped service refs")
      })
  public ResponseDTO<PageResponse<ServiceResponse>>
  getServicesFilteredByRefs(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                                NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "List of ServicesIds") @QueryParam(
          "serviceIdentifiers") List<String> serviceIdentifiers) {
    checkAccessForListingAtScope(accountId, orgIdentifier, projectIdentifier, serviceIdentifiers);

    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifiers, false, null, null, null, false, null);

    Pageable pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));

    Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);

    serviceEntities.forEach(serviceEntity -> {
      if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }
    });
    return ResponseDTO.newResponse(
        getNGPageResponse(serviceEntities.map(entity -> ServiceElementMapper.toResponseWrapper(entity, false))));
  }

  private void checkAccessForListingAtScope(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    if (isEmpty(serviceIdentifiers)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }

    boolean checkProjectLevelList = false;
    boolean checkOrgLevelList = false;
    boolean checkAccountLevelList = false;

    if (isNotEmpty(serviceIdentifiers)) {
      for (String serviceRef : serviceIdentifiers) {
        if (isNotEmpty(serviceRef) && !EngineExpressionEvaluator.hasExpressions(serviceRef)) {
          IdentifierRef serviceIdentifierRef =
              IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier);
          if (io.harness.encryption.Scope.PROJECT.equals(serviceIdentifierRef.getScope())) {
            checkProjectLevelList = true;
          } else if (io.harness.encryption.Scope.ORG.equals(serviceIdentifierRef.getScope())) {
            checkOrgLevelList = true;
          } else if (io.harness.encryption.Scope.ACCOUNT.equals(serviceIdentifierRef.getScope())) {
            checkAccountLevelList = true;
          }
        }
      }
    }

    // listing without scoped refs
    if (checkProjectLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }

    if (checkOrgLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, null),
          Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }

    if (checkAccountLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, null, null),
          Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }
  }

  @GET
  @Path("/list/access")
  @ApiOperation(value = "Gets Service Access list ", nickname = "getServiceAccessList")
  @Operation(operationId = "getServiceAccessList", summary = "Gets Service Access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Services for a Project that are accessible")
      })
  public ResponseDTO<List<ServiceResponse>>
  listAccessServices(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") @Size(
          max = MAX_LIMIT) List<String> serviceIdentifiers,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @QueryParam("type") ServiceDefinitionType type, @QueryParam("gitOpsEnabled") Boolean gitOpsEnabled,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @QueryParam("deploymentMetadataYaml") String deploymentMetaDataYaml,
      @Parameter(description = "Specify true if all accessible Services are to be included") @QueryParam(
          "includeAllServicesAccessibleAtScope") @DefaultValue("false") boolean includeAllServicesAccessibleAtScope) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        "Unauthorized to list services");

    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false,
        searchTerm, type, gitOpsEnabled, includeAllServicesAccessibleAtScope, null);
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }
    List<ServiceResponse> serviceList;
    if (type == ServiceDefinitionType.CUSTOM_DEPLOYMENT && !isEmpty(deploymentTemplateIdentifier)
        && !isEmpty(versionLabel)) {
      serviceList = serviceEntityService.listRunTimePermission(criteria)
                        .stream()
                        .filter(serviceEntity
                            -> customDeploymentYamlHelper.isDeploymentTemplateService(
                                deploymentTemplateIdentifier, versionLabel, serviceEntity))
                        .map(ServiceElementMapper::toAccessListResponseWrapper)
                        .collect(Collectors.toList());
    } else if (ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS.equals(type)) {
      List<ServiceEntity> serviceEntities = serviceEntityService.listRunTimePermission(criteria);
      serviceEntities =
          deploymentMetadataServiceHelper.filterOnDeploymentMetadata(serviceEntities, type, deploymentMetaDataYaml);
      serviceList =
          serviceEntities.stream().map(ServiceElementMapper::toAccessListResponseWrapper).collect(Collectors.toList());
    } else {
      serviceList = serviceEntityService.listRunTimePermission(criteria)
                        .stream()
                        .map(ServiceElementMapper::toAccessListResponseWrapper)
                        .collect(Collectors.toList());
    }
    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_SCOPE_INFRA_TO_SERVICES)) {
      Map<String, List<String>> envRefInfraRefsMapping = new HashMap<>();
      serviceList = filterByScopedInfrastructures(
          accountId, orgIdentifier, projectIdentifier, serviceList, envRefInfraRefsMapping);
    }
    List<PermissionCheckDTO> permissionCheckDTOS =
        serviceList.stream().map(CDNGRbacUtility::serviceResponseToPermissionCheckDTO).collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return ResponseDTO.newResponse(filterByPermissionAndId(accessControlList, serviceList));
  }

  @GET
  @Path("/dummy-serviceConfig-api")
  @ApiOperation(value = "This is dummy api to expose NGServiceConfig", nickname = "dummyNGServiceConfigApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<NGServiceConfig> getNGServiceConfig() {
    return ResponseDTO.newResponse(NGServiceConfig.builder().build());
  }

  @GET
  @Path("/dummy-artifactSummary-api")
  @ApiOperation(value = "This is dummy api to expose ArtifactSummary", nickname = "dummyArtifactSummaryApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<ArtifactSummary> getArtifactSummaries() {
    return ResponseDTO.newResponse(new ArtifactSummary() {
      @Override
      public String getType() {
        return null;
      }

      @Override
      public String getDisplayName() {
        return null;
      }
    });
  }

  @GET
  @Path("/runtimeInputs/{serviceIdentifier}")
  @ApiOperation(value = "This api returns runtime input YAML", nickname = "getRuntimeInputsServiceEntity")
  @Hidden
  public ResponseDTO<NGEntityTemplateResponseDTO> getServiceRuntimeInputs(
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);

    if (serviceEntity.isPresent()) {
      if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
        throw new InvalidRequestException("Service is not configured with a Service definition. Service Yaml is empty");
      }
      String serviceInputYaml = serviceEntityService.createServiceInputsYaml(
          serviceEntity.get().getYaml(), serviceEntity.get().getIdentifier());
      return ResponseDTO.newResponse(
          NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(serviceInputYaml).build());
    } else {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }
  }

  @POST
  @Path("/servicesYamlMetadata")
  @ApiOperation(
      value = "This api returns service YAML and runtime input YAML", nickname = "getServicesYamlAndRuntimeInputs")
  @Hidden
  public ResponseDTO<ServicesV2YamlMetadataDTO>
  getServicesYamlAndRuntimeInputs(@Parameter(description = SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
                                  @NotNull ServicesYamlMetadataApiInput servicesYamlMetadataApiInput,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    List<ServiceV2YamlMetadata> serviceV2YamlMetadataList = serviceEntityService.getServicesYamlMetadata(accountId,
        orgIdentifier, projectIdentifier, servicesYamlMetadataApiInput.getServiceIdentifiers(), new HashMap<>(), false);

    return ResponseDTO.newResponse(
        ServicesV2YamlMetadataDTO.builder().serviceV2YamlMetadataList(serviceV2YamlMetadataList).build());
  }

  @POST
  @Path("/v2/services-yaml-metadata")
  @ApiOperation(
      value = "This api returns service YAML and runtime input YAML", nickname = "getServicesYamlAndRuntimeInputsV2")
  @Hidden
  public ResponseDTO<ServicesV2YamlMetadataDTO>
  getServicesYamlAndRuntimeInputsV2(@Parameter(description = SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
                                    @NotNull ServicesYamlMetadataApiInputV2 servicesYamlMetadataApiInput,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info for the Base entity")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache") @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache) {
    // get service ref-> branch map
    Map<String, String> serviceRefBranchMap = getServiceBranchMap(
        accountId, orgIdentifier, projectIdentifier, servicesYamlMetadataApiInput.getEntityWithGitInfoList());

    // scoped service refs
    List<String> serviceRefs = new ArrayList<>(serviceRefBranchMap.keySet());

    List<ServiceV2YamlMetadata> servicesYamlMetadata =
        serviceEntityService.getServicesYamlMetadata(accountId, orgIdentifier, projectIdentifier, serviceRefs,
            serviceRefBranchMap, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache));

    return ResponseDTO.newResponse(
        ServicesV2YamlMetadataDTO.builder().serviceV2YamlMetadataList(servicesYamlMetadata).build());
  }

  private Map<String, String> getServiceBranchMap(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<EntityWithGitInfo> entityWithGitInfo) {
    Map<String, String> resultMap = new HashMap<>();

    if (isEmpty(entityWithGitInfo)) {
      return resultMap;
    }

    for (EntityWithGitInfo input : entityWithGitInfo) {
      String scopedRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
          accountIdentifier, orgIdentifier, projectIdentifier, input.getRef());
      resultMap.put(scopedRef, input.getBranch());
    }

    return resultMap;
  }

  @GET
  @Path("/artifactSourceInputs/{serviceIdentifier}")
  @ApiOperation(value = "This api returns artifact source identifiers and their runtime inputs YAML",
      nickname = "getArtifactSourceInputs")
  @Hidden
  public ResponseDTO<ArtifactSourcesResponseDTO>
  getArtifactSourceInputs(@Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
                              "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache) {
    Optional<ServiceEntity> serviceEntity = serviceEntityService.get(accountId, orgIdentifier, projectIdentifier,
        serviceIdentifier, false, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), false);

    if (serviceEntity.isPresent()) {
      if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
        throw new InvalidRequestException(
            format("Service %s is not configured with a Service definition. Service Yaml is empty", serviceIdentifier));
      }
      return ResponseDTO.newResponse(
          serviceEntityService.getArtifactSourceInputs(serviceEntity.get().getYaml(), serviceIdentifier));
    } else {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }
  }

  @GET
  @Path("/dummy-artifactSourceConfig-api")
  @ApiOperation(value = "This is dummy api to expose ArtifactSourceConfig", nickname = "dummyArtifactSourceConfigApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<ArtifactSourceConfig> getArtifactSourceConfig() {
    return ResponseDTO.newResponse(ArtifactSourceConfig.builder().build());
  }

  @POST
  @Path("/artifact-source-references")
  @ApiOperation(
      value = "Gets Artifact Source Template entity references", nickname = "getArtifactSourceTemplateEntityReferences")
  @Operation(operationId = "getArtifactSourceTemplateEntityReferences",
      summary = "Gets Artifact Source Template entity references",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns all entity references in the artifact source template.")
      })
  @Hidden
  public ResponseDTO<List<EntityDetailProtoDTO>>
  getEntityReferences(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true, description = "Artifact Source Yaml Request DTO containing entityYaml")
      @NotNull ArtifactSourceYamlRequestDTO artifactSourceYamlRequestDTO) {
    List<EntityDetailProtoDTO> entityReferences = artifactSourceTemplateHelper.getReferencesFromYaml(
        accountId, orgId, projectId, artifactSourceYamlRequestDTO.getEntityYaml());
    return ResponseDTO.newResponse(entityReferences);
  }

  @POST
  @Path("/mergeServiceInputs/{serviceIdentifier}")
  @ApiOperation(value = "This api merges old and new service inputs YAML", nickname = "mergeServiceInputs")
  @Hidden
  public ResponseDTO<ServiceInputsMergedResponseDto> mergeServiceInputs(
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      String oldServiceInputsYaml) {
    return ResponseDTO.newResponse(serviceEntityService.mergeServiceInputs(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, oldServiceInputsYaml));
  }

  @GET
  @Path("/k8s/command-flags")
  @ApiOperation(value = "Get Command flags for K8s", nickname = "k8sCmdFlags")
  @Operation(operationId = "k8sCmdFlags", summary = "Retrieving the list of Kubernetes Command Options",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Kubernetes Command Options")
      })
  public ResponseDTO<Set<K8sCommandFlagType>>
  getK8sCommandFlags(@QueryParam("serviceSpecType") @NotNull String serviceSpecType) {
    Set<K8sCommandFlagType> k8sCmdFlags = new HashSet<>();
    for (K8sCommandFlagType k8sCommandFlagType : K8sCommandFlagType.values()) {
      if (k8sCommandFlagType.getServiceSpecTypes().contains(serviceSpecType)) {
        k8sCmdFlags.add(k8sCommandFlagType);
      }
    }
    return ResponseDTO.newResponse(k8sCmdFlags);
  }

  @GET
  @Path("/hooks/actions")
  @ApiOperation(value = "Get Available Service Hook Actions", nickname = "hookActions")
  @Operation(operationId = "hookActions", summary = "Retrieving the list of actions available for service hooks",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of actions available for service hooks")
      })
  public ResponseDTO<Set<ServiceHookAction>>
  getServiceHookActions(@QueryParam("serviceSpecType") @NotNull String serviceSpecType) {
    if (allowedServiceSpecs.contains(serviceSpecType)) {
      return ResponseDTO.newResponse(Set.of(ServiceHookAction.values()));
    }
    throw new InvalidRequestException(
        format("Service with type: [%s] does not support service hooks", serviceSpecType));
  }

  @GET
  @Path("validate-template-inputs")
  @ApiOperation(value = "This validates inputs for templates like artifact sources for service yaml",
      nickname = "validateTemplateInputs")
  @Hidden
  public ResponseDTO<ValidateTemplateInputsResponseDTO>
  validateTemplateInputs(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String serviceIdentifier,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(
        serviceEntityService.validateTemplateInputs(accountId, orgId, projectId, serviceIdentifier, loadFromCache));
  }

  private List<ServiceResponse> filterByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<ServiceResponse> serviceList) {
    List<ServiceResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      ServiceResponse serviceResponse = serviceList.get(i);
      if (accessControlDTO.isPermitted()
          && serviceResponse.getService().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(serviceResponse);
      }
    }
    return filteredAccessControlDtoList;
  }

  private List<ServiceResponse> filterByScopedInfrastructures(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<ServiceResponse> serviceResponses,
      Map<String, List<String>> envRefInfraRefsMapping) {
    if (CollectionUtils.isEmpty(serviceResponses)) {
      return serviceResponses;
    }
    List<String> currentServiceRefs = serviceResponses.stream()
                                          .map(serviceResponse -> serviceResponse.getService().getIdentifier())
                                          .collect(toList());
    List<String> allowedServiceRefs = infrastructureEntityService.filterServicesByScopedInfrastructures(
        accountIdentifier, orgIdentifier, projectIdentifier, currentServiceRefs, envRefInfraRefsMapping);
    return serviceResponses.stream()
        .filter(serviceResponse -> allowedServiceRefs.contains(serviceResponse.getService().getIdentifier()))
        .collect(toList());
  }

  private void throwExceptionForNoRequestDTO(List<ServiceRequestDTO> dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(ServiceRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  @GET
  @Path("kustomize/command-flags")
  @ApiOperation(value = "Get Command flags for kustomize", nickname = "kustomizeCmdFlags")
  @Operation(operationId = "kustomizeCmdFlags", summary = "Retrieving the list of Kustomize Command Flags",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Kustomize Command Flags")
      })
  public ResponseDTO<Set<KustomizeCommandFlagType>>
  getKustomizeCommandFlags() {
    return ResponseDTO.newResponse(new HashSet<>(Arrays.asList(KustomizeCommandFlagType.values())));
  }
  boolean hasViewPermissionForAllServices(String accountId, String orgIdentifier, String projectIdentifier) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION);
  }
  private Page<ServiceEntity> getRBACFilteredServices(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Pageable pageRequest) {
    Page<ServiceEntity> serviceEntities;
    if (hasViewPermissionForAllServices(accountId, orgIdentifier, projectIdentifier)) {
      serviceEntities = serviceEntityService.list(criteria, pageRequest);

    } else {
      Page<ServiceEntity> serviceEntityPage = serviceEntityService.list(criteria, Pageable.unpaged());

      if (serviceEntityPage == null) {
        return Page.empty();
      }

      List<ServiceEntity> serviceList = serviceRbacHelper.getPermittedServiceList(serviceEntityPage.getContent());

      if (isEmpty(serviceList)) {
        return Page.empty();
      }
      populateInFilter(criteria, ServiceEntityKeys.identifier,
          serviceList.stream().map(ServiceEntity::getIdentifier).collect(toList()));

      serviceEntities = serviceEntityService.list(criteria, pageRequest);
    }
    return serviceEntities;
  }

  @GET
  @Path("/list-repo")
  @Hidden
  @ApiOperation(value = "Gets all repo list", nickname = "getRepositoryList")
  @Operation(operationId = "getRepositoryList", summary = "Gets the list of all repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the repositories of all Services")
      })

  public ResponseDTO<RepoListResponseDTO>
  listRepos(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify true if all accessible Services are to be included") @QueryParam(
          "includeAllServicesAccessibleAtScope") boolean includeAllServicesAccessibleAtScope) {
    RepoListResponseDTO repoListResponseDTO = serviceEntityService.getListOfRepos(
        accountIdentifier, orgIdentifier, projectIdentifier, includeAllServicesAccessibleAtScope);
    return ResponseDTO.newResponse(repoListResponseDTO);
  }
}
