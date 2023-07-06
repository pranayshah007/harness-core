/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.entities;

import io.harness.cvng.notification.beans.NotificationRuleConditionType;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class FireHydrantReportNotificationCondition extends NotificationRuleConditionEntity {
  @Override
  public NotificationRuleConditionType getType() {
    return NotificationRuleConditionType.FIRE_HYDRANT_REPORT;
  }
}
