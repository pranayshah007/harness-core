/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ACCOUNT;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ORGANIZATION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
public class ScopeAccessHelper {
  private final AccessControlClient accessControlClient;

  @Inject
  public ScopeAccessHelper(AccessControlClient accessControlClient) {
    this.accessControlClient = accessControlClient;
  }

  public List<Scope> getPermittedScopes(List<Scope> scopes) {
    if (scopes.isEmpty()) {
      return Collections.emptyList();
    }

    return Streams.stream(Iterables.partition(scopes, 1000))
        .map(this::checkAccess)
        .flatMap(List<Scope>::stream)
        .collect(Collectors.toList());
  }

  private List<Scope> checkAccess(List<Scope> scopes) {
    List<PermissionCheckDTO> permissionChecks =
        scopes.stream().map(this::getPermissionCheckDtoForViewAccessForScope).collect(Collectors.toList());

    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccess(permissionChecks);
    Set<Scope> permittedScopes = accessCheckResponse.getAccessControlList()
                                     .stream()
                                     .filter(AccessControlDTO::isPermitted)
                                     .map(this::getScopeFromAccessCheckResponse)
                                     .collect(Collectors.toSet());

    return scopes.stream().filter(permittedScopes::contains).collect(Collectors.toList());
  }

  private Scope getScopeFromAccessCheckResponse(AccessControlDTO accessControlDTO) {
    if (accessControlDTO.getResourceType().equals(PROJECT)) {
      return Scope.builder()
          .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
          .orgIdentifier(accessControlDTO.getResourceScope().getOrgIdentifier())
          .projectIdentifier(accessControlDTO.getResourceIdentifier())
          .build();
    } else if (accessControlDTO.getResourceType().equals(ORGANIZATION)) {
      return Scope.builder()
          .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
          .orgIdentifier(accessControlDTO.getResourceIdentifier())
          .build();
    }
    return Scope.builder().accountIdentifier(accessControlDTO.getResourceIdentifier()).build();
  }

  public PermissionCheckDTO getPermissionCheckDtoForViewAccessForScope(Scope scope) {
    if (StringUtils.isNotBlank(scope.getProjectIdentifier())) {
      return PermissionCheckDTO.builder()
          .permission(VIEW_PROJECT_PERMISSION)
          .resourceIdentifier(scope.getProjectIdentifier())
          .resourceScope(ResourceScope.builder()
                             .accountIdentifier(scope.getAccountIdentifier())
                             .orgIdentifier(scope.getOrgIdentifier())
                             .build())
          .resourceType(PROJECT)
          .build();
    } else if (StringUtils.isNotBlank(scope.getOrgIdentifier())) {
      return PermissionCheckDTO.builder()
          .permission(VIEW_ORGANIZATION_PERMISSION)
          .resourceIdentifier(scope.getOrgIdentifier())
          .resourceScope(ResourceScope.builder().accountIdentifier(scope.getAccountIdentifier()).build())
          .resourceType(ORGANIZATION)
          .build();
    }
    return PermissionCheckDTO.builder()
        .permission(VIEW_ACCOUNT_PERMISSION)
        .resourceIdentifier(scope.getAccountIdentifier())
        .resourceScope(ResourceScope.builder().build())
        .resourceType(ACCOUNT)
        .build();
  }
}
