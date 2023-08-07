/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.platform;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("archType")
@RecasterAlias("io.harness.beans.yaml.extended.platform.ArchType")
public enum ArchType {
  @JsonProperty("Amd64") Amd64("Amd64"),
  @JsonProperty("Arm64") Arm64("Arm64");
  private final String yamlName;
  @ApiModelProperty(hidden = true) String uuid;
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ArchType getArchType(@JsonProperty("archType") String yamlName) {
    for (ArchType archType : ArchType.values()) {
      if (archType.yamlName.equalsIgnoreCase(yamlName)) {
        return archType;
      }
    }
    throw new IllegalArgumentException(
        String.format("Arch type %s is invalid, valid values are : %s", yamlName, Arrays.asList(ArchType.values())));
  }

  ArchType(String yamlName) {
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

  public static ArchType fromString(final String s) {
    return ArchType.getArchType(s);
  }
}