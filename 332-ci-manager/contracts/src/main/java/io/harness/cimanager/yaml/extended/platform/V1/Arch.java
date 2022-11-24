/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.platform.V1;

import io.harness.beans.yaml.extended.platform.ArchType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("arch")
public enum Arch {
  @JsonProperty("amd64")
  AMD_64("amd64") {
    @Override
    public ArchType toArchType() {
      return ArchType.Amd64;
    }
  },
  @JsonProperty("arm64")
  ARM_64("arm64") {
    @Override
    public ArchType toArchType() {
      return ArchType.Arm64;
    }
  };
  private final String yamlName;

  Arch(String yamlName) {
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

  public abstract ArchType toArchType();
}