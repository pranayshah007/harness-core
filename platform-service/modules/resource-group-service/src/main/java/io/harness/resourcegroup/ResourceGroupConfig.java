/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ScopeSelector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
public class ResourceGroupConfig {
  @NotNull @Size(max = 128) @NotEmpty String name;
  @NotNull @Size(max = 128) @NotEmpty String identifier;
  @Size(max = 1024) String description;
  @Size(max = 128) Map<String, String> tags = new HashMap<>();
  @Size(max = 256) @Valid List<ScopeSelector> includedScopes = new ArrayList<>();
  ResourceFilter resourceFilter;
  boolean fullScopeSelected;
  @NotEmpty Set<String> allowedScopeLevels = new HashSet<>();

  @Builder
  public ResourceGroupConfig(String name, String identifier, String description, Map<String, String> tags,
      List<ScopeSelector> includedScopes, ResourceFilter resourceFilter, boolean fullScopeSelected,
      Set<String> allowedScopeLevels) {
    this.name = name;
    this.identifier = identifier;
    this.description = description;
    this.tags = tags == null ? new HashMap<>() : tags;
    this.includedScopes = includedScopes == null ? new ArrayList<>() : includedScopes;
    this.resourceFilter = resourceFilter;
    this.fullScopeSelected = fullScopeSelected;
    this.allowedScopeLevels = allowedScopeLevels == null ? new HashSet<>() : allowedScopeLevels;
  }
}
