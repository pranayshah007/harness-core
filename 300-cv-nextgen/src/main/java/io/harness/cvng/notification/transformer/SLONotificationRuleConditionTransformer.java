/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.ErrorBudgetBurnRateConditionSpec;
import io.harness.cvng.notification.beans.ErrorBudgetRemainingMinutesConditionSpec;
import io.harness.cvng.notification.beans.ErrorBudgetRemainingPercentageConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.SLONotificationRule;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetBurnRateCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetRemainingMinutesCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetRemainingPercentageCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleCondition;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SLONotificationRuleConditionTransformer
    extends NotificationRuleConditionTransformer<SLONotificationRule, NotificationRuleConditionSpec> {
  @Inject Map<CVNGNotificationChannelType, NotificationMethodTransformer> channelTypeNotificationMethodTransformerMap;

  @Override
  public SLONotificationRule getEntity(ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO) {
    return SLONotificationRule.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(notificationRuleDTO.getIdentifier())
        .name(notificationRuleDTO.getName())
        .type(NotificationRuleType.SLO)
        .notificationMethod(
            channelTypeNotificationMethodTransformerMap.get(notificationRuleDTO.getNotificationMethod().getType())
                .getEntityNotificationMethod(notificationRuleDTO.getNotificationMethod().getSpec()))
        .conditions(notificationRuleDTO.getConditions()
                        .stream()
                        .map(condition -> getEntityCondition(condition))
                        .collect(Collectors.toList()))
        .build();
  }

  @Override
  protected List<NotificationRuleConditionSpec> getSpec(SLONotificationRule notificationRule) {
    return notificationRule.getConditions()
        .stream()
        .map(condition -> getDTOCondition(condition))
        .collect(Collectors.toList());
  }

  private SLONotificationRuleCondition getEntityCondition(NotificationRuleCondition condition) {
    switch (condition.getSpec().getType()) {
      case ERROR_BUDGET_REMAINING_PERCENTAGE:
        ErrorBudgetRemainingPercentageConditionSpec remainingPercentageConditionSpec =
            (ErrorBudgetRemainingPercentageConditionSpec) condition.getSpec();
        return SLOErrorBudgetRemainingPercentageCondition.builder()
            .threshold(remainingPercentageConditionSpec.getThreshold())
            .build();
      case ERROR_BUDGET_REMAINING_MINUTES:
        ErrorBudgetRemainingMinutesConditionSpec remainingMinutesConditionSpec =
            (ErrorBudgetRemainingMinutesConditionSpec) condition.getSpec();
        return SLOErrorBudgetRemainingMinutesCondition.builder()
            .threshold(remainingMinutesConditionSpec.getThreshold())
            .build();
      case ERROR_BUDGET_BURN_RATE:
        ErrorBudgetBurnRateConditionSpec burnRateConditionSpec = (ErrorBudgetBurnRateConditionSpec) condition.getSpec();
        return SLOErrorBudgetBurnRateCondition.builder()
            .threshold(burnRateConditionSpec.getThreshold())
            .lookBackDuration(
                NotificationRuleCommonUtils.getDurationInMillis(burnRateConditionSpec.getLookBackDuration()))
            .build();
      default:
        throw new InvalidArgumentsException("Invalid SLO Notification Rule Condition Type: " + condition.getType());
    }
  }

  private NotificationRuleConditionSpec getDTOCondition(SLONotificationRuleCondition condition) {
    switch (condition.getType()) {
      case ERROR_BUDGET_REMAINING_PERCENTAGE:
        SLOErrorBudgetRemainingPercentageCondition remainingPercentageCondition =
            (SLOErrorBudgetRemainingPercentageCondition) condition;
        return ErrorBudgetRemainingPercentageConditionSpec.builder()
            .threshold(remainingPercentageCondition.getThreshold())
            .build();
      case ERROR_BUDGET_REMAINING_MINUTES:
        SLOErrorBudgetRemainingMinutesCondition remainingMinutesCondition =
            (SLOErrorBudgetRemainingMinutesCondition) condition;
        return ErrorBudgetRemainingMinutesConditionSpec.builder()
            .threshold(remainingMinutesCondition.getThreshold())
            .build();
      case ERROR_BUDGET_BURN_RATE:
        SLOErrorBudgetBurnRateCondition burnRateCondition = (SLOErrorBudgetBurnRateCondition) condition;
        return ErrorBudgetBurnRateConditionSpec.builder()
            .threshold(burnRateCondition.getThreshold())
            .lookBackDuration(NotificationRuleCommonUtils.getDurationAsString(burnRateCondition.getLookBackDuration()))
            .build();
      default:
        throw new InvalidArgumentsException("Invalid SLO Notification Rule Condition Type: " + condition.getType());
    }
  }
}
