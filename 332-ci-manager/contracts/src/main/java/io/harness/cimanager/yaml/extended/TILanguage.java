/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CI)
@TypeAlias("tiLanguage")
@RecasterAlias("io.harness.beans.yaml.extended.TILanguage")
public enum TILanguage {
  @JsonProperty("Java") JAVA("Java"),
  @JsonProperty("Kotlin") KOTLIN("Kotlin"),
  @JsonProperty("Scala") SCALA("Scala"),
  @JsonProperty("Csharp") CSHARP("Csharp"),
  @JsonProperty("Python") PYTHON("Python");

  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TILanguage getLanguage(@JsonProperty("language") String yamlName) {
    for (TILanguage language : TILanguage.values()) {
      if (language.yamlName.equalsIgnoreCase(yamlName)) {
        return language;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  TILanguage(String yamlName) {
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

  public static TILanguage fromString(final String s) {
    return TILanguage.getLanguage(s);
  }
}
