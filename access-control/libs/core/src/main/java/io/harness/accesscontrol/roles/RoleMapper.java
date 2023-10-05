/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.collect.Sets.newHashSet;

import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PL)
@Singleton
public class RoleMapper {
  public static RoleDTO toDTO(Role role) {
    return RoleDTO.builder()
        .identifier(role.getIdentifier())
        .name(role.getName())
        .allowedScopeLevels(toAllowedScopeLevelsEnum(role.getAllowedScopeLevels()))
        .permissions(role.getPermissions() == null ? new HashSet<>() : role.getPermissions())
        .description(role.getDescription())
        .tags(role.getTags())
        .build();
  }

  public static Set<RoleDTO.ScopeLevel> toAllowedScopeLevelsEnum(Set<String> scopeLevels) {
    if (isEmpty(scopeLevels)) {
      return newHashSet();
    }
    return scopeLevels.stream().map(RoleDTO.ScopeLevel::fromString).collect(Collectors.toSet());
  }
}
