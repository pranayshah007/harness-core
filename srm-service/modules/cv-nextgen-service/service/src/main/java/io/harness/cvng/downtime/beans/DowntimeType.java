/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

@AllArgsConstructor
public enum DowntimeType {
  @JsonProperty("Onetime") ONE_TIME("Onetime"),
  @JsonProperty("Recurring") RECURRING("Recurring");

  private static Map<String, DowntimeType> downtimeTypeMap;

  @Getter private final String identifier;

  public static DowntimeType fromString(String stringValue) {
    if (MapUtils.isEmpty(downtimeTypeMap)) {
      downtimeTypeMap = Arrays.stream(DowntimeType.values())
                            .collect(Collectors.toMap(DowntimeType::getIdentifier, Function.identity()));
    }
    if (!downtimeTypeMap.containsKey(stringValue)) {
      throw new IllegalArgumentException("DowntimeType should be in : " + downtimeTypeMap.keySet());
    }
    return downtimeTypeMap.get(stringValue);
  }
}
