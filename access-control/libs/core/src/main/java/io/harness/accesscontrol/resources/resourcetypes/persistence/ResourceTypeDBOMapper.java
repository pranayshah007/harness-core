/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcetypes.persistence;

import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ResourceTypeDBOMapper {
  public static ResourceTypeDBO toDBO(ResourceType resourceType) {
    return ResourceTypeDBO.builder()
        .identifier(resourceType.getIdentifier())
        .permissionKey(resourceType.getPermissionKey())
        .isPublic(resourceType.isPublic())
        .build();
  }

  public static ResourceType fromDBO(ResourceTypeDBO resourceTypeDBO) {
    return ResourceType.builder()
        .identifier(resourceTypeDBO.getIdentifier())
        .permissionKey(resourceTypeDBO.getPermissionKey())
        .isPublic(Boolean.TRUE.equals(resourceTypeDBO.getIsPublic()))
        .build();
  }
}
