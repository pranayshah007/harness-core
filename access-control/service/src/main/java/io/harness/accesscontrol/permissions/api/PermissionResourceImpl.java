/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.api;

import static io.harness.accesscontrol.permissions.PermissionStatus.ACTIVE;
import static io.harness.accesscontrol.permissions.PermissionStatus.DEPRECATED;
import static io.harness.accesscontrol.permissions.PermissionStatus.EXPERIMENTAL;

import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeMapper;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class PermissionResourceImpl implements PermissionResource {
  private final PermissionService permissionService;

  @Inject
  public PermissionResourceImpl(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Override
  public ResponseDTO<List<PermissionResponseDTO>> get(HarnessScopeParams scopeParams, boolean scopeFilterDisabled) {
    List<Permission> permissions = getPermissions(scopeParams, scopeFilterDisabled);
    return ResponseDTO.newResponse(
        permissions.stream()
            .map(permission
                -> PermissionDTOMapper.toDTO(
                    permission, permissionService.getResourceTypeFromPermission(permission).orElse(null)))
            .collect(Collectors.toList()));
  }

  @Override
  public ResponseDTO<Set<String>> getResourceTypes(HarnessScopeParams scopeParams, boolean scopeFilterDisabled) {
    List<Permission> permissions = getPermissions(scopeParams, scopeFilterDisabled);
    return ResponseDTO.newResponse(permissions.stream()
                                       .map(permissionService::getResourceTypeFromPermission)
                                       .filter(Optional::isPresent)
                                       .map(Optional::get)
                                       .map(ResourceType::getIdentifier)
                                       .collect(Collectors.toSet()));
  }

  private List<Permission> getPermissions(HarnessScopeParams scopeParams, boolean scopeFilterDisabled) {
    Set<String> scopeFilter = new HashSet<>();
    if (!scopeFilterDisabled) {
      Scope scope = ScopeMapper.fromParams(scopeParams);
      scopeFilter.add(scope.getLevel().toString());
    }
    PermissionFilter query = PermissionFilter.builder()
                                 .allowedScopeLevelsFilter(scopeFilter)
                                 .identifierFilter(new HashSet<>())
                                 .statusFilter(Sets.newHashSet(EXPERIMENTAL, ACTIVE, DEPRECATED))
                                 .build();
    return permissionService.list(query);
  }
}
