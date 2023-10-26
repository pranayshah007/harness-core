/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HarnessTeam.CDC)
public enum FreezeEventType {
  @JsonProperty(FreezeEventTypeConstants.FREEZE_WINDOW_ENABLED)
  FREEZE_WINDOW_ENABLED("Freeze", FreezeEventTypeConstants.FREEZE_WINDOW_ENABLED),
  @JsonProperty(FreezeEventTypeConstants.DEPLOYMENT_REJECTED_DUE_TO_FREEZE)
  DEPLOYMENT_REJECTED_DUE_TO_FREEZE("Freeze", FreezeEventTypeConstants.DEPLOYMENT_REJECTED_DUE_TO_FREEZE),
  @JsonProperty(FreezeEventTypeConstants.ON_ENABLE_FREEZE_WINDOW)
  ON_ENABLE_FREEZE_WINDOW("Freeze", FreezeEventTypeConstants.ON_ENABLE_FREEZE_WINDOW);

  private String level;
  private String displayName;

  FreezeEventType(String level, String displayName) {
    this.level = level;
    this.displayName = displayName;
  }

  public String getLevel() {
    return level;
  }

  public String getDisplayName() {
    return displayName;
  }
}
