/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.ng.core.dto.EntityScopeInfo;
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
public class EnvironmentGroupRbacHelper {
  @Inject private AccessControlClient accessControlClient;

  public List<EnvironmentGroupEntity> getPermittedEnvironmentGroupList(List<EnvironmentGroupEntity> environmentGroups) {
    if (isEmpty(environmentGroups)) {
      return Collections.emptyList();
    }
    Map<EntityScopeInfo, EnvironmentGroupEntity> environmentGroupMap = environmentGroups.stream().collect(
        Collectors.toMap(EnvironmentGroupRbacHelper::getEntityScopeInfoFromEnvironmentGroup, Function.identity()));
    List<PermissionCheckDTO> permissionChecks =
        environmentGroups.stream()
            .map(environmentGroup
                -> PermissionCheckDTO.builder()
                       .permission(CDNGRbacPermissions.ENVIRONMENT_GROUP_VIEW_PERMISSION)
                       .resourceIdentifier(environmentGroup.getIdentifier())
                       .resourceScope(ResourceScope.of(environmentGroup.getAccountIdentifier(),
                           environmentGroup.getOrgIdentifier(), environmentGroup.getProjectIdentifier()))
                       .resourceType(NGResourceType.ENVIRONMENT_GROUP)
                       .build())
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);
    List<EnvironmentGroupEntity> permittedEnvironmentGroups = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        EnvironmentGroupEntity environmentGroup = environmentGroupMap.get(
            EnvironmentGroupRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));
        if (environmentGroup != null) {
          permittedEnvironmentGroups.add(environmentGroup);
        }
      }
    }
    return permittedEnvironmentGroups;
  }

  private static EntityScopeInfo getEntityScopeInfoFromEnvironmentGroup(EnvironmentGroupEntity environmentGroup) {
    return EntityScopeInfo.builder()
        .accountIdentifier(environmentGroup.getAccountIdentifier())
        .orgIdentifier(isBlank(environmentGroup.getOrgIdentifier()) ? null : environmentGroup.getOrgIdentifier())
        .projectIdentifier(
            isBlank(environmentGroup.getProjectIdentifier()) ? null : environmentGroup.getProjectIdentifier())
        .identifier(environmentGroup.getIdentifier())
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
