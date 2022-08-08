/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.remote.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.resourcegroup.v1.model.ResourceGroup.DEFAULT_COLOR;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.v1.model.ResourceGroup;
import io.harness.resourcegroup.v1.model.ResourceGroup.ResourceGroupBuilder;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ResourceGroupMapper {
  public static ResourceGroup fromDTO(ResourceGroupDTO resourceGroupDTO) {
    if (resourceGroupDTO == null) {
      return null;
    }
    resourceGroupDTO.sanitizeResourceSelectors();
    ResourceGroupBuilder resourceGroupBuilder =
        ResourceGroup.builder()
            .accountIdentifier(resourceGroupDTO.getAccountIdentifier())
            .orgIdentifier(resourceGroupDTO.getOrgIdentifier())
            .projectIdentifier(resourceGroupDTO.getProjectIdentifier())
            .identifier(resourceGroupDTO.getIdentifier())
            .name(resourceGroupDTO.getName())
            .color(isBlank(resourceGroupDTO.getColor()) ? DEFAULT_COLOR : resourceGroupDTO.getColor())
            .tags(convertToList(resourceGroupDTO.getTags()))
            .description(resourceGroupDTO.getDescription())
            .allowedScopeLevels(resourceGroupDTO.getAllowedScopeLevels() == null
                    ? new HashSet<>()
                    : resourceGroupDTO.getAllowedScopeLevels())
            .fullScopeSelected(resourceGroupDTO.isFullScopeSelected())
            .resourceSelectors(resourceGroupDTO.getResourceSelectors() == null
                    ? new ArrayList<>()
                    : resourceGroupDTO.getResourceSelectors());

    if (resourceGroupDTO.isFullScopeSelected()) {
      resourceGroupDTO.setResourceSelectors(Collections.emptyList());
    }

    return resourceGroupBuilder.build();
  }

  public static ResourceGroupDTO toDTO(ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }
    ResourceGroupDTO dto = ResourceGroupDTO.builder()
                               .accountIdentifier(resourceGroup.getAccountIdentifier())
                               .orgIdentifier(resourceGroup.getOrgIdentifier())
                               .projectIdentifier(resourceGroup.getProjectIdentifier())
                               .identifier(resourceGroup.getIdentifier())
                               .name(resourceGroup.getName())
                               .color(resourceGroup.getColor())
                               .tags(convertToMap(resourceGroup.getTags()))
                               .fullScopeSelected(Boolean.TRUE.equals(resourceGroup.getFullScopeSelected()))
                               .description(resourceGroup.getDescription())
                               .resourceSelectors(resourceGroup.getResourceSelectors())
                               .build();
    dto.setAllowedScopeLevels(
        resourceGroup.getAllowedScopeLevels() == null ? new HashSet<>() : resourceGroup.getAllowedScopeLevels());
    return dto;
  }

  public static ResourceGroupResponse toResponseWrapper(ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }
    return ResourceGroupResponse.builder()
        .createdAt(resourceGroup.getCreatedAt())
        .lastModifiedAt(resourceGroup.getLastModifiedAt())
        .resourceGroup(toDTO(resourceGroup))
        .harnessManaged(Boolean.TRUE.equals(resourceGroup.getHarnessManaged()))
        .build();
  }
}
