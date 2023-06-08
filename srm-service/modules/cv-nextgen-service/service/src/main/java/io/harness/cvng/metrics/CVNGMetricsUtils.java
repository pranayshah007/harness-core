/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

public interface CVNGMetricsUtils {
  String METRIC_LABEL_PREFIX = "metricsLabel_";
  // time taken metrics are tracked from the time when task becomes eligible to finish.
  String VERIFICATION_JOB_INSTANCE_EXTRA_TIME = "verification_job_instance_extra_time";
  String VERIFICATION_JOB_INSTANCE_HEALTH_SOURCE_EXTRA_TIME = "verification_job_instance_health_source_extra_time";
  String DATA_COLLECTION_TASK_TOTAL_TIME = "data_collection_task_total_time";
  String DATA_COLLECTION_TASK_WAIT_TIME = "data_collection_task_wait_time";
  String DATA_COLLECTION_TASK_RUNNING_TIME = "data_collection_task_running_time";
  String LEARNING_ENGINE_TASK_TOTAL_TIME = "learning_engine_task_total_time";
  String LEARNING_ENGINE_TASK_WAIT_TIME = "learning_engine_task_wait_time";
  String LEARNING_ENGINE_TASK_RUNNING_TIME = "learning_engine_task_running_time";
  String API_CALL_EXECUTION_TIME = "api_call_execution_time";
  String API_CALL_RESPONSE_SIZE = "api_call_response_size";
  String ANALYSIS_STATE_MACHINE_RETRY_COUNT = "analysis_state_machine_retry_count";
  String ORCHESTRATOR_STATE_MACHINE_QUEUE_COUNT_ABOVE_FIVE = "orchestrator_state_machine_queue_size_above_five_count";
  String SLO_DATA_ANALYSIS_METRIC = "slo_data_analysis_metric";
  String RECALCULATION_FAILURE = "recalculation_failure";
  String CALCULATION_FAILURE = "calculation_failure";

  String ORCHESTRATION_TIME = "orchestration_time";

  String STATE_MACHINE_EXECUTION_TIME = "state_machine_execution_time";

  static String getApiCallLogResponseCodeMetricName(String responseCode) {
    return String.format("api_call_response_code_%sxx", responseCode.charAt(0));
  }

  static String getLearningEngineTaskStatusMetricName(LearningEngineTask.ExecutionStatus executionStatus) {
    return String.format("learning_engine_task_%s_count", executionStatus.toString().toLowerCase());
  }

  static String getVerificationJobInstanceStatusMetricName(VerificationJobInstance.ExecutionStatus executionStatus) {
    return String.format("verification_job_instance_%s_count", executionStatus.toString().toLowerCase());
  }

  static String getVerificationJobInstanceStatusMetricName(ActivityVerificationStatus activityVerificationStatus) {
    return String.format("verification_job_instance_%s_count", activityVerificationStatus.toString().toLowerCase());
  }
  static String getDataCollectionTaskStatusMetricName(DataCollectionExecutionStatus executionStatus) {
    return String.format("data_collection_task_%s_count", executionStatus.toString().toLowerCase());
  }
}
