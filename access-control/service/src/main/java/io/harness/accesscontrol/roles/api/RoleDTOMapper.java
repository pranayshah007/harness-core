/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.collect.Sets.newHashSet;

import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.api.RoleDTO.ScopeLevel;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PL)
@Singleton
public class RoleDTOMapper {
  private final ScopeService scopeService;

  @Inject
  public RoleDTOMapper(ScopeService scopeService) {
    this.scopeService = scopeService;
  }

  public RoleResponseDTO toResponseDTO(Role object) {
    Scope scope = null;
    if (object.getScopeIdentifier() != null) {
      scope = scopeService.buildScopeFromScopeIdentifier(object.getScopeIdentifier());
    }
    return RoleResponseDTO.builder()
        .role(RoleDTO.builder()
                  .identifier(object.getIdentifier())
                  .name(object.getName())
                  .allowedScopeLevels(toAllowedScopeLevelsEnum(object.getAllowedScopeLevels()))
                  .permissions(object.getPermissions())
                  .description(object.getDescription())
                  .tags(object.getTags())
                  .build())
        .scope(ScopeMapper.toDTO(scope))
        .harnessManaged(object.isManaged())
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .build();
  }

  public static Role fromDTO(String scopeIdentifier, RoleDTO object) {
    return Role.builder()
        .identifier(object.getIdentifier())
        .scopeIdentifier(scopeIdentifier)
        .name(object.getName())
        .allowedScopeLevels(fromAllowedScopeLevelsEnum(object.getAllowedScopeLevels()))
        .permissions(object.getPermissions() == null ? new HashSet<>() : object.getPermissions())
        .description(object.getDescription())
        .tags(object.getTags())
        .managed(false)
        .build();
  }

  public static Set<String> fromAllowedScopeLevelsEnum(Set<ScopeLevel> scopeLevels) {
    if (isEmpty(scopeLevels)) {
      return newHashSet();
    }
    return scopeLevels.stream().map(ScopeLevel::toString).collect(Collectors.toSet());
  }

  public static Set<ScopeLevel> toAllowedScopeLevelsEnum(Set<String> scopeLevels) {
    if (isEmpty(scopeLevels)) {
      return newHashSet();
    }
    return scopeLevels.stream().map(ScopeLevel::fromString).collect(Collectors.toSet());
  }
}
