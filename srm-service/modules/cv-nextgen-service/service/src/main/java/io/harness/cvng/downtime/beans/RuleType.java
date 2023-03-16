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
public enum RuleType {
  @JsonProperty("All") ALL("All"),
  @JsonProperty("Identifiers") IDENTFIERS("Identifiers");

  private static Map<String, RuleType> ruleTypeMap;

  @Getter private final String identifier;

  public static RuleType fromString(String stringValue) {
    if (MapUtils.isEmpty(ruleTypeMap)) {
      ruleTypeMap =
          Arrays.stream(RuleType.values()).collect(Collectors.toMap(RuleType::getIdentifier, Function.identity()));
    }
    if (!ruleTypeMap.containsKey(stringValue)) {
      throw new IllegalArgumentException("RuleType should be in : " + ruleTypeMap.keySet());
    }
    return ruleTypeMap.get(stringValue);
  }
}
