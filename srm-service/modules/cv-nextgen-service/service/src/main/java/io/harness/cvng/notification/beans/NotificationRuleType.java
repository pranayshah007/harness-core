/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

public enum NotificationRuleType {
  @JsonProperty("MonitoredService") MONITORED_SERVICE("monitoredservice"),
  @JsonProperty("ServiceLevelObjective") SLO("slo"),
  @JsonProperty("FireHydrant") FIRE_HYDRANT("firehydrant");

  @Getter private final String templateSuffixIdentifier;

  NotificationRuleType(String templateSuffixIdentifier) {
    this.templateSuffixIdentifier = templateSuffixIdentifier;
  }
}
