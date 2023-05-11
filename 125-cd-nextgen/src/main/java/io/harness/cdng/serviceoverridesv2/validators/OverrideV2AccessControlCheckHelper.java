/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import static io.harness.exception.WingsException.USER;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.CollectionUtils;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.pms.rbac.NGResourceType;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class OverrideV2AccessControlCheckHelper {
  @Inject private AccessControlClient accessControlClient;

  public static final String ENVIRONMENT_TYPE = "type";

  public void checkForEnvAndAttributesAccessOrThrow(
      ResourceScope resourceScope, String identifier, String permission, String environmentType) {
    Map<String, String> environmentAttributes = getEnvironmentAttributesMap(environmentType);
    List<PermissionCheckDTO> permissionChecks =
        getPermissionChecksDTOForEnvironment(environmentAttributes, resourceScope, identifier, permission);
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);
    List<AccessControlDTO> accessControlDTOList = accessCheckResponse.getAccessControlList();

    final boolean isActionAllowed =
        CollectionUtils.emptyIfNull(accessControlDTOList).stream().anyMatch(AccessControlDTO::isPermitted);
    if (!isActionAllowed) {
      throw new NGAccessDeniedException(
          String.format("Missing permission %s on %s with identifier %s", permission, ENVIRONMENT, identifier), USER,
          permissionChecks);
    }
  }

  public void validateRBACForService(ServiceOverrideRequestDTOV2 requestDTOV2, String accountId) {
    IdentifierRef svcIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        requestDTOV2.getServiceRef(), accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, svcIdentifierRef.getOrgIdentifier(), svcIdentifierRef.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, svcIdentifierRef.getIdentifier()), SERVICE_UPDATE_PERMISSION);
  }

  private List<PermissionCheckDTO> getPermissionChecksDTOForEnvironment(
      Map<String, String> environmentAttributes, ResourceScope resourceScope, String identifier, String permission) {
    return List.of(PermissionCheckDTO.builder()
                       .permission(permission)
                       .resourceIdentifier(identifier)
                       .resourceScope(resourceScope)
                       .resourceType(ENVIRONMENT)
                       .build(),
        PermissionCheckDTO.builder()
            .permission(permission)
            .resourceAttributes(environmentAttributes)
            .resourceScope(resourceScope)
            .resourceType(ENVIRONMENT)
            .build()

    );
  }

  private Map<String, String> getEnvironmentAttributesMap(String environmentType) {
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put(ENVIRONMENT_TYPE, environmentType);
    return environmentAttributes;
  }
}
