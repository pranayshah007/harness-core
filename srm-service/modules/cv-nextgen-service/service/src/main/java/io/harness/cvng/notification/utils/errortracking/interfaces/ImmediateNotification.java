/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;

import io.harness.cvng.beans.errortracking.CriticalEventType;
import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.utils.errortracking.ImmediateEmailNotification;
import io.harness.cvng.notification.utils.errortracking.ImmediateSlackNotification;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public interface ImmediateNotification {
  String ET_ARC_SCREEN_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/eventsummary/events/arc?request=%s&environment=%s&harnessService=%s&dep=%s&fromTimestamp=%s&toTimestamp=%s";

  static Map<String, String> getNotificationDataMap(ErrorTrackingHitSummary errorTrackingHitSummary, String baseLinkUrl,
      MonitoredService monitoredService, NotificationRule notificationRule, String environmentId) {
    Map<String, String> templateDataMap = new HashMap<>();
    if (notificationRule.getNotificationMethod().getType() == CVNGNotificationChannelType.SLACK) {
      templateDataMap = ImmediateSlackNotification.getNotificationDataMap(
          errorTrackingHitSummary, baseLinkUrl, monitoredService, notificationRule, environmentId);
    } else if (notificationRule.getNotificationMethod().getType() == CVNGNotificationChannelType.EMAIL) {
      templateDataMap = ImmediateEmailNotification.getNotificationDataMap(
          errorTrackingHitSummary, baseLinkUrl, monitoredService, notificationRule, environmentId);
    }
    return templateDataMap;
  }

  static String getNotificationEventTriggerList() {
    return Arrays.stream(CriticalEventType.values())
        .filter(type -> type != CriticalEventType.ANY)
        .map(CriticalEventType::getDisplayName)
        .collect(Collectors.joining(", "));
  }

  static String getEventStatus() {
    return ErrorTrackingEventStatus.NEW_EVENTS.getDisplayName();
  }

  static String buildArcScreenUrlWithParameters(ErrorTrackingHitSummary errorTrackingHitSummary, String baseLinkUrl,
      String account, String org, String project, Integer request, String env, String service, String deployment) {
    long firstSeen = errorTrackingHitSummary.getFirstSeen().getTime() / 1000;

    // Subtract and add 30 seconds to simulate a 1-minute window with the firstSeen date in the middle for the arcscreen
    long fromTime = firstSeen - 30;
    long toTime = firstSeen + 30;

    return String.format(ET_ARC_SCREEN_FORMAT, baseLinkUrl, account, CET_MODULE_NAME, org, project, request, env,
        service, deployment, fromTime, toTime);
  }
}
