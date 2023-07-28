/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.spec.server.ng.v1.model.Service;
import io.harness.spec.server.ng.v1.model.ServiceRequest;
import io.harness.spec.server.ng.v1.model.ServiceResponse;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Singleton
public class ServiceResourceApiUtils {
  private final Validator validator;

  @Inject
  public ServiceResourceApiUtils(Validator validator) {
    this.validator = validator;
  }

  public ServiceResponse mapToServiceResponse(ServiceEntity serviceEntity) {
    ServiceResponse serviceResponse = new ServiceResponse();
    Service service = new Service();
    service.setAccount(serviceEntity.getAccountId());
    service.setIdentifier(serviceEntity.getIdentifier());
    service.setOrg(serviceEntity.getOrgIdentifier());
    service.setProject(serviceEntity.getProjectIdentifier());
    service.setName(serviceEntity.getName());
    service.setDescription(serviceEntity.getDescription());
    service.setTags(convertToMap(serviceEntity.getTags()));
    service.setYaml(serviceEntity.getYaml());
    serviceResponse.setService(service);
    serviceResponse.setCreated(serviceEntity.getCreatedAt());
    serviceResponse.setUpdated(serviceEntity.getLastModifiedAt());
    Set<ConstraintViolation<ServiceResponse>> violations = validator.validate(serviceResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return serviceResponse;
  }
  public ServiceResponse mapToAccessListResponse(ServiceEntity serviceEntity) {
    ServiceResponse serviceResponse = new ServiceResponse();
    Service service = new Service();
    service.setAccount(serviceEntity.getAccountId());
    service.setOrg(serviceEntity.getOrgIdentifier());
    service.setProject(serviceEntity.getProjectIdentifier());
    service.setIdentifier(serviceEntity.getIdentifier());
    service.setName(serviceEntity.getName());
    service.setDescription(serviceEntity.getDescription());
    service.setTags(convertToMap(serviceEntity.getTags()));
    serviceResponse.setService(service);
    serviceResponse.setCreated(serviceEntity.getCreatedAt());
    serviceResponse.setUpdated(serviceEntity.getLastModifiedAt());
    Set<ConstraintViolation<ServiceResponse>> violations = validator.validate(serviceResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return serviceResponse;
  }

  public ServiceEntity mapToServiceEntity(
      ServiceRequest sharedRequestBody, String org, String project, String account) {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .identifier(sharedRequestBody.getIdentifier())
                                      .accountId(account)
                                      .orgIdentifier(org)
                                      .projectIdentifier(project)
                                      .name(sharedRequestBody.getName())
                                      .description(sharedRequestBody.getDescription())
                                      .tags(convertToList(sharedRequestBody.getTags()))
                                      .yaml(sharedRequestBody.getYaml())
                                      .build();
    // This also validates the service yaml
    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    if (isEmpty(serviceEntity.getYaml())) {
      serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
    }
    serviceEntity.setGitOpsEnabled(ngServiceV2InfoConfig.getGitOpsEnabled());
    if (ngServiceV2InfoConfig.getServiceDefinition() != null) {
      serviceEntity.setType(ngServiceV2InfoConfig.getServiceDefinition().getType());
    }
    Set<ConstraintViolation<ServiceEntity>> violations = validator.validate(serviceEntity);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }

    return serviceEntity;
  }

  public PermissionCheckDTO serviceResponseToPermissionCheckDTO(ServiceResponse serviceResponse) {
    return PermissionCheckDTO.builder()
        .permission(CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION)
        .resourceIdentifier(serviceResponse.getService().getIdentifier())
        .resourceScope(ResourceScope.builder()
                           .accountIdentifier(serviceResponse.getService().getAccount())
                           .orgIdentifier(serviceResponse.getService().getOrg())
                           .projectIdentifier(serviceResponse.getService().getProject())
                           .build())
        .resourceType(NGResourceType.SERVICE)
        .build();
  }

  public String mapSort(String sort, String order) {
    String property;
    switch (sort) {
      case "identifier":
        property = ServiceEntityKeys.identifier;
        break;
      case "harness_account":
        property = ServiceEntityKeys.accountId;
        break;
      case "org":
        property = ServiceEntityKeys.orgIdentifier;
        break;
      case "project":
        property = ServiceEntityKeys.projectIdentifier;
        break;
      case "created":
        property = ServiceEntityKeys.createdAt;
        break;
      case "updated":
        property = ServiceEntityKeys.lastModifiedAt;
        break;
      default:
        property = sort;
    }
    return property + ',' + order;
  }

  static void validateServiceScope(ServiceRequestDTO requestDTO) {
    try {
      Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
          "org identifier must be specified. Services can only be created at Project scope");
      Preconditions.checkArgument(isNotEmpty(requestDTO.getProjectIdentifier()),
          "project identifier must be specified. Services can only be created at Project scope");
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  public void throwExceptionForNoRequestDTO(ServiceRequest dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  public List<ServiceResponse> filterByPermissionAndId(
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