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
import io.harness.resourcegroup.v2.model.AttributeFilter;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PL)
public class ResourceGroupFactory {
  private final String CONDITIONAL_EXPRESSION_TEMPLATE = "<+resource.attribute.%s> =~ [%s]";

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

  ResourceGroup buildResourceGroup(ResourceGroupResponse resourceGroupResponse, String scopeIdentifier) {
    ResourceGroupDTO resourceGroupDTO = resourceGroupResponse.getResourceGroup();
    Set<ResourceSelector> resourceSelectors = buildResourceSelector(resourceGroupDTO);
    return ResourceGroup.builder()
        .identifier(resourceGroupDTO.getIdentifier())
        .scopeIdentifier(scopeIdentifier)
        .name(resourceGroupDTO.getName())
        .resourceSelectors(resourceSelectors.stream()
                               .filter(resourceSelector -> !resourceSelector.isConditional())
                               .map(ResourceSelector::getSelector)
                               .collect(Collectors.toSet()))
        .resourceSelectorsV2(resourceSelectors)
        .scopeSelectors(
            resourceGroupDTO.getIncludedScopes().stream().map(this::buildScopeSelector).collect(Collectors.toSet()))
        .managed(resourceGroupResponse.isHarnessManaged())
        .allowedScopeLevels(resourceGroupDTO.getAllowedScopeLevels())
        .build();
  }

  private io.harness.accesscontrol.resources.resourcegroups.ScopeSelector buildScopeSelector(
      ScopeSelector scopeSelector) {
    Scope scope = getScope(scopeSelector);
    return io.harness.accesscontrol.resources.resourcegroups.ScopeSelector.builder()
        .scopeIdentifier(scope == null ? null : scope.toString())
        .includingChildScopes(ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter()))
        .build();
  }

  private Scope getScope(ScopeSelector scopeSelector) {
    if (isNotEmpty(scopeSelector.getAccountIdentifier())) {
      return ScopeMapper.fromParams(HarnessScopeParams.builder()
                                        .accountIdentifier(scopeSelector.getAccountIdentifier())
                                        .orgIdentifier(scopeSelector.getOrgIdentifier())
                                        .projectIdentifier(scopeSelector.getProjectIdentifier())
                                        .build());
    }
    return null;
  }

  private Set<ResourceSelector> getResourceSelectors(
      io.harness.resourcegroup.v2.model.ResourceSelector resourceSelector) {
    if (isEmpty(resourceSelector.getIdentifiers())) {
      String selector = PATH_DELIMITER.concat(resourceSelector.getResourceType())
                            .concat(PATH_DELIMITER)
                            .concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER);
      Optional<String> condition = getCondition(resourceSelector.getAttributeFilter());
      return Collections.singleton(ResourceSelector.builder()
                                       .selector(selector)
                                       .conditional(condition.isPresent())
                                       .condition(condition.orElse(null))
                                       .build());
    } else {
      return resourceSelector.getIdentifiers()
          .stream()
          .map(identifier
              -> ResourceSelector.builder()
                     .selector(PATH_DELIMITER.concat(resourceSelector.getResourceType())
                                   .concat(PATH_DELIMITER)
                                   .concat(identifier))
                     .build())
          .collect(Collectors.toSet());
    }
  }

  private Optional<String> getCondition(AttributeFilter attributeFilter) {
    if (attributeFilter == null || isEmpty(attributeFilter.getAttributeValues())) {
      return Optional.empty();
    }
    return Optional.of(String.format(CONDITIONAL_EXPRESSION_TEMPLATE, attributeFilter.getAttributeName(),
        convertToString(attributeFilter.getAttributeValues())));
  }

  private String convertToString(List<String> attributeValues) {
    return attributeValues.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(","));
  }

  Set<ResourceSelector> buildResourceSelector(ResourceGroupDTO resourceGroupDTO) {
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    Set<ResourceSelector> selectors = new HashSet<>();
    ResourceFilter resourceFilter = resourceGroupDTO.getResourceFilter();
    List<ScopeSelector> includedScopes = resourceGroupDTO.getIncludedScopes();
    if (isEmpty(includedScopes) || resourceFilter == null
        || (!resourceFilter.isIncludeAllResources() && isEmpty(resourceFilter.getResources()))) {
      return new HashSet<>();
    }

    if (Boolean.TRUE.equals(resourceFilter.isIncludeAllResources())) {
      resourceSelectors.add(ResourceSelector.builder()
                                .selector(PATH_DELIMITER.concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER)
                                              .concat(PATH_DELIMITER)
                                              .concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER))
                                .build());
    } else {
      resourceFilter.getResources().forEach(
          resourceSelector -> { resourceSelectors.addAll(getResourceSelectors(resourceSelector)); });
    }

    includedScopes.stream().filter(Objects::nonNull).forEach(scopeSelector -> {
      Scope scope = getScope(scopeSelector);
      StringBuilder selector = new StringBuilder(scope == null ? "" : scope.toString().concat(SCOPE_DELIMITER));
      if (ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scopeSelector.getFilter())) {
        selector.append(PATH_DELIMITER).append(ResourceGroup.INCLUDE_CHILD_SCOPES_IDENTIFIER);
      }
      resourceSelectors.forEach(resourceSelector
          -> selectors.add(ResourceSelector.builder()
                               .selector(selector.toString().concat(resourceSelector.getSelector()))
                               .conditional(resourceSelector.isConditional())
                               .condition(resourceSelector.getCondition())
                               .build()));
    });
    return selectors;
  }
}
