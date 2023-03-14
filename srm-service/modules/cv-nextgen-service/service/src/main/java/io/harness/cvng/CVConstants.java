/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Sets;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CV)
public interface CVConstants {
  String SERVICE_BASE_URL = "/cv/api";
  Duration MAX_DATA_RETENTION_DURATION = Duration.ofDays(180);
  /**
   * This should be set in findOption for the queries that are potentially working with large data.
   * This should be used for anything that is using
   * io.harness.cvng.utils.CVNGParallelExecutor#executeParallel(java.util.List) Ex: new
   * FindOptions().maxTime(MONGO_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
   */
  int MONGO_QUERY_TIMEOUT_SEC = 5;
  String DEFAULT_HEALTH_JOB_NAME = "Health Verification";
  String DEFAULT_HEALTH_JOB_ID = DEFAULT_HEALTH_JOB_NAME.replace(" ", "_");
  String DEFAULT_TEST_JOB_NAME = "Load Test Verification";
  String DEFAULT_TEST_JOB_ID = DEFAULT_TEST_JOB_NAME.replace(" ", "_");
  String DEFAULT_CANARY_JOB_NAME = "Canary Deployment Verification";
  String DEFAULT_CANARY_JOB_ID = DEFAULT_CANARY_JOB_NAME.replace(" ", "_");
  String DEFAULT_BLUE_GREEN_JOB_NAME = "Blue Green Deployment Verification";
  String DEFAULT_BLUE_GREEN_JOB_ID = DEFAULT_BLUE_GREEN_JOB_NAME.replace(" ", "_");
  String RUNTIME_PARAM_STRING = "<+input>";
  int STATE_MACHINE_IGNORE_LIMIT = 100;

  int CREATE_TIME_MINUTES_FOR_DEMO_CVCONFIG = 120;
  Duration DATA_COLLECTION_TIME_RANGE_FOR_SLI = Duration.ofDays(1);
  int STATE_MACHINE_IGNORE_MINUTES = 150;
  int STATE_MACHINE_IGNORE_MINUTES_FOR_DEMO = CREATE_TIME_MINUTES_FOR_DEMO_CVCONFIG + 120;
  int STATE_MACHINE_IGNORE_MINUTES_DEFAULT = 150;
  int STATE_MACHINE_IGNORE_MINUTES_FOR_SLI = Integer.MAX_VALUE;

  String DATA_SOURCE_TYPE = "type";
  String SLO_SPEC = "type";
  String DOWNTIME_SPEC_TYPE = "type";

  String RULE_TYPE = "type";

  String SLO_TARGET_TYPE = "type";
  String SLI_METRIC_TYPE = "type";
  String METRIC_THRESHOLD_METRIC_TYPE = "type";
  String LIVE_MONITORING = "live_monitoring";
  String DEPLOYMENT = "deployment";

  String TAG_DATA_SOURCE = "dataSourceType";
  String TAG_VERIFICATION_TYPE = "verificationType";
  String TAG_ACCOUNT_ID = "accountId";
  String TAG_ONBOARDING = "onboarding";
  String STATEMACHINE_PUBLISHER = "srm_statemachine_publisher";

  String CUSTOM_CHANGE_PUBLISHER = "srm_custom_change_publisher";
  String SET_KEY = "$set";
  int BULK_OPERATION_THRESHOLD = 1000;

  String SLO_PARAM_MESSAGE = "SLO identifier for the entity";
  String USER_JOURNEY_PARAM_MESSAGE = "For filtering on the basis of user journeys' identifiers";
  String MONITORED_SERVICE_PARAM_MESSAGE = "For filtering on the basis of monitored services' identifiers";
  String SLI_TYPE_PARAM_MESSAGE = "For filtering on the basis of SLI types";
  String TARGET_TYPE_PARAM_MESSAGE = "For filtering on the basis of target types";
  String ERROR_BUDGET_RISK_PARAM_MESSAGE = "For filtering on the basis of error budget risks";
  String VERIFICATION_TASK_ID = "verificationTaskId";

  String ENVIRONMENT = System.getenv("ENV") == null ? "localhost" : System.getenv("ENV");
  Set<String> LEARNING_ENGINE_TASKS_METRIC_LIST = Collections.unmodifiableSet(
      Sets.newHashSet("ng_le_deployment_max_queued_time_sec", "ng_le_service_health_max_queued_time_sec",
          "ng_le_deployment_task_count", "ng_le_service_health_task_count"));
}
