/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.beans;

import io.harness.beans.yaml.extended.CIShellType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Shell {
  @JsonProperty("sh")
  SH("sh") {
    @Override
    public CIShellType toShellType() {
      return CIShellType.SH;
    }
  },
  @JsonProperty("bash")
  BASH("bash") {
    @Override
    public CIShellType toShellType() {
      return CIShellType.BASH;
    }
  },
  @JsonProperty("powershell")
  POWERSHELL("powershell") {
    @Override
    public CIShellType toShellType() {
      return CIShellType.POWERSHELL;
    }
  },
  @JsonProperty("pwsh")
  PWSH("pwsh") {
    @Override
    public CIShellType toShellType() {
      return CIShellType.PWSH;
    }
  },
  @JsonProperty("python")
  PYTHON("python") {
    @Override
    public CIShellType toShellType() {
      return CIShellType.PYTHON;
    }
  };

  private final String yamlName;

  Shell(String yamlName) {
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

  public abstract CIShellType toShellType();
}
