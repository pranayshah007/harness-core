/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDP)
public enum ServiceHookType {
  @JsonProperty(ServiceHookConstants.PRE_HOOK) PRE_HOOK(ServiceHookConstants.PRE_HOOK),
  @JsonProperty(ServiceHookConstants.POST_HOOK) POST_HOOK(ServiceHookConstants.POST_HOOK);

  private final String displayName;

  ServiceHookType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ServiceHookType getHookType(@JsonProperty("type") String displayName) {
    for (ServiceHookType serviceHookType : ServiceHookType.values()) {
      if (serviceHookType.displayName.equalsIgnoreCase(displayName)) {
        return serviceHookType;
      }
    }

    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", displayName, Arrays.toString(ServiceHookType.values())));
  }
}
