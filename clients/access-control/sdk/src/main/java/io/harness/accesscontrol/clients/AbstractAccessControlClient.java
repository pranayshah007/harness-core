/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.clients;

import static io.harness.exception.WingsException.USER;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckRequestDTO;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.UnexpectedException;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PL)
public abstract class AbstractAccessControlClient implements AccessControlClient {
  private static final String ORGANIZATION_RESOURCE_TYPE = "ORGANIZATION";
  private static final String ACCOUNT_RESOURCE_TYPE = "ACCOUNT";
  private static final String PROJECT_RESOURCE_TYPE = "PROJECT";

  public AbstractAccessControlClient() {}

  protected abstract AccessCheckResponseDTO checkForAccess(AccessCheckRequestDTO accessCheckRequestDTO);

  @Override
  public AccessCheckResponseDTO checkForAccess(Principal principal, List<PermissionCheckDTO> permissionCheckDTOList) {
    AccessCheckRequestDTO accessCheckRequestDTO =
        AccessCheckRequestDTO.builder().principal(principal).permissions(permissionCheckDTOList).build();
    return checkForAccess(accessCheckRequestDTO);
  }

  @Override
  public AccessCheckResponseDTO checkForAccess(List<PermissionCheckDTO> permissionCheckDTOList) {
    return checkForAccess(null, permissionCheckDTOList);
  }

  @Override
  public boolean hasAccess(ResourceScope resourceScope, Resource resource, String permission) {
    return hasAccess(null, resourceScope, resource, permission);
  }

  @Override
  public boolean hasAccess(Principal principal, ResourceScope resourceScope, Resource resource, String permission) {
    try {
      checkForAccessOrThrowInternal(principal, resourceScope, resource, permission, null);
      return true;
    } catch (AccessDeniedException accessDeniedException) {
      return false;
    }
  }

  private PermissionCheckDTO getPermissionCheckDTOForScope(ResourceScope resourceScope, String permission) {
    PermissionCheckDTO.Builder builder = PermissionCheckDTO.builder().permission(permission);
    if (!StringUtils.isEmpty(resourceScope.getProjectIdentifier())) {
      return builder
          .resourceScope(ResourceScope.builder()
                             .accountIdentifier(resourceScope.getAccountIdentifier())
                             .orgIdentifier(resourceScope.getOrgIdentifier())
                             .build())
          .resourceType(PROJECT_RESOURCE_TYPE)
          .resourceIdentifier(resourceScope.getProjectIdentifier())
          .build();
    }
    if (!StringUtils.isEmpty(resourceScope.getOrgIdentifier())) {
      return builder
          .resourceScope(ResourceScope.builder().accountIdentifier(resourceScope.getAccountIdentifier()).build())
          .resourceType(ORGANIZATION_RESOURCE_TYPE)
          .resourceIdentifier(resourceScope.getOrgIdentifier())
          .build();
    }
    return builder.resourceScope(ResourceScope.builder().build())
        .resourceType(ACCOUNT_RESOURCE_TYPE)
        .resourceIdentifier(resourceScope.getAccountIdentifier())
        .build();
  }

  @Override
  public void checkForAccessOrThrow(
      @Nullable ResourceScope resourceScope, @Nullable Resource resource, @NotNull String permission) {
    checkForAccessOrThrowInternal(null, resourceScope, resource, permission, null);
  }

  @Override
  public void checkForAccessOrThrow(
      ResourceScope resourceScope, Resource resource, String permission, String exceptionMessage) {
    checkForAccessOrThrowInternal(null, resourceScope, resource, permission, exceptionMessage);
  }

  @Override
  public void checkForAccessOrThrow(
      Principal principal, ResourceScope resourceScope, Resource resource, String permission, String exceptionMessage) {
    checkForAccessOrThrowInternal(principal, resourceScope, resource, permission, exceptionMessage);
  }

  private void checkForAccessOrThrowInternal(
      Principal principal, ResourceScope resourceScope, Resource resource, String permission, String exceptionMessage) {
    PermissionCheckDTO permissionCheckDTO;
    if (resource == null && resourceScope == null) {
      throw new UnexpectedException("Both resource scope and resource cannot be null together");
    }
    if (resource == null) {
      permissionCheckDTO = getPermissionCheckDTOForScope(resourceScope, permission);
    } else {
      permissionCheckDTO = PermissionCheckDTO.builder()
                               .permission(permission)
                               .resourceType(resource.getResourceType())
                               .resourceIdentifier(resource.getResourceIdentifier())
                               .resourceScope(resourceScope)
                               .resourceAttributes(resource.getResourceAttributes())
                               .build();
    }
    AccessCheckResponseDTO accessCheckResponseDTO =
        checkForAccess(principal, Collections.singletonList(permissionCheckDTO));
    AccessControlDTO accessControlDTO = accessCheckResponseDTO.getAccessControlList().get(0);
    String finalMessage;
    if (!StringUtils.isEmpty(exceptionMessage)) {
      finalMessage = exceptionMessage;
    } else {
      finalMessage = String.format("Missing permission %s on %s", accessControlDTO.getPermission(),
          accessControlDTO.getResourceType().toLowerCase());
      if (!StringUtils.isEmpty(accessControlDTO.getResourceIdentifier())) {
        finalMessage =
            finalMessage.concat(String.format(" with identifier %s", accessControlDTO.getResourceIdentifier()));
      }
    }
    if (!accessControlDTO.isPermitted()) {
      throw new NGAccessDeniedException(finalMessage, USER, Collections.singletonList(permissionCheckDTO));
    }
  }
}
