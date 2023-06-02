/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model.kubeconfig;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@OwnedBy(HarnessTeam.CDP)
public enum InteractiveMode {
  @JsonProperty("Never") NEVER("Never"),
  @JsonProperty("IfAvailable") IF_AVAILABLE("IfAvailable"),
  @JsonProperty("Always") ALWAYS("Always");

  @Getter private final String name;

  InteractiveMode(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return this.name;
  }
}
