/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.activity;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;

public enum ActivityType {
  DEPLOYMENT("DEPLOYMENT"),
  CONFIG("CONFIG"),
  KUBERNETES("KUBERNETES"),
  HARNESS_CD("HARNESS_CD"),
  PAGER_DUTY("PAGER_DUTY"),
  HARNESS_CD_CURRENT_GEN("HARNESS_CD_CURRENT_GEN"),
  FEATURE_FLAG("FEATURE_FLAG"),
  CHAOS_EXPERIMENT("CHAOS_EXPERIMENT"),
  CUSTOM_DEPLOY("CUSTOM_DEPLOY"),
  CUSTOM_INCIDENT("CUSTOM_INCIDENT"),
  CUSTOM_INFRA("CUSTOM_INFRA"),
  CUSTOM_FF("CUSTOM_FF"),
  SRM_STEP_ANALYSIS("SRM_STEP_ANALYSIS");

  public final String name;

  ActivityType(String name) {
    this.name = name;
  }

  @JsonValue
  public String getName() {
    return name;
  }

  private static Map<String, ActivityType> STRING_TO_ACTIVITY_TYPE_MAP;

  public static ActivityType fromString(String stringValue) {
    if (MapUtils.isEmpty(STRING_TO_ACTIVITY_TYPE_MAP)) {
      STRING_TO_ACTIVITY_TYPE_MAP =
          Arrays.stream(ActivityType.values()).collect(Collectors.toMap(ActivityType::getName, Function.identity()));
      // TODO: Remove this once UI migrated to jsonValues for queryParams
      Arrays.asList(ActivityType.values())
          .forEach(activityType -> STRING_TO_ACTIVITY_TYPE_MAP.put(activityType.name(), activityType));
    }
    if (!STRING_TO_ACTIVITY_TYPE_MAP.containsKey(stringValue)) {
      throw new IllegalStateException("Change source type should be in : " + STRING_TO_ACTIVITY_TYPE_MAP.keySet());
    }
    return STRING_TO_ACTIVITY_TYPE_MAP.get(stringValue);
  }
}
