/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.MapUtils;

@AllArgsConstructor
public enum ChangeCategory {
  //@JsonProperty added for swagger as it doesnt understand @JsonValue
  @JsonProperty("Deployment") DEPLOYMENT("Deployment"),
  @JsonProperty("Infrastructure") INFRASTRUCTURE("Infrastructure"),
  @JsonProperty("Alert") ALERTS("Alert");

  private static Map<String, ChangeCategory> STRING_TO_CHANGE_CATEGORY_MAP;

  private String value;

  @JsonValue
  public String getValue() {
    return value;
  }

  public static ChangeCategory fromString(String stringValue) {
    if (MapUtils.isEmpty(STRING_TO_CHANGE_CATEGORY_MAP)) {
      STRING_TO_CHANGE_CATEGORY_MAP = Arrays.stream(ChangeCategory.values())
                                          .collect(Collectors.toMap(ChangeCategory::getValue, Function.identity()));
      // TODO: Remove this once UI migrated to jsonValues for queryParams
      Arrays.asList(ChangeCategory.values()).forEach(cc -> STRING_TO_CHANGE_CATEGORY_MAP.put(cc.name(), cc));
    }
    if (!STRING_TO_CHANGE_CATEGORY_MAP.containsKey(stringValue)) {
      throw new IllegalArgumentException("Change source type should be in : " + STRING_TO_CHANGE_CATEGORY_MAP.keySet());
    }
    return STRING_TO_CHANGE_CATEGORY_MAP.get(stringValue);
  }
}
