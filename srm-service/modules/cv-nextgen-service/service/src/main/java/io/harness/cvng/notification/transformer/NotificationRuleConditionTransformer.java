/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.NotificationRule;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class NotificationRuleConditionTransformer<E extends NotificationRule, S
                                                               extends NotificationRuleConditionSpec> {
  @Inject
  Map<CVNGNotificationChannelType, NotificationMethodTransformer>
      notificationChannelTypeNotificationMethodTransformerMap;

  public abstract E getEntity(ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO);

  public final NotificationRuleDTO getDto(E notificationRule) {
    return NotificationRuleDTO.builder()
        .orgIdentifier(notificationRule.getOrgIdentifier())
        .projectIdentifier(notificationRule.getProjectIdentifier())
        .identifier(notificationRule.getIdentifier())
        .name(notificationRule.getName())
        .type(notificationRule.getType())
        .notificationMethod(notificationChannelTypeNotificationMethodTransformerMap
                                .get(notificationRule.getNotificationMethod().getType())
                                .getDTONotificationMethod(notificationRule.getNotificationMethod()))
        .conditions(getConditions((List<NotificationRuleConditionSpec>) getSpec(notificationRule)))
        .build();
  }

  protected abstract List<S> getSpec(E notificationRule);

  private List<NotificationRuleCondition> getConditions(List<NotificationRuleConditionSpec> specs) {
    return specs.stream()
        .map(spec -> NotificationRuleCondition.builder().type(spec.getType()).spec(spec).build())
        .collect(Collectors.toList());
  }
}
