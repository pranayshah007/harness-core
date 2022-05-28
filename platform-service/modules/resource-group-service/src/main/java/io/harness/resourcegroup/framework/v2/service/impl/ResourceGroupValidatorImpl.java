/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupValidator;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.utils.ScopeUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourceGroupValidatorImpl implements ResourceGroupValidator {
  Map<String, Resource> resourceMap;

  private boolean isCurrentOnlyScopeSelector(Scope scope, ScopeSelector scopeSelector) {
    Scope scopeOfSelector = Scope.of(
        scopeSelector.getAccountIdentifier(), scopeSelector.getOrgIdentifier(), scopeSelector.getProjectIdentifier());
    if (scope.equals(scopeOfSelector) && ScopeFilterType.EXCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())) {
      return true;
    }
    return false;
  }
  private boolean sanitizeStaticResourceSelector(Scope scope, ResourceSelector resourceSelector) {
    String resourceType = resourceSelector.getResourceType();
    ScopeLevel scopeLevel =
        ScopeLevel.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());

    Resource resource = resourceMap.get(resourceType);
    if (resource == null || !resource.getValidScopeLevels().contains(scopeLevel)
        || !resource.getSelectorKind().containsKey(scopeLevel)
        || !resource.getSelectorKind().get(scopeLevel).contains(BY_RESOURCE_IDENTIFIER)) {
      resourceSelector.getIdentifiers().clear();
      return true;
    }

    List<String> resourceIds = resourceSelector.getIdentifiers();
    List<Boolean> validationResult = resource.validate(resourceIds, scope);
    List<String> validResourceIds = IntStream.range(0, resourceIds.size())
                                        .filter(validationResult::get)
                                        .mapToObj(resourceIds::get)
                                        .collect(Collectors.toList());
    resourceSelector.setIdentifiers(validResourceIds);

    return resourceIds.isEmpty() || (resourceIds.size() != validResourceIds.size());
  }

  private boolean isValidDynamicResourceSelector(
      Scope scope, ResourceSelector resourceSelector, boolean includeChildScopes) {
    String resourceType = resourceSelector.getResourceType();
    ScopeLevel scopeLevel =
        ScopeLevel.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());

    return resourceMap.containsKey(resourceType)
        && resourceMap.get(resourceType).getValidScopeLevels().contains(scopeLevel)
        && resourceMap.get(resourceType).getSelectorKind().containsKey(scopeLevel)
        && (Boolean.TRUE.equals(includeChildScopes)
                ? resourceMap.get(resourceType)
                      .getSelectorKind()
                      .get(scopeLevel)
                      .contains(BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES)
                : resourceMap.get(resourceType).getSelectorKind().get(scopeLevel).contains(BY_RESOURCE_TYPE));
  }

  public boolean sanitizeResourceSelectors(ResourceGroup resourceGroup) {
    if (resourceGroup.getResourceFilter() == null) {
      return false;
    }
    if (Boolean.TRUE.equals(resourceGroup.getResourceFilter().isIncludeAllResources())) {
      return false;
    }
    boolean includeChildScopes = false;
    Scope scope = Scope.builder()
                      .accountIdentifier(resourceGroup.getAccountIdentifier())
                      .orgIdentifier(resourceGroup.getOrgIdentifier())
                      .projectIdentifier(resourceGroup.getProjectIdentifier())
                      .build();

    if (!isEmpty(resourceGroup.getIncludedScopes())) {
      for (ScopeSelector includedScope : resourceGroup.getIncludedScopes()) {
        if (!isCurrentOnlyScopeSelector(scope, includedScope)) {
          includeChildScopes = true;
          break;
        }
      }
    }

    boolean updated = false;

    List<ResourceSelector> newResourceSelectors = new ArrayList<>(resourceGroup.getResourceFilter().getResources());
    for (Iterator<ResourceSelector> iterator = newResourceSelectors.iterator(); iterator.hasNext();) {
      ResourceSelector resourceSelector = iterator.next();
      if (isEmpty(resourceSelector.getIdentifiers())) {
        if (!isValidDynamicResourceSelector(scope, resourceSelector, includeChildScopes)) {
          iterator.remove();
          updated = true;
        }
      } else {
        updated = updated || sanitizeStaticResourceSelector(scope, resourceSelector);
        if (updated && resourceSelector.getIdentifiers().isEmpty()) {
          iterator.remove();
        }
      }
    }
    ResourceFilter resourceFilter = resourceGroup.getResourceFilter();
    resourceFilter.setResources(newResourceSelectors);
    return updated;
  }

  private void validateResourceFilter(ResourceFilter resourceFilter, boolean includeStaticResources) {
    if (resourceFilter == null) {
      return;
    }
    if (resourceFilter.isIncludeAllResources() && !isEmpty(resourceFilter.getResources())) {
      throw new InvalidRequestException("Cannot provide specific resources when you include all resources");
    }
    if (!isEmpty(resourceFilter.getResources())) {
      resourceFilter.getResources().forEach(filter -> {
        if (!includeStaticResources && !isEmpty(filter.getIdentifiers())) {
          throw new InvalidRequestException(
              "Cannot provide specific identifiers in resource filter for a dynamic scope");
        }
      });
    }
  }

  public void validateResourceGroup(ResourceGroupRequest resourceGroupRequest) {
    if (resourceGroupRequest == null || resourceGroupRequest.getResourceGroup() == null) {
      return;
    }
    ResourceGroupDTO resourceGroupDTO = resourceGroupRequest.getResourceGroup();
    Scope scopeOfResourceGroup = Scope.of(resourceGroupDTO.getAccountIdentifier(), resourceGroupDTO.getOrgIdentifier(),
        resourceGroupDTO.getProjectIdentifier());
    AtomicBoolean includeStaticResources = new AtomicBoolean(false);
    ResourceFilter resourceFilter = resourceGroupDTO.getResourceFilter();

    if (ScopeUtils.isAccountScope(scopeOfResourceGroup)) {
      resourceGroupDTO.getIncludedScopes().forEach(scope -> {
        includeStaticResources.set(isCurrentOnlyScopeSelector(scopeOfResourceGroup, scope));
        if (!scope.getAccountIdentifier().equals(scopeOfResourceGroup.getAccountIdentifier())) {
          throw new InvalidRequestException("Scope of included scopes does not match with the scope of resource group");
        }
      });
    } else if (ScopeUtils.isOrganizationScope(scopeOfResourceGroup)) {
      resourceGroupDTO.getIncludedScopes().forEach(scope -> {
        includeStaticResources.set(isCurrentOnlyScopeSelector(scopeOfResourceGroup, scope));
        if (!(scopeOfResourceGroup.getAccountIdentifier().equals(scope.getAccountIdentifier())
                && scopeOfResourceGroup.getOrgIdentifier().equals(scope.getOrgIdentifier()))) {
          throw new InvalidRequestException("Scope of included scopes does not match with the scope of resource group");
        }
      });
    } else if (ScopeUtils.isProjectScope(scopeOfResourceGroup)) {
      includeStaticResources.set(true);
      resourceGroupDTO.getIncludedScopes().forEach(scope -> {
        if (!scopeOfResourceGroup.equals(
                Scope.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()))) {
          throw new InvalidRequestException("Scope of included scopes does not match with the scope of resource group");
        }
      });
    } else {
      throw new InvalidRequestException("Invalid scope of resource group");
    }

    validateResourceFilter(resourceFilter, includeStaticResources.get());
  }
}
