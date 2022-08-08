/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model.blueprint;

import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_MNG_GROUP_PATTERN;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_SUBSCRIPTION_PATTERN;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceScopeType {
  MANAGEMENT_GROUP("managementGroup"),
  SUBSCRIPTION("subscriptions");

  private final String value;

  ResourceScopeType(String value) {
    this.value = value;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ResourceScopeType fromString(final String value) {
    ResourceScopeType[] items = ResourceScopeType.values();
    for (ResourceScopeType item : items) {
      if (item.toString().equalsIgnoreCase(value)) {
        return item;
      }
    }
    return null;
  }

  public static ResourceScopeType fromBlueprintId(final String value) {
    if (value == null) {
      return null;
    }
    if (value.startsWith(RESOURCE_SCOPE_SUBSCRIPTION_PATTERN)) {
      return SUBSCRIPTION;
    } else if (value.startsWith(RESOURCE_SCOPE_MNG_GROUP_PATTERN)) {
      return MANAGEMENT_GROUP;
    } else {
      return null;
    }
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
