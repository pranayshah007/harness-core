/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.cache;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CI)
@TypeAlias("cachePolicy")
@RecasterAlias("io.harness.beans.yaml.extended.cache.CachePolicy")
public enum CachePolicy {
  @JsonProperty("pull") PULL("pull"),
  @JsonProperty("push") PUSH("push"),
  @JsonProperty("pull-push") PULL_PUSH("pull-push");

  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static CachePolicy getPolicy(@JsonProperty("policy") String yamlName) {
    for (CachePolicy language : CachePolicy.values()) {
      if (language.yamlName.equalsIgnoreCase(yamlName)) {
        return language;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  CachePolicy(String yamlName) {
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

  public static CachePolicy fromString(final String s) {
    return CachePolicy.getPolicy(s);
  }
}
