/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDC)
public enum ServiceNowAuthType {
  @JsonProperty(ServiceNowConstants.USERNAME_PASSWORD) USER_PASSWORD(ServiceNowConstants.USERNAME_PASSWORD),
  @JsonProperty(ServiceNowConstants.ADFS) ADFS(ServiceNowConstants.ADFS),
  @JsonProperty(ServiceNowConstants.REFRESH_TOKEN) REFRESH_TOKEN(ServiceNowConstants.REFRESH_TOKEN);

  private final String displayName;

  ServiceNowAuthType(String displayName) {
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

  public static ServiceNowAuthType fromString(String typeEnum) {
    for (ServiceNowAuthType enumValue : ServiceNowAuthType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid Service Now auth type value: " + typeEnum);
  }
}
