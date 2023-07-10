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
  //@JsonProperty added for swagger as it doesn't understand @JsonValue
  @JsonProperty("HarnessCDNextGen")
  HARNESS_CD("HarnessCDNextGen", ChangeCategory.DEPLOYMENT, ActivityType.DEPLOYMENT, true),
  @JsonProperty("PagerDuty") PAGER_DUTY("PagerDuty", ChangeCategory.ALERTS, ActivityType.PAGER_DUTY, false),
  @JsonProperty("K8sCluster") KUBERNETES("K8sCluster", ChangeCategory.INFRASTRUCTURE, ActivityType.KUBERNETES, false),
  @JsonProperty("HarnessCD")
  HARNESS_CD_CURRENT_GEN("HarnessCD", ChangeCategory.DEPLOYMENT, ActivityType.HARNESS_CD_CURRENT_GEN, false),
  @JsonProperty("HarnessFF") HARNESS_FF("HarnessFF", ChangeCategory.FEATURE_FLAG, ActivityType.FEATURE_FLAG, true),
  @JsonProperty("HarnessCE")
  HARNESS_CE("HarnessCE", ChangeCategory.CHAOS_EXPERIMENT, ActivityType.CHAOS_EXPERIMENT, true),
  @JsonProperty("CustomDeploy")
  CUSTOM_DEPLOY("CustomDeploy", ChangeCategory.DEPLOYMENT, ActivityType.CUSTOM_DEPLOY, false),
  @JsonProperty("CustomIncident")
  CUSTOM_INCIDENT("CustomIncident", ChangeCategory.ALERTS, ActivityType.CUSTOM_INCIDENT, false),
  @JsonProperty("CustomInfrastructure")
  CUSTOM_INFRA("CustomInfrastructure", ChangeCategory.INFRASTRUCTURE, ActivityType.CUSTOM_INFRA, false),
  @JsonProperty("CustomFF") CUSTOM_FF("CustomFF", ChangeCategory.FEATURE_FLAG, ActivityType.CUSTOM_FF, false),
  @JsonProperty("SRM_STEP_ANALYSIS")
  SRM_STEP_ANALYSIS("SRM_STEP_ANALYSIS", ChangeCategory.DEPLOYMENT, ActivityType.SRM_STEP_ANALYSIS, true);
  private static Map<ActivityType, ChangeSourceType> ACTIVITY_TO_CHANGE_SOURCE_TYPE_MAP;
  private static Map<ChangeCategory, List<ChangeSourceType>> CATEGORY_TO_CHANGE_SOURCE_TYPES_MAP;

  private String value;
  @Getter private ChangeCategory changeCategory;
  @Getter private ActivityType activityType;
  @Getter private boolean isInternal;

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return this.value;
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
}
