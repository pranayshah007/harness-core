/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("tiDotNetBuildEnvName")
@RecasterAlias("io.harness.beans.yaml.extended.TIDotNetBuildEnvName")
public enum TIDotNetBuildEnvName {
  @JsonProperty("Core") CORE("Core"),
  @JsonProperty("Framework") FRAMEWORK("Framework");

  private final String yamlName;

  TIDotNetBuildEnvName(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TIDotNetBuildEnvName getDotNetBuildEnvName(@JsonProperty("buildEnvironment") String yamlName) {
    for (TIDotNetBuildEnvName dotNetVersion : TIDotNetBuildEnvName.values()) {
      if (dotNetVersion.yamlName.equalsIgnoreCase(yamlName)) {
        return dotNetVersion;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static TIDotNetBuildEnvName fromString(final String s) {
    return TIDotNetBuildEnvName.getDotNetBuildEnvName(s);
  }
}
