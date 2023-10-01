/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service.api;

import io.harness.notification.entities.NotificationRule;

import java.util.List;
import javax.validation.Valid;

public interface NotificationRuleManagementService {
  NotificationRule create(@Valid NotificationRule notificationRule);

  NotificationRule update(@Valid NotificationRule notificationRule);

  NotificationRule get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String notificationRuleNameIdentifier);

  NotificationRule get(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  List<NotificationRule> list(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  boolean delete(@Valid NotificationRule notificationRule);
}
