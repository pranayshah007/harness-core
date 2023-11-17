/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;

import io.harness.cvng.core.entities.MonitoredService;
import io.harness.exception.InvalidArgumentsException;

import java.util.Arrays;
import java.util.Map;

public interface ErrorTrackingNotification {
  String ET_MONITORED_SERVICE_URL_FORMAT = "%s/account/%s/%s/orgs/%s/projects/%s/etmonitoredservices/edit/%s";
  String EVENT_VERSION_LABEL = "Events appeared on the deployment version ";

  String ENVIRONMENT_NAME = "ENVIRONMENT_NAME";
  String EVENT_STATUS = "EVENT_STATUS";
  String NOTIFICATION_EVENT_TRIGGER_LIST = "NOTIFICATION_EVENT_TRIGGER_LIST";

  static String getNotificationUrl(String baseUrl, MonitoredService monitoredService) {
    return String.format(ET_MONITORED_SERVICE_URL_FORMAT, baseUrl, monitoredService.getAccountId(), CET_MODULE_NAME,
        monitoredService.getOrgIdentifier(), monitoredService.getProjectIdentifier(), monitoredService.getIdentifier());
  }

  static void validateTemplateValues(Map<String, String> templateData, String... excludedKeys) {
    StringBuilder emptyValues = new StringBuilder();
    templateData.entrySet()
        .stream()
        .filter(entry -> Arrays.stream(excludedKeys).noneMatch(excluded -> excluded.equals(entry.getKey())))
        .filter(entry -> entry.getValue().isEmpty())
        .map(entry -> " " + entry.getKey())
        .forEach(emptyValues::append);

    if (!emptyValues.isEmpty()) {
      throw new InvalidArgumentsException(
          "The following template variables are empty where empty is not allowed:" + emptyValues);
    }
  }
}
