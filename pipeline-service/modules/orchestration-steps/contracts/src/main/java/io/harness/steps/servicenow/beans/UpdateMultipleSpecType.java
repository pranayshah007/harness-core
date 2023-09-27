/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.servicenow.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(PIPELINE)
public enum UpdateMultipleSpecType {
  @JsonProperty(UpdateMultipleSpecTypeConstants.CHANGE_TASK) CHANGE_TASK(UpdateMultipleSpecTypeConstants.CHANGE_TASK);
  private final String displayName;

  UpdateMultipleSpecType(String displayName) {
    this.displayName = displayName;
  }
  @Override
  public String toString() {
    return displayName;
  }
}
