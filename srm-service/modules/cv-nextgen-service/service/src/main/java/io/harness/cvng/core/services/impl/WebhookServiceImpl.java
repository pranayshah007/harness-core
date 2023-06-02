/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.HeaderConfig;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.PagerDutyEventMetaData;
import io.harness.cvng.core.beans.CustomChangeWebhookPayload;
import io.harness.cvng.core.beans.PagerDutyWebhookEvent;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.PagerDutyWebhook;
import io.harness.cvng.core.entities.PagerDutyWebhook.PagerDutyWebhookKeys;
import io.harness.cvng.core.entities.Webhook;
import io.harness.cvng.core.entities.Webhook.WebhookKeys;
import io.harness.cvng.core.jobs.CustomChangeEventPublisherService;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.cvng.utils.SRMServiceAuthIfHasApiKey;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import groovy.util.logging.Slf4j;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.HttpHeaders;

@Slf4j
public class WebhookServiceImpl implements WebhookService {
  @Inject private HPersistence hPersistence;
  @Inject private ChangeEventService changeEventService;

  @Inject private CustomChangeEventPublisherService customChangeEventPublisherService;

  @Inject AccessControlClient accessControlClient;

  public static final String MONITORED_SERVICE = "MONITOREDSERVICE";

  public static final String EDIT_PERMISSION = "chi_monitoredservice_edit";

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

  @Override
  @SRMServiceAuthIfHasApiKey
  public void handleCustomChangeWebhook(ProjectParams projectParams, String monitoredServiceIdentifier,
      String changeSourceIdentifier, CustomChangeWebhookPayload customChangeWebhookPayload) {
    customChangeEventPublisherService.publishCustomChangeEvent(
        projectParams, monitoredServiceIdentifier, changeSourceIdentifier, customChangeWebhookPayload);
  }

  @Override
  public void checkAuthorization(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String monitoredServiceIdentifier, HttpHeaders httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));

    boolean hasApiKey = false;
    for (HeaderConfig headerConfig : headerConfigs) {
      if (headerConfig.getKey().equalsIgnoreCase(X_API_KEY)) {
        hasApiKey = true;
        break;
      }
    }
    if (hasApiKey) {
      accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of(MONITORED_SERVICE, monitoredServiceIdentifier), EDIT_PERMISSION);
    } else {
      throw new InvalidRequestException(
          String.format("Authorization is mandatory for custom change in %s:%s:%s. Please add %s header in the request",
              accountIdentifier, orgIdentifier, projectIdentifier, X_API_KEY));
    }
  }
}
