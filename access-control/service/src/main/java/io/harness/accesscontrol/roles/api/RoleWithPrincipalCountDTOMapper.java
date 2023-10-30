/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.collect.Sets.newHashSet;

import io.harness.accesscontrol.roles.RoleWithPrincipalCount;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;

import com.google.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleWithPrincipalCountDTOMapper {
  private final ScopeService scopeService;

  @Inject
  public RoleWithPrincipalCountDTOMapper(ScopeService scopeService) {
    this.scopeService = scopeService;
  }

  public RoleWithPrincipalCountResponseDTO toResponseDTO(RoleWithPrincipalCount object) {
    Scope scope = null;
    if (object.getRole().getScopeIdentifier() != null) {
      scope = scopeService.buildScopeFromScopeIdentifier(object.getRole().getScopeIdentifier());
    }
    return RoleWithPrincipalCountResponseDTO.builder()
        .role(RoleDTO.builder()
                  .identifier(object.getRole().getIdentifier())
                  .name(object.getRole().getName())
                  .allowedScopeLevels(toAllowedScopeLevelsEnum(object.getRole().getAllowedScopeLevels()))
                  .permissions(object.getRole().getPermissions())
                  .description(object.getRole().getDescription())
                  .tags(object.getRole().getTags())
                  .build())
        .scope(ScopeMapper.toDTO(scope))
        .harnessManaged(object.isHarnessManaged())
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .roleAssignedToUserCount(object.getRoleAssignedToUserCount())
        .roleAssignedToUserGroupCount(object.getRoleAssignedToUserGroupCount())
        .roleAssignedToServiceAccountCount(object.getRoleAssignedToServiceAccountCount())
        .build();
  }

  public static Set<RoleDTO.ScopeLevel> toAllowedScopeLevelsEnum(Set<String> scopeLevels) {
    if (isEmpty(scopeLevels)) {
      return newHashSet();
    }
    return scopeLevels.stream().map(RoleDTO.ScopeLevel::fromString).collect(Collectors.toSet());
  }
}
