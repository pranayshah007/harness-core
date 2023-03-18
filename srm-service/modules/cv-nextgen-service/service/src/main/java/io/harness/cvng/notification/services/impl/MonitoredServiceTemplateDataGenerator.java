/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;

public abstract class MonitoredServiceTemplateDataGenerator<T extends MonitoredServiceNotificationRuleCondition>
    extends NotificationRuleTemplateDataGenerator<T> {
  @Override
  public String getEntityName() {
    return MONITORED_SERVICE_NAME;
  }

  private static final String MONITORED_SERVICE_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=ServiceHealth&notificationTime=%s";

  @Override
  public String getUrl(
      String baseUrl, ProjectParams projectParams, String identifier, NotificationRuleType type, Long endTime) {
    return String.format(MONITORED_SERVICE_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier, endTime);
  }
}
