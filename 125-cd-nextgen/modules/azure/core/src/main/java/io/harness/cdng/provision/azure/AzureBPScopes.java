/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor
public enum AzureBPScopes {
  @JsonProperty("Subscription") SUBSCRIPTION("Subscription"),
  @JsonProperty("ManagementGroup") MANAGEMENT_GROUP("ManagementGroup");

  private final String scope;

  @JsonValue
  public String getValue() {
    return scope;
  }

  @Override
  public String toString() {
    return this.scope;
  }
}
