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
  public static final String MONITORED_SERVICE_NAME = "MONITORED_SERVICE_NAME";
  public static final String SLO_NAME = "SLO_NAME";
  public static final String HEADER_MESSAGE = "HEADER_MESSAGE";
  public static final String TRIGGER_MESSAGE = "TRIGGER_MESSAGE";
  public static final String MODULE_NAME = "cv";

  public static final String THEME_COLOR = "#EC372E";
  public static final String COLOR = "COLOR";
  public static final String SERVICE_NAME = "SERVICE_NAME";
  public static final String ACCOUNT_NAME = "ACCOUNT_NAME";
  public static final String ORG_NAME = "ORG_NAME";
  public static final String PROJECT_NAME = "PROJECT_NAME";
  public static final String START_TS_SECS = "START_TS_SECS";
  public static final String START_DATE = "START_DATE";
  public static final String URL = "URL";
}
