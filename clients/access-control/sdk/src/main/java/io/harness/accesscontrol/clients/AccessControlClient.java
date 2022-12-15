/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.clients;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(HarnessTeam.PL)
public interface AccessControlClient {
  AccessCheckResponseDTO checkForAccess(Principal principal, List<PermissionCheckDTO> permissionCheckDTOList);

  AccessCheckResponseDTO checkForAccess(List<PermissionCheckDTO> permissionCheckDTOList);

  boolean hasAccess(Principal principal, ResourceScope resourceScope, Resource resource, String permission);

  boolean hasAccess(ResourceScope resourceScope, Resource resource, String permission);

  void checkForAccessOrThrow(ResourceScope resourceScope, Resource resource, String permission);

  void checkForAccessOrThrow(
      ResourceScope resourceScope, Resource resource, String permission, String exceptionMessage);

  void checkForAccessOrThrow(
      Principal principal, ResourceScope resourceScope, Resource resource, String permission, String exceptionMessage);

  AccessCheckResponseDTO checkForAccessOrThrow(List<PermissionCheckDTO> permissionCheckDTOList);
}
