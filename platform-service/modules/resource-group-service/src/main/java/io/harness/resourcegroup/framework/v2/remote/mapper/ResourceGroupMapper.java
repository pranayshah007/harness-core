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
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.model.DynamicResourceSelector;
import io.harness.resourcegroup.model.ResourceSelector;
import io.harness.resourcegroup.model.ResourceSelectorByScope;
import io.harness.resourcegroup.model.StaticResourceSelector;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
  public static io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO toV1DTO(
      ResourceGroup resourceGroup, boolean harnessManaged) {
    resourceGroup.setHarnessManaged(harnessManaged);
    return io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.toDTO(
        ResourceGroupMapper.toV1(resourceGroup));
  }

  public static io.harness.resourcegroup.v1.model.ResourceGroup toV1(ResourceGroup resourceGroup) {
    List<ResourceSelector> resourceSelectors = new ArrayList<>();
    Scope scopeOfResourceGroup = Scope.of(
        resourceGroup.getAccountIdentifier(), resourceGroup.getOrgIdentifier(), resourceGroup.getProjectIdentifier());
    if (!isValidV1ResourceGroup(scopeOfResourceGroup, resourceGroup)) {
      return null;
    }
    AtomicBoolean includeChildScopes = new AtomicBoolean(false);
    if (!isEmpty(resourceGroup.getIncludedScopes())
        && ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(resourceGroup.getIncludedScopes().get(0).getFilter())) {
      includeChildScopes.set(true);
    }

    if (resourceGroup.getResourceFilter() != null) {
      if (resourceGroup.getResourceFilter().isIncludeAllResources()) {
        resourceSelectors.add(ResourceSelectorByScope.builder()
                                  .includeChildScopes(includeChildScopes.get())
                                  .scope(resourceGroup.getAccountIdentifier() != null ? scopeOfResourceGroup : null)
                                  .build());
      } else if (!isEmpty(resourceGroup.getResourceFilter().getResources())) {
        resourceGroup.getResourceFilter().getResources().forEach(resources -> {
          if (resources != null && resources.getResourceType() != null) {
            if (isEmpty(resources.getIdentifiers())) {
              resourceSelectors.add(DynamicResourceSelector.builder()
                                        .resourceType(resources.getResourceType())
                                        .includeChildScopes(includeChildScopes.get())
                                        .build());
            } else {
              resourceSelectors.add(StaticResourceSelector.builder()
                                        .resourceType(resources.getResourceType())
                                        .identifiers(resources.getIdentifiers())
                                        .build());
            }
          }
        });
      }
    }

    return io.harness.resourcegroup.v1.model.ResourceGroup.builder()
        .id(resourceGroup.getId())
        .accountIdentifier(resourceGroup.getAccountIdentifier())
        .orgIdentifier(resourceGroup.getOrgIdentifier())
        .projectIdentifier(resourceGroup.getProjectIdentifier())
        .identifier(resourceGroup.getIdentifier())
        .name(resourceGroup.getName())
        .color(isBlank(resourceGroup.getColor()) ? HARNESS_BLUE : resourceGroup.getColor())
        .tags(resourceGroup.getTags())
        .description(resourceGroup.getDescription())
        .harnessManaged(resourceGroup.getHarnessManaged())
        .allowedScopeLevels(
            resourceGroup.getAllowedScopeLevels() == null ? new HashSet<>() : resourceGroup.getAllowedScopeLevels())
        .resourceSelectors(resourceSelectors)
        .fullScopeSelected(resourceGroup.getHarnessManaged() && !includeChildScopes.get())
        .build();
  }

  public static ResourceGroup fromV1(io.harness.resourcegroup.v1.model.ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }

    ResourceGroup resourceGroupV2 =
        ResourceGroup.builder()
            .id(resourceGroup.getId())
            .accountIdentifier(resourceGroup.getAccountIdentifier())
            .orgIdentifier(resourceGroup.getOrgIdentifier())
            .projectIdentifier(resourceGroup.getProjectIdentifier())
            .identifier(resourceGroup.getIdentifier())
            .name(resourceGroup.getName())
            .color(isBlank(resourceGroup.getColor()) ? HARNESS_BLUE : resourceGroup.getColor())
            .tags(resourceGroup.getTags())
            .description(resourceGroup.getDescription())
            .harnessManaged(resourceGroup.getHarnessManaged())
            .allowedScopeLevels(
                resourceGroup.getAllowedScopeLevels() == null ? new HashSet<>() : resourceGroup.getAllowedScopeLevels())
            .build();

    return setScopeAndResourceFilter(resourceGroup, resourceGroupV2);
  }

  public ResourceGroup setScopeAndResourceFilter(
      io.harness.resourcegroup.v1.model.ResourceGroup resourceGroup, ResourceGroup resourceGroupV2) {
    List<io.harness.resourcegroup.v2.model.ResourceSelector> resources = new ArrayList<>();
    List<ScopeSelector> includedScopes = new ArrayList<>();

    AtomicBoolean includeChildScopes = new AtomicBoolean(false);
    AtomicBoolean includeAllResources = new AtomicBoolean(false);

    for (Iterator<ResourceSelector> iterator = resourceGroup.getResourceSelectors().iterator(); iterator.hasNext();) {
      ResourceSelector resourceSelector = iterator.next();
      if (resourceSelector instanceof StaticResourceSelector
          && !isEmpty(((StaticResourceSelector) resourceSelector).getIdentifiers())) {
        resources.add(io.harness.resourcegroup.v2.model.ResourceSelector.builder()
                          .resourceType(((StaticResourceSelector) resourceSelector).getResourceType())
                          .identifiers(((StaticResourceSelector) resourceSelector).getIdentifiers())
                          .build());
      } else if (resourceSelector instanceof DynamicResourceSelector) {
        resources.add(io.harness.resourcegroup.v2.model.ResourceSelector.builder()
                          .resourceType(((DynamicResourceSelector) resourceSelector).getResourceType())
                          .build());
        if (Boolean.TRUE.equals(((DynamicResourceSelector) resourceSelector).getIncludeChildScopes())) {
          includeChildScopes.set(true);
        }
      } else if (resourceSelector instanceof ResourceSelectorByScope) {
        includeAllResources.set(true);
        if (Boolean.TRUE.equals(((ResourceSelectorByScope) resourceSelector).isIncludeChildScopes())) {
          includeChildScopes.set(true);
        }
      }
    }

    includedScopes.add(ScopeSelector.builder()
                           .accountIdentifier(resourceGroup.getAccountIdentifier())
                           .orgIdentifier(resourceGroup.getOrgIdentifier())
                           .projectIdentifier(resourceGroup.getProjectIdentifier())
                           .filter(includeChildScopes.get() ? ScopeFilterType.INCLUDING_CHILD_SCOPES
                                                            : ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                           .build());

    resourceGroupV2.setIncludedScopes(includedScopes);
    resourceGroupV2.setResourceFilter(
        ResourceFilter.builder().includeAllResources(includeAllResources.get()).resources(resources).build());
    return resourceGroupV2;
  }

  public static ResourceGroupDTO fromV1DTO(
      io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO resourceGroup, boolean harnessManaged) {
    io.harness.resourcegroup.v1.model.ResourceGroup resourceGroupV1 =
        io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroup);
    resourceGroupV1.setHarnessManaged(harnessManaged);
    return toDTO(fromV1(resourceGroupV1));
  }

  public static io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO toV1DTO(
      ResourceGroupDTO resourceGroup, boolean harnessManaged) {
    return toV1DTO(fromDTO(resourceGroup), harnessManaged);
  }

  public static io.harness.resourcegroup.v1.remote.dto.ResourceGroupResponse toV1Response(
      ResourceGroupResponse response) {
    if (response == null) {
      return null;
    }
    return io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.toResponseWrapper(
        toV1(fromDTO(response.getResourceGroup(), response.isHarnessManaged())));
  }

  public static ResourceGroupResponse toV2Response(
      io.harness.resourcegroup.v1.remote.dto.ResourceGroupResponse response) {
    if (response == null) {
      return null;
    }
    return toResponseWrapper(fromDTO(fromV1DTO(response.getResourceGroup(), response.isHarnessManaged())));
  }
}
