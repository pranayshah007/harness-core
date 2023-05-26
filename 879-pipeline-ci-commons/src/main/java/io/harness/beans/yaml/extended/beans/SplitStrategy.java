/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.beans;

import io.harness.beans.yaml.extended.TISplitStrategy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SplitStrategy {
  @JsonProperty("class_timing")
  CLASS_TIMING("class_timing") {
    @Override
    public TISplitStrategy toTISplitStrategy() {
      return TISplitStrategy.CLASSTIMING;
    }
  },
  @JsonProperty("test_count")
  TEST_COUNT("test_count") {
    @Override
    public TISplitStrategy toTISplitStrategy() {
      return TISplitStrategy.TESTCOUNT;
    }
  };

  private final String yamlName;

  SplitStrategy(String yamlName) {
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

  public abstract TISplitStrategy toTISplitStrategy();
}
