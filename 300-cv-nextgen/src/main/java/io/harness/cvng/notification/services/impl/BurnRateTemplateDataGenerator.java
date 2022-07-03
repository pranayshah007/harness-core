/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.BURN_RATE;
import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getDurationAsStringWithoutSuffix;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetBurnRateCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils.NotificationMessage;

import java.util.Map;

public class BurnRateTemplateDataGenerator
    extends NotificationRuleTemplateDataGenerator<SLOErrorBudgetBurnRateCondition> {
  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, String name, String identifier,
      String serviceIdentifier, SLOErrorBudgetBurnRateCondition condition, NotificationMessage notificationMessage) {
    Map<String, String> templateData = getCommonTemplateData(projectParams, identifier, serviceIdentifier, condition);

    String headerMessage = getHeaderMessage(notificationMessage);
    String triggerMessage = getTriggerMessage(condition);

    templateData.put("SLO_NAME", name);
    templateData.put("HEADER_MESSAGE", headerMessage);
    templateData.put("TRIGGER_MESSAGE", triggerMessage);

    return templateData;
  }

  private String getHeaderMessage(NotificationMessage notificationMessage) {
    return "current burn rate is " + notificationMessage.getTemplateDataMap().get(BURN_RATE) + "% for";
  }

  private String getTriggerMessage(SLOErrorBudgetBurnRateCondition condition) {
    String durationAsString = getDurationAsStringWithoutSuffix(condition.getLookBackDuration());
    return "When Error Budget burn rate goes above " + condition.getThreshold() + "% in the last " + durationAsString
        + " minutes";
  }
}
