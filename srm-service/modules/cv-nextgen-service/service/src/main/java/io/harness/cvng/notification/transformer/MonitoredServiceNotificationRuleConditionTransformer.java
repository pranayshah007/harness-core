/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.ChangeImpactConditionSpec;
import io.harness.cvng.notification.beans.ChangeObservedConditionSpec;
import io.harness.cvng.notification.beans.ErrorTrackingConditionSpec;
import io.harness.cvng.notification.beans.HealthScoreConditionSpec;
import io.harness.cvng.notification.beans.MonitoredServiceChangeEventType;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceHealthScoreCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MonitoredServiceNotificationRuleConditionTransformer
    extends NotificationRuleConditionTransformer<MonitoredServiceNotificationRule, NotificationRuleConditionSpec> {
  @Inject
  Map<CVNGNotificationChannelType, NotificationMethodTransformer>
      notificationChannelTypeNotificationMethodTransformerMap;

  @Override
  public MonitoredServiceNotificationRule getEntity(
      ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO) {
    return MonitoredServiceNotificationRule.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(notificationRuleDTO.getIdentifier())
        .name(notificationRuleDTO.getName())
        .type(NotificationRuleType.MONITORED_SERVICE)
        .notificationMethod(notificationChannelTypeNotificationMethodTransformerMap
                                .get(notificationRuleDTO.getNotificationMethod().getType())
                                .getEntityNotificationMethod(notificationRuleDTO.getNotificationMethod().getSpec()))
        .conditions(
            notificationRuleDTO.getConditions().stream().map(this::getEntityCondition).collect(Collectors.toList()))
        .build();
  }

  @Override
  protected List<NotificationRuleConditionSpec> getSpec(MonitoredServiceNotificationRule notificationRule) {
    return notificationRule.getConditions().stream().map(this::getDTOCondition).collect(Collectors.toList());
  }

  private MonitoredServiceNotificationRuleCondition getEntityCondition(NotificationRuleCondition condition) {
    switch (condition.getSpec().getType()) {
      case CHANGE_IMPACT:
        ChangeImpactConditionSpec changeImpactConditionSpec = (ChangeImpactConditionSpec) condition.getSpec();
        return MonitoredServiceChangeImpactCondition.builder()
            .changeCategories(isNotEmpty(changeImpactConditionSpec.getChangeCategories())
                    ? changeImpactConditionSpec.getChangeCategories()
                    : changeImpactConditionSpec.getChangeEventTypes()
                          .stream()
                          .map(MonitoredServiceChangeEventType::convertMonitoredServiceChangeEventTypeToChangeCategory)
                          .collect(Collectors.toList()))
            .threshold(changeImpactConditionSpec.getThreshold())
            .period(NotificationRuleCommonUtils.getDurationInMillis(changeImpactConditionSpec.getPeriod()))
            .build();
      case HEALTH_SCORE:
        HealthScoreConditionSpec healthScoreConditionSpec = (HealthScoreConditionSpec) condition.getSpec();
        return MonitoredServiceHealthScoreCondition.builder()
            .threshold(healthScoreConditionSpec.getThreshold())
            .period(NotificationRuleCommonUtils.getDurationInMillis(healthScoreConditionSpec.getPeriod()))
            .build();
      case CHANGE_OBSERVED:
        ChangeObservedConditionSpec changeObservedConditionSpec = (ChangeObservedConditionSpec) condition.getSpec();
        return MonitoredServiceChangeObservedCondition.builder()
            .changeCategories(isNotEmpty(changeObservedConditionSpec.getChangeCategories())
                    ? changeObservedConditionSpec.getChangeCategories()
                    : changeObservedConditionSpec.getChangeEventTypes()
                          .stream()
                          .map(MonitoredServiceChangeEventType::convertMonitoredServiceChangeEventTypeToChangeCategory)
                          .collect(Collectors.toList()))
            .build();
      case CODE_ERRORS:
        ErrorTrackingConditionSpec errorTrackingConditionSpec = (ErrorTrackingConditionSpec) condition.getSpec();
        return MonitoredServiceCodeErrorCondition.builder()
            .errorTrackingEventTypes(errorTrackingConditionSpec.getErrorTrackingEventTypes())
            .errorTrackingEventStatus(errorTrackingConditionSpec.getErrorTrackingEventStatus())
            .build();
      default:
        throw new InvalidArgumentsException(
            "Invalid Monitored Service Notification Rule Condition Type: " + condition.getType());
    }
  }

  private NotificationRuleConditionSpec getDTOCondition(MonitoredServiceNotificationRuleCondition condition) {
    switch (condition.getType()) {
      case CHANGE_IMPACT:
        MonitoredServiceChangeImpactCondition changeImpactCondition = (MonitoredServiceChangeImpactCondition) condition;
        return ChangeImpactConditionSpec.builder()
            .changeEventTypes(
                changeImpactCondition.getChangeCategories()
                    .stream()
                    .map(MonitoredServiceChangeEventType::convertChangeCategoryToMonitoredServiceChangeEventType)
                    .collect(Collectors.toList()))
            .changeCategories(changeImpactCondition.getChangeCategories())
            .threshold(changeImpactCondition.getThreshold())
            .period(NotificationRuleCommonUtils.getDurationAsString(changeImpactCondition.getPeriod()))
            .build();
      case HEALTH_SCORE:
        MonitoredServiceHealthScoreCondition healthScoreCondition = (MonitoredServiceHealthScoreCondition) condition;
        return HealthScoreConditionSpec.builder()
            .threshold(healthScoreCondition.getThreshold())
            .period(NotificationRuleCommonUtils.getDurationAsString(healthScoreCondition.getPeriod()))
            .build();
      case CHANGE_OBSERVED:
        MonitoredServiceChangeObservedCondition changeObservedCondition =
            (MonitoredServiceChangeObservedCondition) condition;
        return ChangeObservedConditionSpec.builder()
            .changeEventTypes(
                changeObservedCondition.getChangeCategories()
                    .stream()
                    .map(MonitoredServiceChangeEventType::convertChangeCategoryToMonitoredServiceChangeEventType)
                    .collect(Collectors.toList()))
            .changeCategories(changeObservedCondition.getChangeCategories())
            .build();
      case CODE_ERRORS:
        MonitoredServiceCodeErrorCondition codeErrorCondition = (MonitoredServiceCodeErrorCondition) condition;
        return ErrorTrackingConditionSpec.builder()
            .errorTrackingEventTypes(codeErrorCondition.getErrorTrackingEventTypes())
            .errorTrackingEventStatus(codeErrorCondition.getErrorTrackingEventStatus())
            .build();
      default:
        throw new InvalidArgumentsException(
            "Invalid Monitored Service Notification Rule Condition Type: " + condition.getType());
    }
  }
}
