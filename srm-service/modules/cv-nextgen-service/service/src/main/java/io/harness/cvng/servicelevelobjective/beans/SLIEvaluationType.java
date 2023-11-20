/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

@Getter
@AllArgsConstructor
public enum SLIEvaluationType {
  @JsonEnumDefaultValue @JsonProperty("Window") WINDOW("Window", "Window"),
  @JsonProperty("Request") REQUEST("Request", "Request"),
  @JsonProperty("MetricLess") METRIC_LESS("MetricLess", "Window");

  private static Map<String, SLIEvaluationType> STRING_TO_TYPE_MAP;

  private String identifier;
  private final String compositeSLOEvaluationType;

  public static SLIEvaluationType fromString(String stringValue) {
    if (MapUtils.isEmpty(STRING_TO_TYPE_MAP)) {
      STRING_TO_TYPE_MAP = Arrays.stream(SLIEvaluationType.values())
                               .collect(Collectors.toMap(SLIEvaluationType::getIdentifier, Function.identity()));
    }
    if (!STRING_TO_TYPE_MAP.containsKey(stringValue)) {
      throw new IllegalArgumentException("SLIEvaluationType should be in : " + STRING_TO_TYPE_MAP.keySet());
    }
    return STRING_TO_TYPE_MAP.get(stringValue);
  }
}
