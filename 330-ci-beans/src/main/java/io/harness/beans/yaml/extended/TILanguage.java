package io.harness.beans.yaml.extended;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.CI)
public enum TILanguage {
  @JsonProperty("Java") JAVA("Java"),
  @JsonProperty("Csharp") CSHARP("Csharp");

  private final String yamlName;

  @JsonCreator
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
