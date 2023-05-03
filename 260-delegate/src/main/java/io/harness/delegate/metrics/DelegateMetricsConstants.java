/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.metrics;

import static io.harness.delegate.metrics.DelegateMetricDetails.create;

import com.google.common.collect.Maps;
import java.util.Map;

public class DelegateMetricsConstants {
  public static Map<String, DelegateMetricDetails> DELEGATE_AGENT_METRIC_MAP = Maps.newHashMap();

  public static final String TASK_EXECUTION_TIME = "task_execution_time";
  public static final String TASKS_CURRENTLY_EXECUTING = "tasks_currently_executing";
  public static final String TASKS_IN_QUEUE = "tasks_in_queue";
  public static final String TASK_TIMEOUT = "task_timeout";
  public static final String TASK_COMPLETED = "task_completed";
  public static final String TASK_FAILED = "task_failed";
  public static final String TASK_REJECTED = "task_rejected";
  public static final String DELEGATE_CONNECTED = "delegate_connected";
  public static final String RESOURCE_CONSUMPTION_ABOVE_THRESHOLD = "delegate_resource_consumption_above_threshold";

  private static final String DELEGATE_NAME_LABEL = "delegate_name";
  private static final String TASK_TYPE_LABEL = "task_type";

  static {
    put(TASK_EXECUTION_TIME, create("Time needed to execute the task.", DELEGATE_NAME_LABEL, TASK_TYPE_LABEL));
    put(TASKS_CURRENTLY_EXECUTING, create("Number of tasks in execution.", DELEGATE_NAME_LABEL));
    put(TASKS_IN_QUEUE, create("Number of tasks in the queue.", DELEGATE_NAME_LABEL));
    put(TASK_TIMEOUT, create("Number of tasks timed out.", DELEGATE_NAME_LABEL, TASK_TYPE_LABEL));
    put(TASK_COMPLETED, create("Number of tasks completed.", DELEGATE_NAME_LABEL, TASK_TYPE_LABEL));
    put(TASK_FAILED, create("Number of tasks failed.", DELEGATE_NAME_LABEL, TASK_TYPE_LABEL));
    put(TASK_REJECTED,
        create("Number of tasks rejected due to high load on delegate.", DELEGATE_NAME_LABEL, TASK_TYPE_LABEL));
    put(DELEGATE_CONNECTED, create("Delegate connected.", DELEGATE_NAME_LABEL));
    put(RESOURCE_CONSUMPTION_ABOVE_THRESHOLD,
        create("Delegate resource consumption reached more than 90% .", DELEGATE_NAME_LABEL));
  }

  private static void put(String metricName, DelegateMetricDetails metricDetails) {
    DELEGATE_AGENT_METRIC_MAP.put(metricName, metricDetails);
  }
}
