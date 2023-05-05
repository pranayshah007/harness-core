/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;

import static java.lang.String.format;

import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.validations.helper.OrgAndProjectValidationHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.spec.server.ng.v1.model.ServiceRequest;
import io.harness.spec.server.ng.v1.model.ServiceResponse;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;

import software.wings.beans.Service.ServiceKeys;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor
public abstract class AbstractServicesApiImpl {
  @Inject private final ServiceEntityService serviceEntityService;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final ServiceEntityManagementService serviceEntityManagementService;
  @Inject private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private final ServiceResourceApiUtils serviceResourceApiUtils;
  @Inject private final ServiceEntityYamlSchemaHelper serviceSchemaHelper;

  public Response createServiceEntity(ServiceRequest serviceRequest, String org, String project, String account) {
    throwExceptionForNoRequestDTO(serviceRequest);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    serviceSchemaHelper.validateSchema(account, serviceRequest.getYaml());
    ServiceEntity serviceEntity = serviceResourceApiUtils.mapToServiceEntity(serviceRequest, org, project, account);
    if (isEmpty(serviceRequest.getYaml())) {
      serviceSchemaHelper.validateSchema(account, serviceEntity.getYaml());
    }
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getAccountId());
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    ServiceResponse serviceResponse = serviceResourceApiUtils.mapToServiceResponse(createdService);
    return Response.status(Response.Status.CREATED).entity(serviceResponse).build();
  }

  public Response deleteServiceEntity(String org, String project, String service, String account, boolean forceDelete) {
    Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(account, org, project, service, false);
    if (serviceEntityOptional.isEmpty()) {
      throw new NotFoundException(String.format("Service with identifier [%s] not found", service));
    }
    boolean deleted =
        serviceEntityManagementService.deleteService(account, org, project, service, "ifMatch", forceDelete);
    if (!deleted) {
      throw new InvalidRequestException(String.format("Service with identifier [%s] could not be deleted", service));
    }
    return Response.ok().entity(serviceResourceApiUtils.mapToServiceResponse(serviceEntityOptional.get())).build();
  }

  public Response getServiceEntity(String org, String project, String service, String account) {
    Optional<ServiceEntity> serviceEntity = serviceEntityService.get(account, org, project, service, false);
    if (serviceEntity.isEmpty()) {
      throw new NotFoundException(
          format("Service with identifier [%s] in project [%s], org [%s] not found", service, project, org));
    }
    ServiceEntity optionalServiceEntity = serviceEntity.get();
    if (EmptyPredicate.isEmpty(serviceEntity.get().getYaml())) {
      NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(optionalServiceEntity);
      serviceEntity.get().setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
    }
    return Response.ok().entity(serviceResourceApiUtils.mapToServiceResponse(optionalServiceEntity)).build();
  }

  public Response getServicesList(String org, String project, Integer page, Integer limit, String searchTerm,
      List<String> services, String sort, Boolean isAccessList, String type, Boolean gitOpsEnabled, String account,
      String order) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    ServiceDefinitionType optionalType = ServiceDefinitionType.getServiceDefinitionType(type);
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        account, org, project, false, searchTerm, optionalType, gitOpsEnabled, false);
    Pageable pageRequest;
    if (isNotEmpty(services)) {
      criteria.and(ServiceEntityKeys.identifier).in(services);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      String sortQuery = serviceResourceApiUtils.mapSort(sort, order);
      pageRequest = PageUtils.getPageRequest(page, limit, Collections.singletonList(sortQuery));
    }
    if (isAccessList != null && isAccessList) {
      List<ServiceResponse> serviceList = serviceEntityService.listRunTimePermission(criteria)
                                              .stream()
                                              .map(serviceResourceApiUtils::mapToAccessListResponse)
                                              .collect(Collectors.toList());
      List<PermissionCheckDTO> permissionCheckDTOS =
          serviceList.stream()
              .map(serviceResourceApiUtils::serviceResponseToPermissionCheckDTO)
              .collect(Collectors.toList());
      List<AccessControlDTO> accessControlList =
          accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
      List<ServiceResponse> filterserviceList = filterByPermissionAndId(accessControlList, serviceList);
      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks =
          ApiUtils.addLinksHeader(responseBuilder, filterserviceList.size(), page, limit);
      return responseBuilderWithLinks.entity(filterserviceList).build();
    } else {
      Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);
      serviceEntities.forEach(serviceEntity -> {
        if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
          NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
          serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
        }
      });
      Page<ServiceResponse> serviceResponsePage = serviceEntities.map(serviceResourceApiUtils::mapToServiceResponse);
      List<ServiceResponse> serviceList = serviceResponsePage.getContent();

      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks =
          ApiUtils.addLinksHeader(responseBuilder, serviceResponsePage.getTotalElements(), page, limit);

      return responseBuilderWithLinks.entity(serviceList).build();
    }
  }

  public Response updateServiceEntity(
      ServiceRequest serviceRequest, String org, String project, String service, String account) {
    throwExceptionForNoRequestDTO(serviceRequest);
    if (!service.equals(serviceRequest.getIdentifier())) {
      throw new InvalidRequestException(
          String.format("Identifier passed in request body: [%s] does not match resource identifier: [%s]",
              serviceRequest.getIdentifier(), service));
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(NGResourceType.SERVICE, serviceRequest.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    serviceSchemaHelper.validateSchema(account, serviceRequest.getYaml());
    ServiceEntity requestService = serviceResourceApiUtils.mapToServiceEntity(serviceRequest, org, project, account);
    if (isEmpty(serviceRequest.getYaml())) {
      serviceSchemaHelper.validateSchema(account, requestService.getYaml());
    }
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestService.getOrgIdentifier(), requestService.getProjectIdentifier(), requestService.getAccountId());
    ServiceEntity updateService = serviceEntityService.update(requestService);

    return Response.ok().entity(serviceResourceApiUtils.mapToServiceResponse(updateService)).build();
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
          && serviceResponse.getService().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(serviceResponse);
      }
    }
    return filteredAccessControlDtoList;
  }
}
