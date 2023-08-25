/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LoadBalancerType {
  @JsonProperty(LoadBalancerKind.AWS_LOAD_BALANCER_CONFIG)
  AWS_LOAD_BALANCER_CONFIG(LoadBalancerKind.AWS_LOAD_BALANCER_CONFIG),
  @JsonProperty(LoadBalancerKind.AWS_ASG_LOAD_BALANCER_CONFIG)
  AWS_ASG_LOAD_BALANCER_CONFIG(LoadBalancerKind.AWS_ASG_LOAD_BALANCER_CONFIG);

  private final String displayName;

  LoadBalancerType(String displayName) {
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
