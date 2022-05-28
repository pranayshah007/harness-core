/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.accesscontrol.scopes.core.Scope.PATH_DELIMITER;
import static io.harness.accesscontrol.scopes.core.Scope.SCOPE_DELIMITER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PL)
public class ResourceGroupFactory {
  public ResourceGroup buildResourceGroup(ResourceGroupResponse resourceGroupResponse, String scopeIdentifier) {
    ResourceGroupDTO resourceGroupDTO = resourceGroupResponse.getResourceGroup();
    Set<String> resourceSelectors = buildResourceSelector(resourceGroupDTO);
    return ResourceGroup.builder()
        .identifier(resourceGroupDTO.getIdentifier())
        .scopeIdentifier(scopeIdentifier)
        .name(resourceGroupDTO.getName())
        .resourceSelectors(resourceSelectors)
        .managed(resourceGroupResponse.isHarnessManaged())
        .allowedScopeLevels(resourceGroupDTO.getAllowedScopeLevels())
        .build();
  }

  public Scope getScope(ScopeSelector scopeSelector) {
    if (isNotEmpty(scopeSelector.getAccountIdentifier())) {
      return ScopeMapper.fromParams(HarnessScopeParams.builder()
                                        .accountIdentifier(scopeSelector.getAccountIdentifier())
                                        .orgIdentifier(scopeSelector.getOrgIdentifier())
                                        .projectIdentifier(scopeSelector.getProjectIdentifier())
                                        .build());
    }
    return null;
  }

  public ResourceGroup buildResourceGroup(ResourceGroupResponse resourceGroupResponse) {
    ResourceGroupDTO resourceGroupDTO = resourceGroupResponse.getResourceGroup();
    Scope scope = null;
    if (isNotEmpty(resourceGroupDTO.getAccountIdentifier())) {
      scope = ScopeMapper.fromParams(HarnessScopeParams.builder()
                                         .accountIdentifier(resourceGroupDTO.getAccountIdentifier())
                                         .orgIdentifier(resourceGroupDTO.getOrgIdentifier())
                                         .projectIdentifier(resourceGroupDTO.getProjectIdentifier())
                                         .build());
    }
    return buildResourceGroup(resourceGroupResponse, scope == null ? null : scope.toString());
  }

  public boolean addProjectResource(ScopeSelector scopeSelector) {
    if (ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())) {
      return true;
    } else if (ScopeFilterType.EXCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())
        && scopeSelector.getProjectIdentifier() != null) {
      return true;
    }
    return false;
  }

  public boolean addOrgResource(ScopeSelector scopeSelector) {
    if (scopeSelector.getProjectIdentifier() == null) {
      if (ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())) {
        return true;
      } else if (ScopeFilterType.EXCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())
          && scopeSelector.getOrgIdentifier() != null) {
        return true;
      }
    }
    return false;
  }

  public Set<String> getResourceSelector(ResourceSelector resourceSelector) {
    if (isEmpty(resourceSelector.getIdentifiers())) {
      return Collections.singleton(PATH_DELIMITER.concat(resourceSelector.getResourceType())
                                       .concat(PATH_DELIMITER)
                                       .concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER));
    } else {
      return resourceSelector.getIdentifiers()
          .stream()
          .map(identifier
              -> PATH_DELIMITER.concat(resourceSelector.getResourceType()).concat(PATH_DELIMITER).concat(identifier))
          .collect(Collectors.toSet());
    }
  }

  public Set<String> buildResourceSelector(ResourceGroupDTO resourceGroupDTO) {
    Set<String> resourceSelectors = new HashSet<>();
    Set<String> scopeSelectors = new HashSet<>();
    Set<String> selectors = new HashSet<>();
    ResourceFilter resourceFilter = resourceGroupDTO.getResourceFilter();
    List<ScopeSelector> includedScopes = resourceGroupDTO.getIncludedScopes();
    if (isEmpty(includedScopes) || resourceFilter == null) {
      return new HashSet<>();
    }

    includedScopes.stream().filter(Objects::nonNull).forEach(scopeSelector -> {
      Scope scope = getScope(scopeSelector);
      StringBuilder selector = new StringBuilder(scope == null ? "" : scope.toString().concat(SCOPE_DELIMITER));
      if (ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())) {
        selector.append(PATH_DELIMITER).append(ResourceGroup.INCLUDE_CHILD_SCOPES_IDENTIFIER);
      }
      scopeSelectors.add(selector.toString());
    });

    if (Boolean.TRUE.equals(resourceFilter.isIncludeAllResources())) {
      resourceSelectors.add(PATH_DELIMITER.concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER)
                                .concat(PATH_DELIMITER)
                                .concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER));
    } else {
      List<ResourceSelector> resources = resourceFilter.getResources();
      resources.forEach(resourceSelector -> { resourceSelectors.addAll(getResourceSelector(resourceSelector)); });
    }

    includedScopes.stream().filter(Objects::nonNull).forEach(scopeSelector -> {
      Scope scope = getScope(scopeSelector);
      Set<String> modifiedResourceSelectors = new HashSet<>(resourceSelectors);

      StringBuilder selector = new StringBuilder(scope == null ? "" : scope.toString().concat(SCOPE_DELIMITER));
      if (ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())) {
        selector.append(PATH_DELIMITER).append(ResourceGroup.INCLUDE_CHILD_SCOPES_IDENTIFIER);
      }
      if (Boolean.FALSE.equals(resourceFilter.isIncludeAllResources())) {
        if (addOrgResource(scopeSelector)) {
          modifiedResourceSelectors.addAll(
              getResourceSelector(ResourceSelector.builder().resourceType("ORGANIZATION").build()));
        }
        if (addProjectResource(scopeSelector)) {
          modifiedResourceSelectors.addAll(
              getResourceSelector(ResourceSelector.builder().resourceType("PROJECT").build()));
        }
      }
      modifiedResourceSelectors.forEach(
          resourceSelector -> { selectors.add(selector.toString().concat(resourceSelector)); });
    });

    scopeSelectors.forEach(scopeSelector -> {
      resourceSelectors.forEach(resourceSelector -> { selectors.add(scopeSelector.concat(resourceSelector)); });
    });

    return selectors;
  }
}
