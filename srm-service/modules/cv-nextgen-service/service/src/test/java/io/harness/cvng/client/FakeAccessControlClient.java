/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.accesscontrol.acl.api.AccessCheckRequestDTO;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.clients.AbstractAccessControlClient;

import java.util.stream.Collectors;

public class FakeAccessControlClient extends AbstractAccessControlClient {
  @Override
  protected AccessCheckResponseDTO checkForAccess(AccessCheckRequestDTO accessCheckRequestDTO) {
    return AccessCheckResponseDTO.builder()
        .principal(accessCheckRequestDTO.getPrincipal())
        .accessControlList(accessCheckRequestDTO.getPermissions()
                               .stream()
                               .map(permissionCheckDTO
                                   -> AccessControlDTO.builder()
                                          .permitted(false)
                                          .permission(permissionCheckDTO.getPermission())
                                          .resourceScope(permissionCheckDTO.getResourceScope())
                                          .resourceIdentifier(permissionCheckDTO.getResourceIdentifier())
                                          .resourceType(permissionCheckDTO.getResourceType())
                                          .build())
                               .collect(Collectors.toList()))
        .build();
  }
}
