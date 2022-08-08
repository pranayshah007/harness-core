/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;
import io.harness.cvng.beans.activity.ActivityType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

@AllArgsConstructor
public enum ChangeSourceType {
  //@JsonProperty added for swagger as it doesnt understand @JsonValue
  @JsonProperty("HarnessCDNextGen") HARNESS_CD("HarnessCDNextGen", ChangeCategory.DEPLOYMENT, ActivityType.DEPLOYMENT),
  @JsonProperty("PagerDuty") PAGER_DUTY("PagerDuty", ChangeCategory.ALERTS, ActivityType.PAGER_DUTY),
  @JsonProperty("K8sCluster") KUBERNETES("K8sCluster", ChangeCategory.INFRASTRUCTURE, ActivityType.KUBERNETES),
  @JsonProperty("HarnessCD")
  HARNESS_CD_CURRENT_GEN("HarnessCD", ChangeCategory.DEPLOYMENT, ActivityType.HARNESS_CD_CURRENT_GEN);

  private static Map<ActivityType, ChangeSourceType> ACTIVITY_TO_CHANGE_SOURCE_TYPE_MAP;
  private static Map<String, ChangeSourceType> STRING_TO_CHANGE_SOURCE_TYPE_MAP;
  private static Map<ChangeCategory, List<ChangeSourceType>> CATEGORY_TO_CHANGE_SOURCE_TYPES_MAP;

  private String value;
  @Getter private ChangeCategory changeCategory;
  @Getter private ActivityType activityType;

  @JsonValue
  public String getValue() {
    return value;
  }

  public static List<ChangeSourceType> getForCategory(ChangeCategory changeCategory) {
    if (MapUtils.isEmpty(CATEGORY_TO_CHANGE_SOURCE_TYPES_MAP)) {
      CATEGORY_TO_CHANGE_SOURCE_TYPES_MAP =
          Arrays.stream(ChangeSourceType.values()).collect(Collectors.groupingBy(ChangeSourceType::getChangeCategory));
    }
    return CATEGORY_TO_CHANGE_SOURCE_TYPES_MAP.getOrDefault(changeCategory, Collections.emptyList());
  }

  public static ChangeSourceType ofActivityType(ActivityType activityType) {
    if (MapUtils.isEmpty(ACTIVITY_TO_CHANGE_SOURCE_TYPE_MAP)) {
      ACTIVITY_TO_CHANGE_SOURCE_TYPE_MAP =
          Arrays.stream(ChangeSourceType.values())
              .collect(Collectors.toMap(ChangeSourceType::getActivityType, Function.identity()));
    }
    if (!ACTIVITY_TO_CHANGE_SOURCE_TYPE_MAP.containsKey(activityType)) {
      throw new IllegalStateException("Activity type:" + activityType + " not mapped to ChangeSourceType");
    }
    return ACTIVITY_TO_CHANGE_SOURCE_TYPE_MAP.get(activityType);
  }

  public static ChangeSourceType fromString(String stringValue) {
    if (MapUtils.isEmpty(STRING_TO_CHANGE_SOURCE_TYPE_MAP)) {
      STRING_TO_CHANGE_SOURCE_TYPE_MAP =
          Arrays.stream(ChangeSourceType.values())
              .collect(Collectors.toMap(ChangeSourceType::getValue, Function.identity()));
      // TODO: Remove this once UI migrated to jsonValues for queryParams
      Arrays.asList(ChangeSourceType.values()).forEach(cst -> STRING_TO_CHANGE_SOURCE_TYPE_MAP.put(cst.name(), cst));
    }
    if (!STRING_TO_CHANGE_SOURCE_TYPE_MAP.containsKey(stringValue)) {
      throw new IllegalStateException("Change source type should be in : " + STRING_TO_CHANGE_SOURCE_TYPE_MAP.keySet());
    }
    return STRING_TO_CHANGE_SOURCE_TYPE_MAP.get(stringValue);
  }
}
