/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.extended.ci.codebase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PRCloneStrategy {
  @JsonProperty(CloneStrategyTypeConstants.MERGE_COMMIT) MERGE_COMMIT(CloneStrategyTypeConstants.MERGE_COMMIT),
  @JsonProperty(CloneStrategyTypeConstants.SOURCE_BRANCH) SOURCE_BRANCH(CloneStrategyTypeConstants.SOURCE_BRANCH);
  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static PRCloneStrategy getPRCloneStrategy(@JsonProperty("prCloneStrategy") String yamlName) {
    for (PRCloneStrategy prCloneStrategy : PRCloneStrategy.values()) {
      if (prCloneStrategy.yamlName.equalsIgnoreCase(yamlName)) {
        return prCloneStrategy;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  PRCloneStrategy(String yamlName) {
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

  public static PRCloneStrategy fromString(final String s) {
    return PRCloneStrategy.getPRCloneStrategy(s);
  }
}
