/*
 * Copyright 2021 Harness Inc. All rights reserved.
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

@TypeAlias("ciShellType")
@RecasterAlias("io.harness.beans.yaml.extended.CIShellType")
public enum CIShellType {
  @JsonProperty("Sh") SH("Sh"),
  @JsonProperty("Bash") BASH("Bash"),
  @JsonProperty("Powershell") POWERSHELL("Powershell"),
  @JsonProperty("Pwsh") PWSH("Pwsh");
  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static CIShellType getShellType(@JsonProperty("shellType") String yamlName) {
    for (CIShellType shellType : CIShellType.values()) {
      if (shellType.yamlName.equalsIgnoreCase(yamlName)) {
        return shellType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  CIShellType(String yamlName) {
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

  public static CIShellType fromString(final String s) {
    return CIShellType.getShellType(s);
  }
}
