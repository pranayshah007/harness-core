/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.filters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import org.codehaus.jackson.annotate.JsonProperty;

public enum FilterType {
  @JsonProperty("tags") tags("tags"),
  @JsonProperty("all") all("all");
  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static FilterType getFilterType(@JsonProperty("type") String yamlName) {
    for (FilterType filterType : FilterType.values()) {
      if (filterType.yamlName.equalsIgnoreCase(yamlName)) {
        return filterType;
      }
    }
    throw new IllegalArgumentException(
        String.format("Invalid value:%s, the expected values are: %s", yamlName, Arrays.toString(FilterType.values())));
  }
  FilterType(String yamlName) {
    this.yamlName = yamlName;
  }
  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  public static FilterType fromString(final String s) {
    return FilterType.getFilterType(s);
  }
}
