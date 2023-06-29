/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationServiceConstants {
  public static final String JSON = ".json";
  public static final String TEST_MAIL_TEMPLATE = "email_test";
  public static final String TEST_SLACK_TEMPLATE = "slack_test";
  public static final String TEST_WEBHOOK_TEMPLATE = "webhook_test";
  public static final String TEST_PD_TEMPLATE = "pd_test";
  public static final String TEST_MSTEAMS_TEMPLATE = "msteams_test";
  public static final String MAILSERVICE = "mailservice";
  public static final String SLACKSERVICE = "slackservice";
  public static final String PAGERDUTYSERVICE = "pagerdutyservice";
  public static final String MSTEAMSSERVICE = "msteamsservice";
  public static final String WEBHOOKSERVICE = "webhookservice";
}
