/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.extended.ci.codebase;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("io.harness.yaml.extended.ci.BuildType")
@OwnedBy(CI)
public enum GitCloneStepBuildType {
  @JsonProperty(BuildTypeConstants.BRANCH) BRANCH(BuildTypeConstants.BRANCH),
  @JsonProperty(BuildTypeConstants.TAG) TAG(BuildTypeConstants.TAG),
  @JsonProperty(BuildTypeConstants.PR) PR(BuildTypeConstants.PR),
  @JsonProperty(BuildTypeConstants.COMMIT_SHA) COMMIT_SHA(BuildTypeConstants.COMMIT_SHA);

  private final String yamlProperty;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static GitCloneStepBuildType getCodeBaseType(@JsonProperty("type") String yamlPropertyName) {
    for (GitCloneStepBuildType buildType : GitCloneStepBuildType.values()) {
      if (buildType.yamlProperty.equalsIgnoreCase(yamlPropertyName)) {
        return buildType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlPropertyName);
  }

  GitCloneStepBuildType(String yamlProperty) {
    this.yamlProperty = yamlProperty;
  }

  @JsonValue
  public String getYamlProperty() {
    return yamlProperty;
  }

  @Override
  public String toString() {
    return yamlProperty;
  }

  public static GitCloneStepBuildType fromString(final String s) {
    return GitCloneStepBuildType.getCodeBaseType(s);
  }
}
