/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CDP)
public enum EcsResizeStrategy {
  @JsonProperty(EcsConstants.RESIZE_NEW_FIRST) RESIZE_NEW_FIRST(EcsConstants.RESIZE_NEW_FIRST),
  @JsonProperty(EcsConstants.DOWNSIZE_OLD_FIRST) DOWNSIZE_OLD_FIRST(EcsConstants.DOWNSIZE_OLD_FIRST);

  private final String displayName;
  EcsResizeStrategy(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
