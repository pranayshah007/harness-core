/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.persistence;

import io.harness.accesscontrol.roles.Role;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class RoleDBOMapper {
  public static RoleDBO toDBO(Role object) {
    return RoleDBO.builder()
        .identifier(object.getIdentifier())
        .scopeIdentifier(object.getScopeIdentifier())
        .name(object.getName())
        .allowedScopeLevels(object.getAllowedScopeLevels())
        .permissions(object.getPermissions())
        .managed(object.isManaged())
        .internal(object.isInternal())
        .description(object.getDescription())
        .tags(object.getTags())
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .version(object.getVersion())
        .isPublic(object.isPublic())
        .build();
  }

  public static Role fromDBO(RoleDBO roleDBO) {
    return Role.builder()
        .identifier(roleDBO.getIdentifier())
        .scopeIdentifier(roleDBO.getScopeIdentifier())
        .name(roleDBO.getName())
        .allowedScopeLevels(roleDBO.getAllowedScopeLevels())
        .permissions(roleDBO.getPermissions())
        .managed(roleDBO.isManaged())
        .internal(Boolean.TRUE.equals(roleDBO.getInternal()))
        .description(roleDBO.getDescription())
        .tags(roleDBO.getTags())
        .createdAt(roleDBO.getCreatedAt())
        .lastModifiedAt(roleDBO.getLastModifiedAt())
        .version(roleDBO.getVersion())
        .isPublic(Boolean.TRUE.equals(roleDBO.getIsPublic()))
        .build();
  }
}
