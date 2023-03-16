/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDC)
public enum JiraAuthType {
  @JsonProperty(JiraConstants.USERNAME_PASSWORD) USER_PASSWORD(JiraConstants.USERNAME_PASSWORD),
  @JsonProperty(JiraConstants.PAT) PAT(JiraConstants.PAT);

  private final String displayName;

  JiraAuthType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }

  public static JiraAuthType fromString(String typeEnum) {
    for (JiraAuthType enumValue : JiraAuthType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid Jira Now auth type value: " + typeEnum);
  }
}
