/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static software.wings.beans.Service.ServiceKeys;

import static java.lang.Long.parseLong;
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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacUtility;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
public class ServiceResourceV2 {
  private final ServiceEntityService serviceEntityService;
  private final AccessControlClient accessControlClient;
  private final ServiceEntityManagementService serviceEntityManagementService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  public static final String SERVICE_PARAM_MESSAGE = "Service Identifier for the entity";

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
      throw new NotFoundException(String.format("Service with identifier [%s] in project [%s], org [%s] not found",
          serviceIdentifier, projectIdentifier, orgIdentifier));
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
      @Parameter(description = "Details of the Service to be created") @Valid ServiceRequestDTO serviceRequestDTO) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
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
      @Parameter(
          description = "Details of the Services to be created") @Valid List<ServiceRequestDTO> serviceRequestDTOs) {
    throwExceptionForNoRequestDTO(serviceRequestDTOs);
    for (ServiceRequestDTO serviceRequestDTO : serviceRequestDTOs) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
          Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    }
    List<ServiceEntity> serviceEntities =
        serviceRequestDTOs.stream()
            .map(serviceRequestDTO -> ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO))
            .collect(Collectors.toList());
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
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if the Service is deleted")
      })
  public ResponseDTO<Boolean>
  delete(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(serviceEntityManagementService.deleteService(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, ifMatch));
  }

  @PUT
  @ApiOperation(value = "Update a service by identifier", nickname = "updateServiceV2")
  @Operation(operationId = "updateServiceV2", summary = "Update a Service by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Service")
      })
  public ResponseDTO<ServiceResponse>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Service to be updated") @Valid ServiceRequestDTO serviceRequestDTO) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceEntity updatedService = serviceEntityService.update(requestService);
    return ResponseDTO.newResponse(
        updatedService.getVersion().toString(), ServiceElementMapper.toResponseWrapper(updatedService));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert a service by identifier", nickname = "upsertServiceV2")
  @Operation(operationId = "upsertServiceV2", summary = "Upsert a Service by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Service")
      })
  public ResponseDTO<ServiceResponse>
  upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Service to be updated") @Valid ServiceRequestDTO serviceRequestDTO) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestService.getOrgIdentifier(), requestService.getProjectIdentifier(), requestService.getAccountId());
    ServiceEntity upsertService = serviceEntityService.upsert(requestService);
    return ResponseDTO.newResponse(
        upsertService.getVersion().toString(), ServiceElementMapper.toResponseWrapper(upsertService));
  }

  @GET
  @ApiOperation(value = "Gets Service list ", nickname = "getServiceList")
  @Operation(operationId = "getServiceList", summary = "Gets Service list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Services for a Project")
      })
  public ResponseDTO<PageResponse<ServiceResponse>>
  listServices(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
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
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");

    Criteria criteria =
        ServiceFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false, searchTerm);
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
    serviceEntities.forEach(serviceEntity -> {
      if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }
    });
    return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(ServiceElementMapper::toResponseWrapper)));
  }

  @GET
  @Path("/list/access")
  @ApiOperation(value = "Gets Service Access list ", nickname = "getServiceAccessList")
  @Operation(operationId = "getServiceAccessList", summary = "Gets Service Access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns the list of Services for a Project that are accessible")
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
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(PROJECT, projectIdentifier), VIEW_PROJECT_PERMISSION, "Unauthorized to list services");

    Criteria criteria =
        ServiceFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false, searchTerm);
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }
    List<ServiceResponse> serviceList = serviceEntityService.listRunTimePermission(criteria)
                                            .stream()
                                            .map(ServiceElementMapper::toAccessListResponseWrapper)
                                            .collect(Collectors.toList());
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
}
