/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.utils.errortracking.DefinedFilterEmailNotification;
import io.harness.cvng.notification.utils.errortracking.DefinedFilterSlackNotification;
import io.harness.cvng.notification.utils.errortracking.SavedFilterEmailNotification;
import io.harness.cvng.notification.utils.errortracking.SavedFilterSlackNotification;

import java.util.HashMap;
import java.util.Map;

public interface AggregatedNotification {
  static Map<String, String> getNotificationDataMap(ErrorTrackingNotificationData notificationData,
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition, String baseLinkUrl,
      MonitoredService monitoredService, NotificationRule notificationRule, String environmentId) {
    Map<String, String> templateDataMap = new HashMap<>();
    if (notificationRule.getNotificationMethod().getType() == CVNGNotificationChannelType.SLACK) {
      if (notificationData.getFilter() == null) {
        templateDataMap.putAll(DefinedFilterSlackNotification.getNotificationDataMap(
            notificationData, codeErrorCondition, baseLinkUrl, monitoredService, notificationRule, environmentId));
      } else {
        templateDataMap.putAll(SavedFilterSlackNotification.getNotificationDataMap(
            notificationData, codeErrorCondition, baseLinkUrl, monitoredService, notificationRule, environmentId));
      }

    } else if (notificationRule.getNotificationMethod().getType() == CVNGNotificationChannelType.EMAIL) {
      if (notificationData.getFilter() == null) {
        templateDataMap.putAll(DefinedFilterEmailNotification.getNotificationDataMap(
            notificationData, codeErrorCondition, baseLinkUrl, monitoredService, notificationRule, environmentId));
      } else {
        templateDataMap.putAll(SavedFilterEmailNotification.getNotificationDataMap(
            notificationData, codeErrorCondition, baseLinkUrl, monitoredService, notificationRule, environmentId));
      }
    }
    return templateDataMap;
  }
}
