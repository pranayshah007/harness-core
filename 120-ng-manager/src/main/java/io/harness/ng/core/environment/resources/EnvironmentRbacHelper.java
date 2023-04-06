/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

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
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacPermissions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentRbacHelper {
  @Inject private AccessControlClient accessControlClient;
  private final String TYPE = "type";
  public List<Environment> getPermittedEnvironmentsList(List<Environment> environments) {
    // This method assumes that all environments are at the same scope
    if (isEmpty(environments)) {
      return Collections.emptyList();
    }

    Map<EntityScopeInfo, Environment> environmentMap = environments.stream().collect(
        Collectors.toMap(EnvironmentRbacHelper::getEntityScopeInfoFromEnvironment, Function.identity()));

    final List<PermissionCheckDTO> permissionChecks =
        environments.stream()
            .map(environment
                -> PermissionCheckDTO.builder()
                       .permission(CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION)
                       .resourceIdentifier(environment.getIdentifier())
                       .resourceScope(ResourceScope.of(environment.getAccountId(), environment.getOrgIdentifier(),
                           environment.getProjectIdentifier()))
                       .resourceType(NGResourceType.ENVIRONMENT)
                       .build())
            .collect(Collectors.toList());

    // pre-prod permission check
    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION)
                             .resourceAttributes(getEnvironmentAttributesMap(EnvironmentType.PreProduction.name()))
                             .resourceScope(ResourceScope.of(environments.get(0).getAccountId(),
                                 environments.get(0).getOrgIdentifier(), environments.get(0).getProjectIdentifier()))
                             .resourceType(NGResourceType.ENVIRONMENT)
                             .build());

    // Prod permission check
    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION)
                             .resourceAttributes(getEnvironmentAttributesMap(EnvironmentType.Production.name()))
                             .resourceScope(ResourceScope.of(environments.get(0).getAccountId(),
                                 environments.get(0).getOrgIdentifier(), environments.get(0).getProjectIdentifier()))
                             .resourceType(NGResourceType.ENVIRONMENT)
                             .build());

    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    final EnvironmentTypeFilteredResponse environmentTypeFilteredResponse =
        checkingTypeBasedFilters(accessCheckResponse.getAccessControlList());
    final List<AccessControlDTO> onlyIdentifierBasedAccessCheckList =
        removeTypeBasedAccessControlDTOs(accessCheckResponse.getAccessControlList());

    final boolean hasPreProdAccess = environmentTypeFilteredResponse.hasPreProdAccess;
    final boolean hasProdAccess = environmentTypeFilteredResponse.hasProdAccess;

    List<Environment> permittedEnvironments = new ArrayList<>();

    for (AccessControlDTO accessControlDTO : onlyIdentifierBasedAccessCheckList) {
      Environment environment =
          environmentMap.get(EnvironmentRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));

      if (environment == null) {
        continue;
      }

      if (accessControlDTO.isPermitted()
          || (EnvironmentType.PreProduction.name().equals(environment.getType().name()) && hasPreProdAccess)
          || (EnvironmentType.Production.name().equals(environment.getType().name()) && hasProdAccess)) {
        permittedEnvironments.add(environment);
      }
    }

    return permittedEnvironments;
  }

  private List<AccessControlDTO> removeTypeBasedAccessControlDTOs(List<AccessControlDTO> accessControlDTOList) {
    return accessControlDTOList.stream()
        .filter(dto -> isEmpty(dto.getResourceAttributes()) || isEmpty(dto.getResourceAttributes().get(TYPE)))
        .collect(Collectors.toList());
  }

  private EnvironmentTypeFilteredResponse checkingTypeBasedFilters(List<AccessControlDTO> accessControlDTOList) {
    boolean hasPreProdAccess = false;
    boolean hasProdAccess = false;

    for (AccessControlDTO accessControlDTO : accessControlDTOList) {
      if (accessControlDTO.isPermitted() && accessControlDTO.getResourceAttributes() != null) {
        if (EnvironmentType.PreProduction.name().equals(accessControlDTO.getResourceAttributes().get(TYPE))) {
          hasPreProdAccess = true;
        } else if (EnvironmentType.Production.name().equals(accessControlDTO.getResourceAttributes().get(TYPE))) {
          hasProdAccess = true;
        }
      }
    }
    return new EnvironmentTypeFilteredResponse(hasPreProdAccess, hasProdAccess);
  }

  private static EntityScopeInfo getEntityScopeInfoFromEnvironment(Environment environmentEntity) {
    return EntityScopeInfo.builder()
        .accountIdentifier(environmentEntity.getAccountId())
        .orgIdentifier(isBlank(environmentEntity.getOrgIdentifier()) ? null : environmentEntity.getOrgIdentifier())
        .projectIdentifier(
            isBlank(environmentEntity.getProjectIdentifier()) ? null : environmentEntity.getProjectIdentifier())
        .identifier(environmentEntity.getIdentifier())
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
  private Map<String, String> getEnvironmentAttributesMap(String environmentType) {
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put(TYPE, environmentType);
    return environmentAttributes;
  }
}
