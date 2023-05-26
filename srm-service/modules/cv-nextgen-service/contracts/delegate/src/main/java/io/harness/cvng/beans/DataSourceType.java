/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.cvng.beans.DataSourceType.Constants.DEMO_TEMPLATE_APPDYNAMICS;
import static io.harness.cvng.beans.DataSourceType.Constants.DEMO_TEMPLATE_PROMETHEUS;
import static io.harness.cvng.beans.DataSourceType.Constants.DEMO_TEMPLATE_SPLUNK;

import io.harness.cvng.models.VerificationType;

public enum DataSourceType {
  APP_DYNAMICS("Appdynamics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_APPDYNAMICS, false),
  SPLUNK("Splunk", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, false),
  SPLUNK_METRIC("Splunk Metrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  STACKDRIVER("Stackdriver", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  STACKDRIVER_LOG("Stackdriver Log", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, false),
  KUBERNETES("Kubernetes", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  NEW_RELIC("New Relic", VerificationType.TIME_SERIES, DEMO_TEMPLATE_APPDYNAMICS, false),
  PROMETHEUS("Prometheus", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  DATADOG_METRICS("DatadogMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  DATADOG_LOG("DatadogLog", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, false),
  ERROR_TRACKING("ErrorTracking", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, false),
  DYNATRACE("Dynatrace", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  CUSTOM_HEALTH_METRIC("CustomHealthMetric", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  CUSTOM_HEALTH_LOG("CustomHealthLog", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, false),
  ELASTICSEARCH("ElasticSearch", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, true),
  CLOUDWATCH_METRICS("CloudWatchMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_APPDYNAMICS, false),
  AWS_PROMETHEUS("AwsPrometheus", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, false),
  SUMOLOGIC_METRICS("SumologicMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, true),
  SUMOLOGIC_LOG("SumologicLog", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, true),
  SPLUNK_SIGNALFX_METRICS("SplunkSignalFXMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS, true),
  GRAFANA_LOKI_LOGS("GrafanaLokiLogs", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK, true);

  private String displayName;
  private VerificationType verificationType;
  // template prefix that should be used for demo data.
  private String demoTemplatePrefix;

  private boolean isNextGenSpec;

  DataSourceType(
      String displayName, VerificationType verificationType, String demoTemplatePrefix, boolean isNextGenSpec) {
    this.displayName = displayName;
    this.verificationType = verificationType;
    this.demoTemplatePrefix = demoTemplatePrefix;
    this.isNextGenSpec = isNextGenSpec;
  }

  public String getDisplayName() {
    return displayName;
  }

  public VerificationType getVerificationType() {
    return verificationType;
  }
  public boolean isNextGenSpec() {
    return isNextGenSpec;
  }

  public String getDemoTemplatePrefix() {
    return demoTemplatePrefix;
  }

  static final class Constants {
    static final String DEMO_TEMPLATE_SPLUNK = "splunk";
    static final String DEMO_TEMPLATE_PROMETHEUS = "prometheus";
    static final String DEMO_TEMPLATE_APPDYNAMICS = "appdynamics";

    private Constants() {
      throw new IllegalStateException("Utility class");
    }
  }
}
