/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.remote.dto;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.resourcegroup.model.DynamicResourceSelector;
import io.harness.resourcegroup.model.ResourceSelector;
import io.harness.resourcegroup.model.ResourceSelectorByScope;
import io.harness.resourcegroup.model.StaticResourceSelector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ResourceGroup", description = "Contains information of Resource Group")
@OwnedBy(HarnessTeam.PL)
public class ResourceGroupDTO {
  @ApiModelProperty(required = true) @NotNull @NotEmpty String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  @EntityIdentifier @ApiModelProperty(required = true) @NotNull @Size(max = 128) @NotEmpty String identifier;
  @NGEntityName @ApiModelProperty(required = true) @NotNull @Size(max = 128) @NotEmpty String name;
  @Size(max = 256) @Valid List<ResourceSelector> resourceSelectors;
  boolean fullScopeSelected;
  @Size(max = 128) Map<String, String> tags;
  @Size(max = 1024) String description;
  Set<String> allowedScopeLevels;
  String color;

  public Scope getScope() {
    return Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  public void sanitizeResourceSelectors() {
    if (getResourceSelectors() == null) {
      return;
    }
    List<ResourceSelector> sanitizedResourceSelectors = new ArrayList<>();
    List<ResourceSelector> sanitizedDynamicResourceSelectors = getResourceSelectors()
                                                                   .stream()
                                                                   .filter(DynamicResourceSelector.class ::isInstance)
                                                                   .map(DynamicResourceSelector.class ::cast)
                                                                   .distinct()
                                                                   .collect(toList());

    List<ResourceSelector> sanitizedResourceSelectorsByScope = getResourceSelectors()
                                                                   .stream()
                                                                   .filter(ResourceSelectorByScope.class ::isInstance)
                                                                   .map(ResourceSelectorByScope.class ::cast)
                                                                   .distinct()
                                                                   .collect(toList());

    sanitizedResourceSelectors.addAll(sanitizedDynamicResourceSelectors);
    sanitizedResourceSelectors.addAll(sanitizedResourceSelectorsByScope);

    Map<String, List<String>> staticResources =
        getResourceSelectors()
            .stream()
            .filter(StaticResourceSelector.class ::isInstance)
            .map(StaticResourceSelector.class ::cast)
            .collect(toMap(StaticResourceSelector::getResourceType, StaticResourceSelector::getIdentifiers,
                (oldResourceIds, newResourceIds) -> {
                  oldResourceIds.addAll(newResourceIds);
                  return oldResourceIds.stream().distinct().collect(toList());
                }));

    staticResources.forEach(
        (resourceType, identifiers)
            -> sanitizedResourceSelectors.add(
                StaticResourceSelector.builder().resourceType(resourceType).identifiers(identifiers).build()));

    resourceSelectors = sanitizedResourceSelectors;
  }
}
