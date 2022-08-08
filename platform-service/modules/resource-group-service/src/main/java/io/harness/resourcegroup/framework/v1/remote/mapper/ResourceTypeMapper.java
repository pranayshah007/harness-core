/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.remote.mapper;

import io.harness.resourcegroup.v1.remote.dto.ResourceTypeDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceTypeDTO.ResourceType;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ResourceTypeMapper {
  public static ResourceTypeDTO toDTO(List<ResourceType> resourceTypes) {
    if (resourceTypes == null) {
      return null;
    }
    return ResourceTypeDTO.builder().resourceTypes(resourceTypes).build();
  }
}
