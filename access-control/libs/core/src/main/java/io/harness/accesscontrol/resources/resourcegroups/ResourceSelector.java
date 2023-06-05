/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.accesscontrol.resources.resourcegroups.ResourceGroup.ALL_RESOURCES_IDENTIFIER;
import static io.harness.accesscontrol.scopes.core.Scope.PATH_DELIMITER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ResourceSelectorKeys")
@OwnedBy(HarnessTeam.PL)
public class ResourceSelector {
  @NotEmpty String selector;
  boolean conditional;
  String condition;

  public static boolean validateResourceType(String resourceType, String resourceSelector) {
    String[] split = resourceSelector.split(PATH_DELIMITER);
    String resourceTypeFromSelector = split[split.length - 2];

    return ALL_RESOURCES_IDENTIFIER.equals(resourceTypeFromSelector)
        || resourceTypeFromSelector.equalsIgnoreCase(resourceType);
  }
}
