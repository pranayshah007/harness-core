/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.core.services;

import java.time.Duration;

public interface CVNextGenConstants {
  String CV_NEXTGEN_RESOURCE_PREFIX = "cv-nextgen";
  String DELEGATE_DATA_COLLECTION = "delegate-data-collection";
  String CVNG_LOG_RESOURCE_PATH = "cvng-log";
  String LOG_RECORD_RESOURCE_PATH = "log-record";
  String HOST_RECORD_RESOURCE_PATH = "host-record";
  String DELEGATE_DATA_COLLECTION_TASK = "delegate-data-collection-task";
  String VERIFICATION_SERVICE_SECRET = "VERIFICATION_SERVICE_SECRET";
  String CV_NEXT_GEN_SERVICE_ENDPOINTS_PREFIX = CV_NEXTGEN_RESOURCE_PREFIX + "/service";
  // TODO: move this to duration
  long CV_ANALYSIS_WINDOW_MINUTES = 5;
  String CV_DATA_COLLECTION_PATH = CV_NEXT_GEN_SERVICE_ENDPOINTS_PREFIX + "/cv-data-collection-task";
  String CD_CURRENT_GEN_CHANGE_EVENTS_PATH = CV_NEXT_GEN_SERVICE_ENDPOINTS_PREFIX + "/change-events";
  Duration DATA_COLLECTION_DELAY = Duration.ofMinutes(2);
  String PERFORMANCE_PACK_IDENTIFIER = "Performance";
  String ERRORS_PACK_IDENTIFIER = "Errors";
  String INFRASTRUCTURE_PACK_IDENTIFIER = "Infrastructure";
  String CUSTOM_PACK_IDENTIFIER = "Custom";
  String SPLUNK_RESOURCE_PATH = "cv-nextgen/splunk/";
  String SPLUNK_SAVED_SEARCH_PATH = "saved-searches";
  String SPLUNK_VALIDATION_RESPONSE_PATH = "validation";
  String ACTIVITY_RESOURCE = "activity";
  String KUBERNETES_RESOURCE = "kubernetes";
  String CHANGE_EVENT_RESOURCE = "change-event";
  int CVNG_MAX_PARALLEL_THREADS = 20;
  int CVNG_TIMELINE_BUCKET_COUNT = 48;
  int SRM_STATEMACHINE_MAX_THREADS = 20;
  int LOG_RECORD_THRESHOLD = 500;
  String ACCOUNT_IDENTIFIER_KEY = "accountIdentifier";
  String ORG_IDENTIFIER_KEY = "orgIdentifier";
  String PROJECT_IDENTIFIER_KEY = "projectIdentifier";
  String PROJECT_PATH = "account/{" + ACCOUNT_IDENTIFIER_KEY + "}/org/{" + ORG_IDENTIFIER_KEY + "}/project/{"
      + PROJECT_IDENTIFIER_KEY + "}";
  String CHANGE_EVENT_PATH = PROJECT_PATH + "/change-event";
  String VERIFY_STEP_PATH = PROJECT_PATH + "/verify-step";
  String MONITORED_SERVICE_YAML_ROOT = "monitoredService";
}
