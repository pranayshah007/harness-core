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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public enum DataSourceType {
  APP_DYNAMICS("Appdynamics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_APPDYNAMICS),
  SPLUNK("Splunk", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK),
  SPLUNK_METRIC("Splunk Metrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  STACKDRIVER("Stackdriver", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  STACKDRIVER_LOG("Stackdriver Log", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK),
  KUBERNETES("Kubernetes", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  NEW_RELIC("New Relic", VerificationType.TIME_SERIES, DEMO_TEMPLATE_APPDYNAMICS),
  PROMETHEUS("Prometheus", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  DATADOG_METRICS("DatadogMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  DATADOG_LOG("DatadogLog", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK),
  ERROR_TRACKING("ErrorTracking", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK),
  DYNATRACE("Dynatrace", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  CUSTOM_HEALTH_METRIC("CustomHealthMetric", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  CUSTOM_HEALTH_LOG("CustomHealthLog", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK),
  ELASTICSEARCH("ElasticSearch", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK),
  CLOUDWATCH_METRICS("CloudWatchMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_APPDYNAMICS),
  AWS_PROMETHEUS("AwsPrometheus", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  SUMOLOGIC_METRICS("SumologicMetrics", VerificationType.TIME_SERIES, DEMO_TEMPLATE_PROMETHEUS),
  SUMOLOGIC_LOG("SumologicLog", VerificationType.LOG, DEMO_TEMPLATE_SPLUNK);
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
    return new ArrayList<>(EnumSet.of(APP_DYNAMICS, STACKDRIVER, NEW_RELIC, PROMETHEUS, DATADOG_METRICS, DYNATRACE,
        CUSTOM_HEALTH_METRIC, CLOUDWATCH_METRICS, SUMOLOGIC_METRICS));
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
