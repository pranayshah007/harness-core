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
  private boolean hasProdAccess = false;
  private boolean hasPreProdAccess = false;
  public List<Environment> getPermittedEnvironmentsList(List<Environment> environments) {
    if (isEmpty(environments)) {
      return Collections.emptyList();
    }

    Map<EntityScopeInfo, Environment> environmentMap = environments.stream().collect(
        Collectors.toMap(EnvironmentRbacHelper::getEntityScopeInfoFromEnvironment, Function.identity()));
    List<PermissionCheckDTO> permissionChecks =
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

    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION)
                             .resourceAttributes(getEnvironmentAttributesMap("PreProduction"))
                             .resourceScope(ResourceScope.of(environments.get(0).getAccountId(),
                                 environments.get(0).getOrgIdentifier(), environments.get(0).getProjectIdentifier()))
                             .resourceType(NGResourceType.ENVIRONMENT)
                             .build());
    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION)
                             .resourceAttributes(getEnvironmentAttributesMap("Production"))
                             .resourceScope(ResourceScope.of(environments.get(0).getAccountId(),
                                 environments.get(0).getOrgIdentifier(), environments.get(0).getProjectIdentifier()))
                             .resourceType(NGResourceType.ENVIRONMENT)
                             .build());

    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);
    List<AccessControlDTO> accessControlDTOList = accessCheckResponse.getAccessControlList();

    accessControlDTOList = CheckingTypeBasedFilters(accessControlDTOList);
    List<Environment> permittedEnvironments = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessControlDTOList) {
      Environment environment =
          environmentMap.get(EnvironmentRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));

      if (environment == null) {
        continue;
      }
      if (accessControlDTO.isPermitted() || (environment.getType().toString() == "PreProduction" && hasPreProdAccess)
          || (environment.getType().toString() == "Production" && hasProdAccess)) {
        permittedEnvironments.add(environment);
      }
    }
    return permittedEnvironments;
  }

  private List<AccessControlDTO> CheckingTypeBasedFilters(List<AccessControlDTO> accessControlDTOList) {
    for (AccessControlDTO accessControlDTO : accessControlDTOList) {
      if (accessControlDTO.isPermitted() && accessControlDTO.getResourceAttributes() != null) {
        if (accessControlDTO.getResourceAttributes().get("type") == "PreProduction") {
          hasPreProdAccess = true;
        } else if (accessControlDTO.getResourceAttributes().get("type") == "Production") {
          hasProdAccess = true;
        }
        accessControlDTOList.remove(accessControlDTO);
      }
    }
    return accessControlDTOList;
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
    environmentAttributes.put("type", environmentType);
    return environmentAttributes;
  }
}
