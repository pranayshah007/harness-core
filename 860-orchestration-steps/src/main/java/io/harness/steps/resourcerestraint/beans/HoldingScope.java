/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;

@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.resourcerestraint.beans.HoldingScope")
@ApiModel
public enum HoldingScope {
  // This is only for backward compatibility
  // TODO : Remove this after a release
  @Deprecated @JsonProperty("PLAN") PLAN("PLAN"),

  // This corresponds to pipeline
  @JsonProperty("PIPELINE") PIPELINE("PIPELINE"),

  // This corresponds to stage
  @JsonProperty("STAGE") STAGE("STAGE"),

  @JsonProperty("STEP_GROUP") STEP_GROUP("STEP_GROUP");

  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static HoldingScope getHoldingScope(@JsonProperty("scope") String yamlName) {
    for (HoldingScope scope : HoldingScope.values()) {
      if (scope.yamlName.equalsIgnoreCase(yamlName)) {
        return scope;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  HoldingScope(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }
  @Override
  public String toString() {
    return yamlName;
  }
}
