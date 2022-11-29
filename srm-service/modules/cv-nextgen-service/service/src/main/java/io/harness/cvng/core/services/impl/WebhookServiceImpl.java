/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.PagerDutyEventMetaData;
import io.harness.cvng.core.beans.PagerDutyWebhookEvent;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.PagerDutyWebhook;
import io.harness.cvng.core.entities.PagerDutyWebhook.PagerDutyWebhookKeys;
import io.harness.cvng.core.entities.Webhook;
import io.harness.cvng.core.entities.Webhook.WebhookKeys;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import groovy.util.logging.Slf4j;

@Slf4j
public class WebhookServiceImpl implements WebhookService {
  @Inject private HPersistence hPersistence;
  @Inject private ChangeEventService changeEventService;

  @Override
  public void createPagerdutyWebhook(
      MonitoredServiceParams monitoredServiceParams, String token, String webhookId, String changeSourceId) {
    PagerDutyWebhook pagerDutyWebhook =
        PagerDutyWebhook.builder()
            .accountId(monitoredServiceParams.getAccountIdentifier())
            .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
            .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
            .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
            .pagerdutyChangeSourceId(changeSourceId)
            .token(token)
            .webhookId(webhookId)
            .build();
    hPersistence.save(pagerDutyWebhook);
  }

  @Override
  public PagerDutyWebhook getPagerdutyWebhook(ProjectParams projectParams, String changeSourceId) {
    return (PagerDutyWebhook) hPersistence.createQuery(Webhook.class)
        .disableValidation()
        .filter(PagerDutyWebhookKeys.pagerdutyChangeSourceId, changeSourceId)
        .filter(WebhookKeys.accountId, projectParams.getAccountIdentifier())
        .filter(WebhookKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(WebhookKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .get();
  }

  @Override
  public void deleteWebhook(Webhook webhook) {
    hPersistence.delete(webhook);
  }

  @Override
  public void handlePagerDutyWebhook(String token, PagerDutyWebhookEvent payload) {
    PagerDutyWebhook webhook =
        (PagerDutyWebhook) hPersistence.createQuery(Webhook.class).filter(WebhookKeys.token, token).get();
    PagerDutyEventMetaData eventMetaData = PagerDutyEventMetaData.builder()
                                               .eventId(payload.getEvent().getData().getId())
                                               .pagerDutyUrl(payload.getEvent().getData().getSelf())
                                               .title(payload.getEvent().getData().getTitle())
                                               .status(payload.getEvent().getData().getStatus())
                                               .triggeredAt(payload.getEvent().getTriggeredAt())
                                               .urgency(payload.getEvent().getData().getUrgency())
                                               .htmlUrl(payload.getEvent().getData().getHtmlUrl())
                                               .build();
    if (isNotEmpty(payload.getEvent().getData().getAssignees())) {
      eventMetaData.setAssignment(payload.getEvent().getData().getAssignees().get(0).getSummary());
      eventMetaData.setAssignmentUrl(payload.getEvent().getData().getAssignees().get(0).getHtmlUrl());
    }
    if (payload.getEvent().getData().getEscalationPolicy() != null) {
      eventMetaData.setEscalationPolicy(payload.getEvent().getData().getEscalationPolicy().getSummary());
      eventMetaData.setEscalationPolicyUrl(payload.getEvent().getData().getEscalationPolicy().getHtmlUrl());
    }
    if (payload.getEvent().getData().getPriority() != null) {
      eventMetaData.setPriority(payload.getEvent().getData().getPriority().getSummary());
    }
    ChangeEventDTO changeEventDTO = ChangeEventDTO.builder()
                                        .accountId(webhook.getAccountId())
                                        .orgIdentifier(webhook.getOrgIdentifier())
                                        .projectIdentifier(webhook.getProjectIdentifier())
                                        .monitoredServiceIdentifier(webhook.getMonitoredServiceIdentifier())
                                        .type(ChangeSourceType.PAGER_DUTY)
                                        .eventTime(eventMetaData.getTriggeredAt().toEpochMilli())
                                        .metadata(eventMetaData)
                                        .build();
    changeEventService.register(changeEventDTO);
  }
}
