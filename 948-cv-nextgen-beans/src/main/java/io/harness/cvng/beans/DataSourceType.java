/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.models.VerificationType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public enum DataSourceType {
  APP_DYNAMICS("Appdynamics", VerificationType.TIME_SERIES, "appdynamics"),
  SPLUNK("Splunk", VerificationType.LOG, "splunk"),
  SPLUNK_METRIC("Splunk Metrics", VerificationType.TIME_SERIES, "prometheus"),
  STACKDRIVER("Stackdriver", VerificationType.TIME_SERIES, "prometheus"),
  STACKDRIVER_LOG("Stackdriver Log", VerificationType.LOG, "splunk"),
  KUBERNETES("Kubernetes", VerificationType.TIME_SERIES, "prometheus"),
  NEW_RELIC("New Relic", VerificationType.TIME_SERIES, "appdynamics"),
  PROMETHEUS("Prometheus", VerificationType.TIME_SERIES, "prometheus"),
  DATADOG_METRICS("DatadogMetrics", VerificationType.TIME_SERIES, "prometheus"),
  DATADOG_LOG("DatadogLog", VerificationType.LOG, "splunk"),
  ERROR_TRACKING("ErrorTracking", VerificationType.LOG, "splunk"),
  DYNATRACE("Dynatrace", VerificationType.TIME_SERIES, "prometheus"),
  CUSTOM_HEALTH_METRIC("CustomHealthMetric", VerificationType.TIME_SERIES, "prometheus"),
  CUSTOM_HEALTH_LOG("CustomHealthLog", VerificationType.LOG, "splunk");

  private String displayName;
  private VerificationType verificationType;
  // template prefix that should be used for demo data.
  private String demoTemplatePrefix;

  DataSourceType(String displayName, VerificationType verificationType, String demoTemplatePrefix) {
    this.displayName = displayName;
    this.verificationType = verificationType;
    this.demoTemplatePrefix = demoTemplatePrefix;
  }

  public String getDisplayName() {
    return displayName;
  }

  public VerificationType getVerificationType() {
    return verificationType;
  }

  public static List<DataSourceType> getTimeSeriesTypes() {
    return new ArrayList<>(
        EnumSet.of(APP_DYNAMICS, STACKDRIVER, NEW_RELIC, PROMETHEUS, DATADOG_METRICS, DYNATRACE, CUSTOM_HEALTH_METRIC));
  }

  public String getDemoTemplatePrefix() {
    return demoTemplatePrefix;
  }
}
