/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.entities;

import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.beans.MonitoredServiceChangeEventType;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("MONITORED_SERVICE")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "MonitoredServiceNotificationRuleKeys")
@EqualsAndHashCode(callSuper = true)
public class MonitoredServiceNotificationRule extends NotificationRule {
  @NonNull List<MonitoredServiceNotificationRuleCondition> conditions;

  @Override
  public NotificationRuleType getType() {
    return NotificationRuleType.MONITORED_SERVICE;
  }

  @Data
  @SuperBuilder
  public abstract static class MonitoredServiceNotificationRuleCondition extends NotificationRuleConditionEntity {}

  @SuperBuilder
  @Data
  public static class MonitoredServiceChangeImpactCondition extends MonitoredServiceNotificationRuleCondition {
    public final NotificationRuleConditionType type = NotificationRuleConditionType.CHANGE_IMPACT;
    @Deprecated List<MonitoredServiceChangeEventType> changeEventTypes;
    List<ChangeCategory> changeCategories;
    @NonNull Double threshold;
    @NonNull long period;
  }

  @SuperBuilder
  @Data
  public static class MonitoredServiceHealthScoreCondition extends MonitoredServiceNotificationRuleCondition {
    public final NotificationRuleConditionType type = NotificationRuleConditionType.HEALTH_SCORE;
    @NonNull Double threshold;
    @NonNull long period;
  }

  @SuperBuilder
  @Data
  public static class MonitoredServiceChangeObservedCondition extends MonitoredServiceNotificationRuleCondition {
    public final NotificationRuleConditionType type = NotificationRuleConditionType.CHANGE_OBSERVED;
    @Deprecated List<MonitoredServiceChangeEventType> changeEventTypes;
    List<ChangeCategory> changeCategories;
  }

  @SuperBuilder
  @Data
  public static class MonitoredServiceCodeErrorCondition extends MonitoredServiceNotificationRuleCondition {
    public final NotificationRuleConditionType type = NotificationRuleConditionType.CODE_ERRORS;
    @NonNull List<ErrorTrackingEventType> errorTrackingEventTypes;
    @NonNull List<ErrorTrackingEventStatus> errorTrackingEventStatus;
  }

  public static class MonitoredServiceNotificationRuleUpdatableEntity
      extends NotificationRuleUpdatableEntity<MonitoredServiceNotificationRule, MonitoredServiceNotificationRule> {
    @Override
    public void setUpdateOperations(UpdateOperations<MonitoredServiceNotificationRule> updateOperations,
        MonitoredServiceNotificationRule monitoredServiceNotificationRule) {
      setCommonOperations(updateOperations, monitoredServiceNotificationRule);
      updateOperations.set(
          MonitoredServiceNotificationRuleKeys.conditions, monitoredServiceNotificationRule.getConditions());
    }
  }
}
