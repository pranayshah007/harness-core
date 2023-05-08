/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.metrics;

import static io.harness.delegate.metrics.DelegateMetricsConstants.DELEGATE_NAME_LABEL;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_TYPE_LABEL;

public enum DelegateMetric {
  TASK_EXECUTION_TIME("task_execution_time", "Time needed to execute the task", DelegateMetricType.GAUGE,
      DELEGATE_NAME_LABEL, TASK_TYPE_LABEL),
  TASKS_CURRENTLY_EXECUTING(
      "tasks_currently_executing", "Number of tasks in execution", DelegateMetricType.GAUGE, DELEGATE_NAME_LABEL),
  TASK_TIMEOUT(
      "task_timeout", "Number of tasks timed out", DelegateMetricType.COUNT, DELEGATE_NAME_LABEL, TASK_TYPE_LABEL),
  TASK_COMPLETED(
      "task_completed", "Number of tasks completed", DelegateMetricType.COUNT, DELEGATE_NAME_LABEL, TASK_TYPE_LABEL),
  TASK_FAILED("task_failed", "Number of tasks failed", DelegateMetricType.COUNT, DELEGATE_NAME_LABEL, TASK_TYPE_LABEL),
  TASK_REJECTED("task_rejected", "Number of tasks rejected due to high load on delegate", DelegateMetricType.COUNT,
      DELEGATE_NAME_LABEL, TASK_TYPE_LABEL),
  DELEGATE_CONNECTED("delegate_connected", "Delegate connected", DelegateMetricType.GAUGE, DELEGATE_NAME_LABEL),
  RESOURCE_CONSUMPTION_ABOVE_THRESHOLD("delegate_resource_consumption_above_threshold",
      "Delegate resource consumption reached more than threshold", DelegateMetricType.GAUGE, DELEGATE_NAME_LABEL);

  private final String metricName;
  private final String description;
  private final DelegateMetricType metricType;
  private final String[] labels;

  DelegateMetric(String metricName, String description, DelegateMetricType metricType, String... labels) {
    this.metricName = metricName;
    this.description = description;
    this.metricType = metricType;
    this.labels = labels;
  }

  public String getMetricName() {
    return metricName;
  }

  public String getDescription() {
    return description;
  }

  public DelegateMetricType getMetricType() {
    return metricType;
  }

  public String[] getLabels() {
    return labels;
  }
}
