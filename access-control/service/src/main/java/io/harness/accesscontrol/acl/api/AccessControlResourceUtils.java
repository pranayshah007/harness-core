/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.security.dto.PrincipalType.SERVICE;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class AccessControlResourceUtils {
  public static boolean serviceContextAndOnlyServicePrincipalInBody(
      io.harness.security.dto.Principal principalInContext, Principal principalToCheckPermissions) {
    Optional<io.harness.security.dto.Principal> serviceCall =
        Optional.ofNullable(principalInContext).filter(x -> SERVICE.equals(x.getType()));

    return serviceCall.isPresent()
        && (principalToCheckPermissions == null
            || Objects.equals(serviceCall.get().getName(), principalToCheckPermissions.getPrincipalIdentifier())
            || PrincipalType.SERVICE.equals(principalToCheckPermissions.getPrincipalType()));
  }

  private static boolean userContextAndDifferentPrincipalInBody(
      io.harness.security.dto.Principal principalInContext, Principal principalToCheckPermissions) {
    /* check if a principal of type other than SERVICE is trying to check permissions for any
       other principal */
    Optional<io.harness.security.dto.Principal> nonServiceCall =
        Optional.ofNullable(principalInContext).filter(x -> !SERVICE.equals(x.getType()));
    return nonServiceCall.isPresent()
        && (principalToCheckPermissions != null
            && !Objects.equals(principalInContext.getName(), principalToCheckPermissions.getPrincipalIdentifier()));
  }

  private static boolean checkForValidContext(io.harness.security.dto.Principal principalInContext) {
    return principalInContext != null && principalInContext.getName() != null && principalInContext.getType() != null;
  }

  public static AccessControlDTO getAccessControlDTO(PermissionCheckDTO permissionCheckDTO, boolean permitted) {
    return AccessControlDTO.builder()
        .permission(permissionCheckDTO.getPermission())
        .resourceIdentifier(permissionCheckDTO.getResourceIdentifier())
        .resourceScope(permissionCheckDTO.getResourceScope())
        .resourceType(permissionCheckDTO.getResourceType())
        .resourceAttributes(permissionCheckDTO.getResourceAttributes())
        .permitted(permitted)
        .build();
  }

  public static boolean checkPreconditions(
      io.harness.security.dto.Principal contextPrincipal, Principal principalToCheckPermissionsFor) {
    boolean validContext = checkForValidContext(contextPrincipal);
    if (!validContext) {
      return false;
    }
    if (serviceContextAndOnlyServicePrincipalInBody(contextPrincipal, principalToCheckPermissionsFor)) {
      return true;
    }
    if (userContextAndDifferentPrincipalInBody(contextPrincipal, principalToCheckPermissionsFor)) {
      return false;
    }
    return true;
  }

  public static boolean checkResourcePreconditions(List<PermissionCheckDTO> permissions) {
    return permissions.stream().allMatch(permissionCheckDTO
        -> isEmpty(permissionCheckDTO.getResourceIdentifier()) || isEmpty(permissionCheckDTO.getResourceAttributes()));
  }
}
