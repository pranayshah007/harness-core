/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.artifact.ArtifactUtilities.getArtifactoryRegistryUrl;
import static io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceServiceImpl.getConnector;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static software.wings.beans.Service.ServiceKeys;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isBlank;
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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.bean.yaml.ArtifactSourceConfig;
import io.harness.cdng.artifact.utils.ArtifactSourceTemplateHelper;
import io.harness.cdng.hooks.ServiceHookAction;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.kinds.KustomizeCommandFlagType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.artifact.ArtifactSourceYamlRequestDTO;
import io.harness.ng.core.beans.DocumentationConstants;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.beans.ServiceV2YamlMetadata;
import io.harness.ng.core.beans.ServicesV2YamlMetadataDTO;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInput;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
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
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

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
  private final AccessControlClient accessControlClient;
  private final ServiceEntityManagementService serviceEntityManagementService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject ArtifactSourceTemplateHelper artifactSourceTemplateHelper;
  private ServiceEntityYamlSchemaHelper serviceSchemaHelper;
  private ScopeAccessHelper scopeAccessHelper;

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
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, deleted);
    String version = "0";
    if (serviceEntity.isPresent()) {
      version = serviceEntity.get().getVersion().toString();
      if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity.get());
        serviceEntity.get().setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }
    } else {
      throw new NotFoundException(format("Service with identifier [%s] in project [%s], org [%s] not found",
          serviceIdentifier, projectIdentifier, orgIdentifier));
    }

    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_ARTIFACTORY_REPOSITORY_URL_MANDATORY)) {
      ServiceEntity service =
          updateArtifactoryRegistryUrlIfEmpty(serviceEntity.get(), accountId, orgIdentifier, projectIdentifier);
      Optional<ServiceEntity> serviceResponse = Optional.ofNullable(service);
      return ResponseDTO.newResponse(
          version, serviceResponse.map(ServiceElementMapper::toResponseWrapper).orElse(null));
    }

    return ResponseDTO.newResponse(version, serviceEntity.map(ServiceElementMapper::toResponseWrapper).orElse(null));
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
      @RequestBody(required = true, description = "Details of the Service to be created", content = {
        @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service create payload",
                     value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
      }) @Valid ServiceRequestDTO serviceRequestDTO) {
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

    return ResponseDTO.newResponse(
        createdService.getVersion().toString(), ServiceElementMapper.toResponseWrapper(createdService));
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
      @RequestBody(required = true, description = "Details of the Service to be updated", content = {
        @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service update payload",
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
    ServiceEntity updatedService = serviceEntityService.update(requestService);
    return ResponseDTO.newResponse(
        updatedService.getVersion().toString(), ServiceElementMapper.toResponseWrapper(updatedService));
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
    return ResponseDTO.newResponse(
        upsertService.getVersion().toString(), ServiceElementMapper.toResponseWrapper(upsertService));
  }

  @GET
  @ApiOperation(value = "Gets Service list ", nickname = "getServiceList")
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
          "includeVersionInfo") @DefaultValue("false") boolean includeVersionInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");

    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false,
        searchTerm, type, gitOpsEnabled, includeAllServicesAccessibleAtScope);
    Pageable pageRequest;
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }

    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);
    if (ServiceDefinitionType.CUSTOM_DEPLOYMENT == type && !isEmpty(deploymentTemplateIdentifier)
        && !isEmpty(versionLabel)) {
      serviceEntities = customDeploymentYamlHelper.getFilteredServiceEntities(
          page, size, sort, deploymentTemplateIdentifier, versionLabel, serviceEntities);
    }
    serviceEntities.forEach(serviceEntity -> {
      if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
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
        accountId, orgIdentifier, projectIdentifier, serviceIdentifiers, false, null, null, null, false);

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
      @QueryParam("versionLabel") String versionLabel) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        "Unauthorized to list services");

    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, false, searchTerm, type, gitOpsEnabled, false);
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
    } else {
      serviceList = serviceEntityService.listRunTimePermission(criteria)
                        .stream()
                        .map(ServiceElementMapper::toAccessListResponseWrapper)
                        .collect(Collectors.toList());
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
      // todo: better error message here
      throw new NotFoundException(format("Service with identifier [%s] in project [%s], org [%s] not found",
          serviceIdentifier, projectIdentifier, orgIdentifier));
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
    List<ServiceEntity> serviceEntities = serviceEntityService.getServices(
        accountId, orgIdentifier, projectIdentifier, servicesYamlMetadataApiInput.getServiceIdentifiers());

    List<ServiceV2YamlMetadata> serviceV2YamlMetadataList = new ArrayList<>();
    serviceEntities.forEach(serviceEntity -> serviceV2YamlMetadataList.add(createServiceV2YamlMetadata(serviceEntity)));

    return ResponseDTO.newResponse(
        ServicesV2YamlMetadataDTO.builder().serviceV2YamlMetadataList(serviceV2YamlMetadataList).build());
  }

  private ServiceV2YamlMetadata createServiceV2YamlMetadata(ServiceEntity serviceEntity) {
    if (featureFlagService.isEnabled(
            serviceEntity.getAccountId(), FeatureName.CDS_ARTIFACTORY_REPOSITORY_URL_MANDATORY)) {
      serviceEntity = updateArtifactoryRegistryUrlIfEmpty(serviceEntity, serviceEntity.getAccountId(),
          serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier());
    }

    if (isBlank(serviceEntity.getYaml())) {
      log.info("Service with identifier {} is not configured with a Service definition. Service Yaml is empty",
          serviceEntity.getIdentifier());
      return ServiceV2YamlMetadata.builder()
          .serviceIdentifier(serviceEntity.getIdentifier())
          .serviceYaml("")
          .inputSetTemplateYaml("")
          .projectIdentifier(serviceEntity.getProjectIdentifier())
          .orgIdentifier(serviceEntity.getOrgIdentifier())
          .build();
    }

    final String serviceInputSetYaml =
        serviceEntityService.createServiceInputsYaml(serviceEntity.getYaml(), serviceEntity.getIdentifier());
    return ServiceV2YamlMetadata.builder()
        .serviceIdentifier(serviceEntity.getIdentifier())
        .serviceYaml(serviceEntity.getYaml())
        .inputSetTemplateYaml(serviceInputSetYaml)
        .orgIdentifier(serviceEntity.getOrgIdentifier())
        .projectIdentifier(serviceEntity.getProjectIdentifier())
        .build();
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
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);

    if (serviceEntity.isPresent()) {
      if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
        throw new InvalidRequestException(
            format("Service %s is not configured with a Service definition. Service Yaml is empty", serviceIdentifier));
      }
      return ResponseDTO.newResponse(
          serviceEntityService.getArtifactSourceInputs(serviceEntity.get().getYaml(), serviceIdentifier));
    } else {
      throw new NotFoundException(format("Service with identifier [%s] in project [%s], org [%s] not found",
          serviceIdentifier, projectIdentifier, orgIdentifier));
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

  @Hidden
  public ServiceEntity updateArtifactoryRegistryUrlIfEmpty(
      ServiceEntity serviceEntity, String accountId, String orgIdentifier, String projectIdentifier) {
    if (serviceEntity == null) {
      return null;
    }

    String repositoryUrlField = "repositoryUrl";

    String serviceYaml = serviceEntity.getYaml();

    YamlNode node = validateAndGetYamlNode(serviceYaml);

    JsonNode artifactSpecNode = null;
    if (node != null) {
      JsonNode serviceNode = node.getCurrJsonNode().get("service");

      if (serviceNode != null) {
        JsonNode serviceDefinitionNode = serviceNode.get("serviceDefinition");

        if (serviceDefinitionNode != null) {
          JsonNode specNode = serviceDefinitionNode.get("spec");

          if (specNode != null) {
            JsonNode artifactsNode = specNode.get("artifacts");

            if (artifactsNode != null) {
              JsonNode primaryNode = artifactsNode.get("primary");

              if (primaryNode != null) {
                artifactSpecNode = primaryNode.get("sources");
              }
            }
          }
        }
      }
    }

    if (artifactSpecNode == null) {
      return serviceEntity;
    }

    Map<String, Object> yamlResMap = getResMap(node, null);
    LinkedHashMap<String, Object> serviceResMap = (LinkedHashMap<String, Object>) yamlResMap.get("service");
    LinkedHashMap<String, Object> serviceDefinitionResMap =
        (LinkedHashMap<String, Object>) serviceResMap.get("serviceDefinition");
    LinkedHashMap<String, Object> specResMap = (LinkedHashMap<String, Object>) serviceDefinitionResMap.get("spec");
    LinkedHashMap<String, Object> artifactsResMap = (LinkedHashMap<String, Object>) specResMap.get("artifacts");
    LinkedHashMap<String, Object> primaryResMap = (LinkedHashMap<String, Object>) artifactsResMap.get("primary");
    ArrayList<LinkedHashMap<String, Object>> sourcesResMap =
        (ArrayList<LinkedHashMap<String, Object>>) primaryResMap.get("sources");

    for (int i = 0; i < sourcesResMap.size(); i++) {
      LinkedHashMap<String, Object> source = sourcesResMap.get(i);

      String type = String.valueOf(source.get("type"));
      type = type.substring(1, type.length() - 1);
      LinkedHashMap<String, Object> spec = (LinkedHashMap<String, Object>) source.get("spec");

      if (type.equals(ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME)) {
        if (!spec.containsKey(repositoryUrlField)) {
          String finalUrl = null;
          String connectorRef = String.valueOf(spec.get("connectorRef"));
          connectorRef = connectorRef.substring(1, connectorRef.length() - 1);
          String repository = String.valueOf(spec.get("repository"));
          repository = repository.substring(1, repository.length() - 1);
          String repositoryFormat = String.valueOf(spec.get("repositoryFormat"));
          repositoryFormat = repositoryFormat.substring(1, repositoryFormat.length() - 1);

          if (repositoryFormat.equals("docker")) {
            IdentifierRef connectorIdentifier =
                IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
            ArtifactoryConnectorDTO connector = getConnector(connectorIdentifier);
            finalUrl = getArtifactoryRegistryUrl(connector.getArtifactoryServerUrl(), null, repository);

            spec.put(repositoryUrlField, finalUrl);
          }
        }
        source.replace("spec", spec);
      }
      sourcesResMap.set(i, source);
    }

    primaryResMap.replace("sources", sourcesResMap);
    artifactsResMap.replace("primary", primaryResMap);
    specResMap.replace("artifacts", artifactsResMap);
    serviceDefinitionResMap.replace("spec", specResMap);
    serviceResMap.replace("serviceDefinition", serviceDefinitionResMap);
    yamlResMap.replace("service", serviceResMap);

    serviceEntity.setYaml(YamlPipelineUtils.writeYamlString(yamlResMap));
    return serviceEntity;
  }

  private YamlNode validateAndGetYamlNode(String yaml) {
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Service YAML is empty.");
    }
    YamlNode yamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
    }
    return yamlNode;
  }

  private Map<String, Object> getResMap(YamlNode yamlNode, String url) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    List<YamlField> childFields = yamlNode.fields();
    boolean connectorRefFlag = false;
    // Iterating over the YAML
    for (YamlField childYamlField : childFields) {
      String fieldName = childYamlField.getName();
      if (fieldName.equals("connectorRef")) {
        connectorRefFlag = true;
      }
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> ValueNode
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        // Value -> ArrayNode
        resMap.put(fieldName, getResMapInArray(childYamlField.getNode(), url));
      } else {
        // Value -> ObjectNode
        resMap.put(fieldName, getResMap(childYamlField.getNode(), url));
      }
    }
    if (connectorRefFlag == true && EmptyPredicate.isNotEmpty(url)) {
      resMap.put("repositoryUrl", url);
    }
    return resMap;
  }

  // Gets the ResMap if the yamlNode is of the type Array
  private List<Object> getResMapInArray(YamlNode yamlNode, String url) {
    List<Object> arrayList = new ArrayList<>();
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        // Value -> LeafNode
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        // Value -> Array
        arrayList.add(getResMapInArray(arrayElement, url));
      } else {
        // Value -> Object
        arrayList.add(getResMap(arrayElement, url));
      }
    }
    return arrayList;
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
}
