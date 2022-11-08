/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ElastigroupInstancesType {
  @JsonProperty(ElastigroupInstancesKind.FIXED) FIXED(ElastigroupInstancesKind.FIXED),
  @JsonProperty(ElastigroupInstancesKind.CURRENT_RUNNING) CURRENT_RUNNING(ElastigroupInstancesKind.CURRENT_RUNNING);

  private final String displayName;

  ElastigroupInstancesType(String displayName) {
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
}
