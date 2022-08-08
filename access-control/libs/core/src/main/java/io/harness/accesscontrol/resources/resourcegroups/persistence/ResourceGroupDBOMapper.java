/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.persistence;

import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ResourceGroupDBOMapper {
  public static ResourceGroupDBO toDBO(ResourceGroup object) {
    return ResourceGroupDBO.builder()
        .identifier(object.getIdentifier())
        .scopeIdentifier(object.getScopeIdentifier())
        .name(object.getName())
        .allowedScopeLevels(object.getAllowedScopeLevels())
        .resourceSelectors(object.getResourceSelectors())
        .resourceSelectorsV2(object.getResourceSelectorsV2())
        .managed(object.isManaged())
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .version(object.getVersion())
        .build();
  }

  public static ResourceGroup fromDBO(ResourceGroupDBO object) {
    return ResourceGroup.builder()
        .identifier(object.getIdentifier())
        .scopeIdentifier(object.getScopeIdentifier())
        .name(object.getName())
        .allowedScopeLevels(object.getAllowedScopeLevels())
        .resourceSelectors(object.getResourceSelectors())
        .resourceSelectorsV2(object.getResourceSelectorsV2())
        .managed(Boolean.TRUE.equals(object.getManaged()))
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .version(object.getVersion())
        .build();
  }
}
