/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins.jenkinsstep;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(PL)
public enum JenkinsParameterFieldType {
  @JsonProperty("String") STRING("String"),
  @JsonProperty("Number") NUMBER("Number");

  private final String displayName;
  JenkinsParameterFieldType(String displayName) {
    this.displayName = displayName;
  }

  @JsonIgnore
  public String getDisplayName() {
    return this.displayName;
  }
}
