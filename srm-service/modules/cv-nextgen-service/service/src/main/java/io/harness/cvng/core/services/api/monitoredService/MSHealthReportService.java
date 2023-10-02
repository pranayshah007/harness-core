/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api.monitoredService;

import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.entities.NotificationRuleConditionEntity;

import java.time.Instant;
import java.util.Map;

public interface MSHealthReportService {
  MSHealthReport getMSHealthReport(ProjectParams projectParams, String monitoredServiceIdentifier, Instant startTime);

  void sendReportNotification(ProjectParams projectParams, Map<String, Object> entityDetails, NotificationRuleType type,
      NotificationRuleConditionEntity condition, NotificationRule.CVNGNotificationChannel notificationChannel,
      String monitoredServiceIdentifier);
}
