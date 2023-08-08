/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.utils;

import java.time.Duration;

public class NotificationRuleConstants {
  public static final Duration COOL_OFF_DURATION = Duration.ofHours(1);
  public static final String CURRENT_HEALTH_SCORE = "CURRENT_HEALTH_SCORE";
  public static final String CHANGE_EVENT_TYPE = "CHANGE_EVENT_TYPE";
  public static final String REMAINING_PERCENTAGE = "REMAINING_PERCENTAGE";
  public static final String REMAINING_MINUTES = "REMAINING_MINUTES";
  public static final String BURN_RATE = "BURN_RATE";
  public static final String ENTITY_IDENTIFIER = "ENTITY_IDENTIFIER";
  public static final String SERVICE_IDENTIFIER = "SERVICE_IDENTIFIER";
  public static final String MONITORED_SERVICE_IDENTIFIER = "MONITORED_SERVICE_IDENTIFIER";
  public static final String ENTITY_NAME = "ENTITY_NAME";
  public static final String MONITORED_SERVICE_NAME = "MONITORED_SERVICE_NAME";
  public static final String HEADER_MESSAGE = "HEADER_MESSAGE";
  public static final String TRIGGER_MESSAGE = "TRIGGER_MESSAGE";
  public static final String ANOMALOUS_METRIC = "ANOMALOUS_METRIC";
  public static final String SLO_PERFORMANCE_SLACK = "SLO_PERFORMANCE_SLACK";

  public static final String SLO_PERFORMANCE_EMAIL = "SLO_PERFORMANCE_EMAIL";

  public static final String SLO_PERFORMANCE = "SLO_PERFORMANCE";

  public static final String MS_HEALTH_REPORT = "MS_HEALTH_REPORT";
  public static final String MODULE_NAME = "cv";
  public static final String CET_MODULE_NAME = "cet";

  public static final String THEME_COLOR = "#EC372E";
  public static final String COLOR = "COLOR";
  public static final String SERVICE_NAME = "SERVICE_NAME";
  public static final String ACCOUNT_NAME = "ACCOUNT_NAME";
  public static final String ORG_NAME = "ORG_NAME";
  public static final String PROJECT_NAME = "PROJECT_NAME";
  public static final String START_TS_SECS = "START_TS_SECS";
  public static final String START_DATE = "START_DATE";
  public static final String URL = "URL";

  public static final String SLO_NAME = "SLO_NAME";

  public static final String PIPELINE_NAME = "PIPELINE_NAME";

  public static final String PIPELINE_ID = "PIPELINE_NAME";

  public static final String PLAN_EXECUTION_ID = "PLAN_EXECUTION_ID";

  public static final String STAGE_STEP_ID = "STAGE_STEP_ID";

  public static final String SERVICE_HEALTH_SUMMARY = "SERVICE_HEALTH_SUMMARY";

  public static final String SLO_SUMMARY = "SLO_SUMMARY";

  public static final String SLO_TARGET = "SLO_TARGET";
  public static final String PAST_SLO_TARGET = "PAST_SLO_TARGET";
  public static final String CURRENT_SLO_TARGET = "CURRENT_SLO_TARGET";
  public static final String ERROR_BUDGET_BURNED = "ERROR_BUDGET_BURNED";
  public static final String TOTAL_CE_COUNT = "TOTAL_CE_COUNT";

  public static final String MONITORED_SERVICE_URL = "MONITORED_SERVICE_URL";
  public static final String SLO_URL = "SLO_URL";
  public static final String MONITORED_SERVICE_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=ServiceHealth&notificationTime=%s";
  public static final String SLOS_FOR_MONITORED_SERVICE_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=SLOs";
  public static final String PROJECT_SIMPLE_SLO_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?tab=Details&sloType=Simple&notificationTime=%s";

  public static final String PROJECT_COMPOSITE_SLO_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?tab=Details&sloType=Composite&notificationTime=%s";

  public static final String ACCOUNT_COMPOSITE_SLO_URL_FORMAT =
      "%s/account/%s/%s/slos/%s?tab=Details&sloType=Composite&notificationTime=%s";

  public static final String NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE =
      "No metric has been assigned to this monitored service";
  public static final String NO_SLO_ASSOCIATED_WITH_MONITORED_SERVICE =
      "No SLO has been associated with this monitored service.\n";
  public static final int N_TOP_MOST_ANOMALOUS_METRICS = 5;
  public static final int ANOMALOUS_METRICS_PAGE_NUMBER = 0;

  public static final String ANALYSIS_DURATION = "ANALYSIS_DURATION";

  public static final String ANALYSIS_STARTED_AT = "ANALYSIS_STARTED_AT";

  public static final String ANALYSIS_PIPELINE_NAME = "ANALYSIS_PIPELINE_NAME";

  public static final String PIPELINE_URL_FORMAT =
      "/account/%s/cd/orgs/%s/projects/%s/pipelines/%s/executions/%s/pipeline?stage=%s";

  public static final String ANALYSIS_ENDED_AT = "ANALYSIS_ENDED_AT";

  public static String SLO_PERFORMANCE_SECTION_FOR_FIREHYDRANT_SLACK = "SLO Name: <${SLO_URL}|${SLO_NAME}>\n"
      + "SLO Target: ${SLO_TARGET}%\n"
      + "SLI 1 hour before: ${PAST_SLO_TARGET}%\n"
      + "SLI now: ${CURRENT_SLO_TARGET}%\n"
      + "Error budget burned: ${ERROR_BUDGET_BURNED} min\n\n";

  public static String SLO_PERFORMANCE_SECTION_FOR_ANALYSIS_REPORT_PAGERDUTY = "SLO Name: ${SLO_NAME}\n"
      + "SLO Target: ${SLO_TARGET}%\n"
      + "SLI at the start of the analysis: ${PAST_SLO_TARGET}%\n"
      + "SLI at the end of the analysis: ${CURRENT_SLO_TARGET}%\n"
      + "Error budget burned: ${ERROR_BUDGET_BURNED} min\n\n";

  public static String SLO_PERFORMANCE_SECTION_FOR_ANALYSIS_REPORT_EMAIL =
      "<p>\n<div>\n  <div>\n    <b>SLO Name</b>: <a href=\\\"${SLO_URL}\\\" target=\\\"_blank\\\" style=\\\"text-decoration: none; color: #1a89bf;\\\">\n      <b>${SLO_NAME}</b>\n    </a>\n  </div>\n  <div style=\\\"color: #0B0B0D;\\\">\n    <b>SLO Target</b>: ${SLO_TARGET}%\n  </div>\n  <div style=\\\"color: #0B0B0D;\\\">\n    <b>SLI at the start of the analysis</b>: ${PAST_SLO_TARGET}%\n  </div>\n  <div style=\\\"color: #0B0B0D;\\\">\n    <b>SLI at the end of the analysis</b>: ${CURRENT_SLO_TARGET}%\n  </div>\n  <div style=\\\"color: #0B0B0D;\\\">\n    <b>Error Budget Remaining</b>: ${ERROR_BUDGET_BURNED} min\n  </div>\n</div>\n</p>";

  public static String SLO_PERFORMANCE_SECTION_FOR_ANALYSIS_REPORT_SLACK = "SLO Name: <${SLO_URL}|${SLO_NAME}>\n"
      + "SLO Target: ${SLO_TARGET}%\n"
      + "SLI at the start of the analysis: ${PAST_SLO_TARGET}%\n"
      + "SLI at the end of the analysis: ${CURRENT_SLO_TARGET}%\n"
      + "Error budget burned: ${ERROR_BUDGET_BURNED} min\n\n";
}
