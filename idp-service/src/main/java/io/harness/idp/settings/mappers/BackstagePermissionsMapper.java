/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class BackstagePermissionsMapper {
  public BackstagePermissions toDTO(BackstagePermissionsEntity backstagePermissionsEntity) {
    BackstagePermissions permissions = new BackstagePermissions();
    permissions.setPermissions(backstagePermissionsEntity.getPermissions());
    permissions.setUserGroup(backstagePermissionsEntity.getUserGroup());
    permissions.setCreated(backstagePermissionsEntity.getCreatedAt());
    permissions.setUpdated(backstagePermissionsEntity.getLastModifiedAt());
    permissions.setIdentifer(backstagePermissionsEntity.getId());
    return permissions;
  }

  public BackstagePermissionsEntity fromDTO(BackstagePermissions permissions, String accountIdentifier) {
    return BackstagePermissionsEntity.builder()
        .permissions(permissions.getPermissions())
        .userGroup(permissions.getUserGroup())
        .accountIdentifier(accountIdentifier)
        .id(permissions.getIdentifer())
        .build();
  }
}
