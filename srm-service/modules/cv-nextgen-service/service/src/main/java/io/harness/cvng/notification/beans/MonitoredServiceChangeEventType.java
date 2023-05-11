/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;

public enum MonitoredServiceChangeEventType {
  // TODO: use ChangeCategory.java instead, once Alert is changed to Incident.
  @JsonProperty("Deployment")
  DEPLOYMENT(Arrays.asList(ActivityType.DEPLOYMENT, ActivityType.HARNESS_CD_CURRENT_GEN, ActivityType.HARNESS_CD),
      "Deployment"),
  @JsonProperty("Infrastructure")
  INFRASTRUCTURE(Arrays.asList(ActivityType.KUBERNETES, ActivityType.CONFIG), "Infrastructure"),
  @JsonProperty("Incident") INCIDENT(Arrays.asList(ActivityType.PAGER_DUTY), "Incident");

  private final List<ActivityType> activityTypes;
  private final String displayName;

  MonitoredServiceChangeEventType(List<ActivityType> activityTypes, String displayName) {
    this.activityTypes = activityTypes;
    this.displayName = displayName;
  }

  public List<ActivityType> getActivityTypes() {
    return this.activityTypes;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public static MonitoredServiceChangeEventType getMonitoredServiceChangeEventTypeFromActivityType(
      ActivityType activityType) {
    switch (activityType) {
      case DEPLOYMENT:
      case HARNESS_CD_CURRENT_GEN:
      case HARNESS_CD:
        return DEPLOYMENT;
      case KUBERNETES:
      case CONFIG:
        return INFRASTRUCTURE;
      case PAGER_DUTY:
        return INCIDENT;
      default:
        throw new InvalidArgumentsException("Not a valid Activity Type " + activityType);
    }
  }

  public static ChangeCategory convertMonitoredServiceChangeEventTypeToChangeCategory(
      MonitoredServiceChangeEventType eventType) {
    switch (eventType) {
      case DEPLOYMENT:
        return ChangeCategory.DEPLOYMENT;
      case INFRASTRUCTURE:
        return ChangeCategory.INFRASTRUCTURE;
      case INCIDENT:
        return ChangeCategory.ALERTS;
      default:
        throw new IllegalArgumentException("Unknown event type: " + eventType);
    }
  }

  public static MonitoredServiceChangeEventType convertChangeCategoryToMonitoredServiceChangeEventType(
      ChangeCategory changeCategory) {
    switch (changeCategory) {
      case DEPLOYMENT:
        return MonitoredServiceChangeEventType.DEPLOYMENT;
      case INFRASTRUCTURE:
        return MonitoredServiceChangeEventType.INFRASTRUCTURE;
      case ALERTS:
        return MonitoredServiceChangeEventType.INCIDENT;
      default:
        return null;
    }
  }
}
