/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.CHANGE_EVENT_TYPE;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.MonitoredServiceChangeEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils.NotificationMessage;

import java.util.Map;
import java.util.stream.Collectors;

public class ChangeObservedTemplateDataGenerator
    extends NotificationRuleTemplateDataGenerator<MonitoredServiceChangeObservedCondition> {
  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, String name, String identifier,
      String serviceIdentifier, MonitoredServiceChangeObservedCondition condition,
      NotificationMessage notificationMessage) {
    Map<String, String> templateData = getCommonTemplateData(projectParams, identifier, serviceIdentifier, condition);

    String headerMessage = getHeaderMessage(notificationMessage);
    String triggerMessage = getTriggerMessage(condition);

    templateData.put("MONITORED_SERVICE_NAME", name);
    templateData.put("HEADER_MESSAGE", headerMessage);
    templateData.put("TRIGGER_MESSAGE", triggerMessage);

    return templateData;
  }

  private String getHeaderMessage(NotificationMessage notificationMessage) {
    return "observed a change in a " + notificationMessage.getTemplateDataMap().get(CHANGE_EVENT_TYPE) + " for";
  }

  private String getTriggerMessage(MonitoredServiceChangeObservedCondition condition) {
    String changeEventTypeString = condition.getChangeEventTypes()
                                       .stream()
                                       .map(MonitoredServiceChangeEventType::getDisplayName)
                                       .collect(Collectors.joining(", "));
    return "When a change observed in a " + changeEventTypeString;
  }
}
