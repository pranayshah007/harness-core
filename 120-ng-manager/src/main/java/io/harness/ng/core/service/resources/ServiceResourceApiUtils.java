/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static javax.ws.rs.core.UriBuilder.fromPath;

import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.spec.server.ng.model.Service;
import io.harness.spec.server.ng.model.ServiceRequest;
import io.harness.spec.server.ng.model.ServiceResponse;

import java.util.ArrayList;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceResourceApiUtils {
  public static final int FIRST_PAGE = 1;

  public ServiceResponse toResponseWrapper(ServiceEntity serviceEntity) {
    ServiceResponse serviceResponsefinal = new ServiceResponse();
    serviceResponsefinal.setService(writeDTO(serviceEntity));
    serviceResponsefinal.setCreated(serviceEntity.getCreatedAt());
    serviceResponsefinal.setUpdated(serviceEntity.getLastModifiedAt());
    return serviceResponsefinal;
  }

  public Service writeDTO(ServiceEntity serviceEntity) {
    Service service = new Service();
    service.setAccount(serviceEntity.getAccountId());
    service.setOrg(serviceEntity.getOrgIdentifier());
    service.setProject(serviceEntity.getProjectIdentifier());
    service.setSlug(serviceEntity.getIdentifier());
    service.setName(serviceEntity.getName());
    service.setDescription(serviceEntity.getDescription());
    service.setTags(convertToMap(serviceEntity.getTags()));
    service.setYaml(serviceEntity.getYaml());
    return service;
  }
  public static ServiceResponse getServiceResponse(ServiceEntity serviceEntity) {
    ServiceResponse serviceResponse = new ServiceResponse();
    io.harness.spec.server.ng.model.Service service = new io.harness.spec.server.ng.model.Service();
    service.setAccount(serviceEntity.getAccountId());
    service.setSlug(serviceEntity.getIdentifier());
    service.setOrg(serviceEntity.getOrgIdentifier());
    service.setProject(serviceEntity.getProjectIdentifier());
    service.setName(serviceEntity.getName());
    service.setDescription(serviceEntity.getDescription());
    service.setTags(convertToMap(serviceEntity.getTags()));
    service.setYaml(serviceEntity.getYaml());
    serviceResponse.setService(service);
    serviceResponse.setCreated(serviceEntity.getCreatedAt());
    serviceResponse.setUpdated(serviceEntity.getLastModifiedAt());
    return serviceResponse;
  }
  public ServiceResponse toAccessListResponseWrapper(ServiceEntity serviceEntity) {
    ServiceResponse serviceResponsefinal = new ServiceResponse();
    serviceResponsefinal.setService(writeAccessListDTO(serviceEntity));
    serviceResponsefinal.setCreated(serviceEntity.getCreatedAt());
    serviceResponsefinal.setUpdated(serviceEntity.getLastModifiedAt());
    return serviceResponsefinal;
  }
  public Service writeAccessListDTO(ServiceEntity serviceEntity) {
    Service service = new Service();
    service.setAccount(serviceEntity.getAccountId());
    service.setOrg(serviceEntity.getOrgIdentifier());
    service.setProject(serviceEntity.getProjectIdentifier());
    service.setSlug(serviceEntity.getIdentifier());
    service.setName(serviceEntity.getName());
    service.setDescription(serviceEntity.getDescription());
    service.setTags(convertToMap(serviceEntity.getTags()));
    return service;
  }

  public static ServiceEntity getServiceEntity(
      ServiceRequest sharedRequestBody, String org, String project, String account) {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .identifier(sharedRequestBody.getSlug())
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
    return serviceEntity;
  }

  public static ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= FIRST_PAGE) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
  public PermissionCheckDTO serviceResponseToPermissionCheckDTO(ServiceResponse serviceResponse) {
    return PermissionCheckDTO.builder()
        .permission(CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION)
        .resourceIdentifier(serviceResponse.getService().getSlug())
        .resourceScope(ResourceScope.builder()
                           .accountIdentifier(serviceResponse.getService().getAccount())
                           .orgIdentifier(serviceResponse.getService().getOrg())
                           .projectIdentifier(serviceResponse.getService().getProject())
                           .build())
        .resourceType(NGResourceType.SERVICE)
        .build();
  }
}
