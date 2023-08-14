/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.remote.mapper;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;

import java.util.ArrayList;
import java.util.HashSet;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ResourceGroupMapper {
  public static ResourceGroup fromDTO(ResourceGroupDTO resourceGroupDTO) {
    if (resourceGroupDTO == null) {
      return null;
    }
    ResourceGroup resourceGroup =
        ResourceGroup.builder()
            .accountIdentifier(resourceGroupDTO.getAccountIdentifier())
            .orgIdentifier(resourceGroupDTO.getOrgIdentifier())
            .projectIdentifier(resourceGroupDTO.getProjectIdentifier())
            .identifier(resourceGroupDTO.getIdentifier())
            .name(resourceGroupDTO.getName())
            .color(isBlank(resourceGroupDTO.getColor()) ? HARNESS_BLUE : resourceGroupDTO.getColor())
            .tags(convertToList(resourceGroupDTO.getTags()))
            .description(resourceGroupDTO.getDescription())
            .allowedScopeLevels(resourceGroupDTO.getAllowedScopeLevels() == null
                    ? new HashSet<>()
                    : resourceGroupDTO.getAllowedScopeLevels())
            .includedScopes(
                resourceGroupDTO.getIncludedScopes() == null ? new ArrayList<>() : resourceGroupDTO.getIncludedScopes())
            .build();

    if (resourceGroupDTO.getResourceFilter() != null) {
      resourceGroup.setResourceFilter(resourceGroupDTO.getResourceFilter());
    }

    return resourceGroup;
  }

  public static ResourceGroup fromDTO(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged) {
    ResourceGroup resourceGroup = fromDTO(resourceGroupDTO);
    resourceGroup.setHarnessManaged(harnessManaged);
    return resourceGroup;
  }

  public static ResourceGroupDTO toDTO(ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }
    return ResourceGroupDTO.builder()
        .accountIdentifier(resourceGroup.getAccountIdentifier())
        .orgIdentifier(resourceGroup.getOrgIdentifier())
        .projectIdentifier(resourceGroup.getProjectIdentifier())
        .identifier(resourceGroup.getIdentifier())
        .name(resourceGroup.getName())
        .color(resourceGroup.getColor())
        .tags(convertToMap(resourceGroup.getTags()))
        .description(resourceGroup.getDescription())
        .allowedScopeLevels(
            resourceGroup.getAllowedScopeLevels() == null ? new HashSet<>() : resourceGroup.getAllowedScopeLevels())
        .includedScopes(resourceGroup.getIncludedScopes())
        .resourceFilter(resourceGroup.getResourceFilter())
        .build();
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

  private boolean isValidV1ResourceGroup(Scope scopeOfResourceGroup, ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return true;
    }

    if (!isEmpty(resourceGroup.getIncludedScopes())) {
      ScopeSelector scope = resourceGroup.getIncludedScopes().get(0);
      if (resourceGroup.getIncludedScopes().size() > 1
          || !scopeOfResourceGroup.equals(
              Scope.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()))) {
        return false;
      }
    }
    return true;
  }
}
