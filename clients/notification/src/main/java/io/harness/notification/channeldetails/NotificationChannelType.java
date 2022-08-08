/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channeldetails;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationChannelType {
  public static final String EMAIL = "Email";
  public static final String PAGERDUTY = "PagerDuty";
  public static final String SLACK = "Slack";
  public static final String MSTEAMS = "MsTeams";
}
