/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacPermissions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ServiceRbacHelper {
  @Inject private AccessControlClient accessControlClient;

  public List<ServiceEntity> getPermittedServiceList(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return Collections.emptyList();
    }

    Map<EntityScopeInfo, ServiceEntity> serviceMap = serviceEntities.stream().collect(
        Collectors.toMap(ServiceRbacHelper::getEntityScopeInfoFromService, Function.identity()));

    List<PermissionCheckDTO> permissionChecks =
        serviceEntities.stream()
            .map(service
                -> PermissionCheckDTO.builder()
                       .permission(CDNGRbacPermissions.SERVICE_VIEW_PERMISSION)
                       .resourceIdentifier(service.getIdentifier())
                       .resourceScope(ResourceScope.of(
                           service.getAccountId(), service.getOrgIdentifier(), service.getProjectIdentifier()))
                       .resourceType(NGResourceType.SERVICE)
                       .build())
            .collect(Collectors.toList());

    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<ServiceEntity> permittedServices = new ArrayList<>();

    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        ServiceEntity service =
            serviceMap.get(ServiceRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));

        if (service != null) {
          permittedServices.add(service);
        }
      }
    }
    return permittedServices;
  }

  private static EntityScopeInfo getEntityScopeInfoFromService(ServiceEntity serviceEntity) {
    return EntityScopeInfo.builder()
        .accountIdentifier(serviceEntity.getAccountId())
        .orgIdentifier(isBlank(serviceEntity.getOrgIdentifier()) ? null : serviceEntity.getOrgIdentifier())
        .projectIdentifier(isBlank(serviceEntity.getProjectIdentifier()) ? null : serviceEntity.getProjectIdentifier())
        .identifier(serviceEntity.getIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }
}
