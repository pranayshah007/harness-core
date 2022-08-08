/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.remote.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.validator.ValidResourceGroupFilter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Schema(name = "ResourceGroupFilter", description = "Contains information of filters for Resource Group")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ValidResourceGroupFilter
public class ResourceGroupFilterDTO {
  @Schema(description = "Filter by account identifier", required = true)
  @ApiModelProperty(required = true)
  String accountIdentifier;
  @Schema(description = "Filter by organization identifier") String orgIdentifier;
  @Schema(description = "Filter by project identifier") String projectIdentifier;
  @Schema(description = "Filter resource group matching by identifier/name") String searchTerm;
  @Schema(description = "Filter by resource group identifiers") Set<String> identifierFilter;
  @JsonIgnore @Schema(description = "Filter by scopeLevel") Set<String> scopeLevelFilter;
  @Schema(description = "Filter based on whether it has a particular resource")
  Set<ResourceSelectorFilter> resourceSelectorFilterList;
  @Schema(description = "Filter based on whether the resource group is Harness managed") ManagedFilter managedFilter;

  @Builder
  public ResourceGroupFilterDTO(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, Set<String> identifierFilter, ManagedFilter managedFilter,
      Set<ResourceSelectorFilter> resourceSelectorFilterList) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.searchTerm = searchTerm;
    this.identifierFilter = identifierFilter == null ? new HashSet<>() : identifierFilter;
    this.managedFilter = managedFilter == null ? ManagedFilter.NO_FILTER : managedFilter;
    this.resourceSelectorFilterList = resourceSelectorFilterList == null ? new HashSet<>() : resourceSelectorFilterList;
  }
}
