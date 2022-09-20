/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.resources.ServiceResourceApiUtils.addLinksHeader;
import static io.harness.ng.core.service.resources.ServiceResourceApiUtils.getServiceEntity;
import static io.harness.ng.core.service.resources.ServiceResourceApiUtils.getServiceResponse;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;

import static software.wings.beans.Service.ServiceKeys;

import static java.lang.String.format;

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
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.ServicesApi;
import io.harness.spec.server.ng.model.ServiceListResponse;
import io.harness.spec.server.ng.model.ServiceRequest;
import io.harness.spec.server.ng.model.ServiceResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class ServicesResourceV3 implements ServicesApi {
  private final ServiceEntityService serviceEntityService;
  private final AccessControlClient accessControlClient;
  private final ServiceEntityManagementService serviceEntityManagementService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  public static final String SERVICE_PARAM_MESSAGE = "Service Identifier for the entity";
  public static final String SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE = "List of Service Identifiers for the entities";

  @Override
  public Response createService(ServiceRequest serviceRequest, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account) {
    throwExceptionForNoRequestDTO(serviceRequest);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    ServiceEntity serviceEntity = getServiceEntity(serviceRequest, org, project, account);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getAccountId());
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    ServiceResponse serviceResponse = getServiceResponse(createdService);
    return Response.ok().entity(serviceResponse).tag(createdService.getVersion().toString()).build();
  }

  @Override
  public Response createServicesBatch(List<ServiceRequest> arrayBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account, Integer page, Integer limit) {
    throwExceptionForNoRequestDTO(arrayBody);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);

    List<ServiceEntity> serviceEntities =
        arrayBody.stream()
            .map(arrayElement -> getServiceEntity(arrayElement, org, project, account))
            .collect(Collectors.toList());
    serviceEntities.forEach(serviceEntity
        -> orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
            serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getAccountId()));
    Page<ServiceEntity> createdServices = serviceEntityService.bulkCreate(account, serviceEntities);
    Page<ServiceResponse> serviceResponsePage = createdServices.map(ServiceResourceApiUtils::toResponseWrapper);
    List<ServiceResponse> serviceList = serviceResponsePage.getContent();
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        addLinksHeader(responseBuilder, "/v1/services/batch", serviceList.size(), page, limit);
    ServiceListResponse serviceListResponse = new ServiceListResponse();
    serviceListResponse.setContent(serviceList);
    serviceListResponse.setEmpty(isEmpty(serviceList));
    return responseBuilderWithLinks.entity(serviceListResponse).build();
  }

  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_delete")
  @Override
  public Response deleteService(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String service, @AccountIdentifier String account) {
    Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(account, org, project, service, false);
    if (!serviceEntityOptional.isPresent()) {
      throw new NotFoundException(String.format("Service with identifier [%s] not found", service));
    }
    boolean deleted = serviceEntityManagementService.deleteService(account, org, project, service, "ifMatch");
    if (!deleted) {
      throw new InvalidRequestException(String.format("Service with identifier [%s] could not be deleted", service));
    }
    return Response.ok()
        .entity(getServiceResponse(serviceEntityOptional.get()))
        .tag(serviceEntityOptional.get().getVersion().toString())
        .build();
  }

  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_view")
  @Override
  public Response getService(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String service, @AccountIdentifier String account) {
    Optional<ServiceEntity> serviceEntity = serviceEntityService.get(account, org, project, service, false);
    String version;
    ServiceEntity serviceEntity1;
    if (serviceEntity.isPresent()) {
      version = serviceEntity.get().getVersion().toString();
      serviceEntity1 = serviceEntity.get();
      if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity1);
        serviceEntity.get().setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }
    } else {
      throw new NotFoundException(
          format("Service with identifier [%s] in project [%s], org [%s] not found", service, project, org));
    }
    return Response.ok().entity(getServiceResponse(serviceEntity1)).tag(version).build();
  }

  @Override
  public Response getServices(@OrgIdentifier String org, @ProjectIdentifier String project, Integer page, Integer limit,
      String search_term, List<String> services, List sort, Boolean is_access_list, String type,
      Boolean git_ops_enabled, @AccountIdentifier String account) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    ServiceDefinitionType type1 = ServiceDefinitionType.getServiceDefinitionType(type);
    Criteria criteria =
        ServiceFilterHelper.createCriteriaForGetList(account, org, project, false, search_term, type1, git_ops_enabled);
    Pageable pageRequest;
    if (isNotEmpty(services)) {
      criteria.and(ServiceEntityKeys.identifier).in(services);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, limit, sort);
    }
    if (is_access_list == true) {
      List<ServiceResponse> serviceList = serviceEntityService.listRunTimePermission(criteria)
                                              .stream()
                                              .map(ServiceResourceApiUtils::toAccessListResponseWrapper)
                                              .collect(Collectors.toList());
      List<PermissionCheckDTO> permissionCheckDTOS =
          serviceList.stream()
              .map(ServiceResourceApiUtils::serviceResponseToPermissionCheckDTO)
              .collect(Collectors.toList());
      List<AccessControlDTO> accessControlList =
          accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
      List<ServiceResponse> filterserviceList = filterByPermissionAndId(accessControlList, serviceList);
      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks = addLinksHeader(responseBuilder,
          format("/v1/orgs/%s/projects/%s/services)", org, project), accessControlList.size(), page, limit);
      ServiceListResponse serviceListResponse = new ServiceListResponse();
      serviceListResponse.setContent(filterserviceList);
      serviceListResponse.setEmpty(isEmpty(serviceList));
      return responseBuilderWithLinks.entity(serviceListResponse).build();
    } else {
      Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);
      serviceEntities.forEach(serviceEntity -> {
        if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
          NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
          serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
        }
      });
      Page<ServiceResponse> serviceResponsePage = serviceEntities.map(ServiceResourceApiUtils::toResponseWrapper);
      List<ServiceResponse> serviceList = serviceResponsePage.getContent();

      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks = addLinksHeader(
          responseBuilder, format("/v1/orgs/%s/projects/%s/services)", org, project), serviceList.size(), page, limit);

      ServiceListResponse serviceListResponse = new ServiceListResponse();
      serviceListResponse.setContent(serviceList);
      serviceListResponse.setEmpty(isEmpty(serviceList));
      return responseBuilderWithLinks.entity(serviceListResponse).build();
    }
  }

  @Override
  public Response updateService(ServiceRequest serviceRequest, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String service, @AccountIdentifier String account) {
    throwExceptionForNoRequestDTO(serviceRequest);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(NGResourceType.SERVICE, serviceRequest.getSlug()), SERVICE_UPDATE_PERMISSION);
    ServiceEntity requestService = getServiceEntity(serviceRequest, org, project, account);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestService.getOrgIdentifier(), requestService.getProjectIdentifier(), requestService.getAccountId());
    ServiceEntity updateService = serviceEntityService.update(requestService);

    return Response.ok().entity(getServiceResponse(updateService)).tag(updateService.getVersion().toString()).build();
  }

  private void throwExceptionForNoRequestDTO(List<ServiceRequest> dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(ServiceRequest dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }
  private List<ServiceResponse> filterByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<ServiceResponse> serviceList) {
    List<ServiceResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      ServiceResponse serviceResponse = serviceList.get(i);
      if (accessControlDTO.isPermitted()
          && serviceResponse.getService().getSlug().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(serviceResponse);
      }
    }
    return filteredAccessControlDtoList;
  }
}
