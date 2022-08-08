/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationChannelType {
  public final String EMAIL = "Email";
  public final String PAGERDUTY = "PagerDuty";
  public final String SLACK = "Slack";
  public final String MSTEAMS = "MsTeams";
}
