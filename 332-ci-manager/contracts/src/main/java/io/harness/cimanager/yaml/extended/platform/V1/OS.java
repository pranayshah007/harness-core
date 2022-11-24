/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.platform.V1;

import io.harness.beans.yaml.extended.infrastrucutre.OSType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("os")
public enum OS {
  @JsonProperty("linux")
  LINUX("linux") {
    @Override
    public OSType toOSType() {
      return OSType.Linux;
    }
  },
  @JsonProperty("windows")
  WINDOWS("windows") {
    @Override
    public OSType toOSType() {
      return OSType.Windows;
    }
  },
  @JsonProperty("macos")
  MACOS("macos") {
    @Override
    public OSType toOSType() {
      return OSType.MacOS;
    }
  };
  private final String yamlName;

  OS(String yamlName) {
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

  public abstract OSType toOSType();
}
