/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ResourceGroup {
  public static final String ALL_RESOURCES_IDENTIFIER = "*";
  public static final String INCLUDE_CHILD_SCOPES_IDENTIFIER = "**";

  final String scopeIdentifier;
  @NotEmpty final String identifier;
  @NotEmpty final String name;
  final Set<String> allowedScopeLevels;
  @NotNull final Set<String> resourceSelectors;
  @NotNull final Set<ResourceSelector> resourceSelectorsV2;
  @NotNull final Set<ScopeSelector> scopeSelectors;
  final boolean managed;
  @EqualsAndHashCode.Exclude @Setter Long createdAt;
  @EqualsAndHashCode.Exclude @Setter Long lastModifiedAt;
  @EqualsAndHashCode.Exclude @Setter Long version;

  public static Set<ResourceSelector> getDiffOfResourceSelectors(
      ResourceGroup resourceGroup1, ResourceGroup resourceGroup2) {
    Set<ResourceSelector> resourceSelectors1 = getAllResourceSelectors(resourceGroup1);
    Set<ResourceSelector> resourceSelectors2 = getAllResourceSelectors(resourceGroup2);
    return Sets.difference(resourceSelectors1, resourceSelectors2);
  }

  private static Set<ResourceSelector> getAllResourceSelectors(ResourceGroup resourceGroup) {
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    if (resourceGroup.getResourceSelectors() != null) {
      resourceSelectors.addAll(resourceGroup.getResourceSelectors()
                                   .stream()
                                   .map(selector -> ResourceSelector.builder().selector(selector).build())
                                   .collect(Collectors.toList()));
    }
    if (resourceGroup.getResourceSelectorsV2() != null) {
      resourceSelectors.addAll(resourceGroup.getResourceSelectorsV2());
    }
    return resourceSelectors;
  }
}
