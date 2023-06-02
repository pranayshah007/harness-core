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

@TypeAlias("tiPythonVersion")
@RecasterAlias("io.harness.beans.yaml.extended.TIPythonVersion")
public enum TIPythonVersion {
  @JsonProperty("3") PYTHONTHREE("3"),
  @JsonProperty("2") PYTHONTWO("2");

  private final String yamlName;

  TIPythonVersion(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TIPythonVersion getPythonVersion(@JsonProperty("pythonVersion") String yamlName) {
    if (yamlName.equalsIgnoreCase("PYTHONTHREE") || yamlName.equalsIgnoreCase(PYTHONTHREE.yamlName)) {
      return PYTHONTHREE;
    } else if (yamlName.equalsIgnoreCase("PYTHONTWO") || yamlName.equalsIgnoreCase(PYTHONTWO.yamlName)) {
      return PYTHONTWO;
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

  public static TIPythonVersion fromString(final String s) {
    return TIPythonVersion.getPythonVersion(s);
  }
}
