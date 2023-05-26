/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
@TypeAlias("tiSplitStrategy")
@RecasterAlias("io.harness.beans.yaml.extended.TISplitStrategy")
public enum TISplitStrategy {
  @JsonProperty("ClassTiming") CLASSTIMING("ClassTiming"),
  @JsonProperty("TestCount") TESTCOUNT("TestCount");

  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TISplitStrategy getSplitStrategy(@JsonProperty("testSplitStrategy") String yamlName) {
    for (TISplitStrategy splitStrategy : TISplitStrategy.values()) {
      if (splitStrategy.yamlName.equalsIgnoreCase(yamlName)) {
        return splitStrategy;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  TISplitStrategy(String yamlName) {
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

  public static TISplitStrategy fromString(final String s) {
    return TISplitStrategy.getSplitStrategy(s);
  }
}
