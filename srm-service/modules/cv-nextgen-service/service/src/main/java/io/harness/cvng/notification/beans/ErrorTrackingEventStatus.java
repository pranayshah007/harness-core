/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ErrorTrackingEventStatus {
  @JsonProperty("NewEvents") NEW_EVENTS("New Events"),
  @JsonProperty("CriticalEvents") CRITICAL_EVENTS("Critical Events"),
  @JsonProperty("ResurfacedEvents") RESURFACED_EVENTS("Resurfaced Events");

  private final String displayName;

  ErrorTrackingEventStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return this.displayName;
  }
}