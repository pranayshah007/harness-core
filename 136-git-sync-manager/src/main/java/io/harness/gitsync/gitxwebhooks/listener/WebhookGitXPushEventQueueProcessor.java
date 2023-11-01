/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.listener;

import static io.harness.eventsframework.EventsFrameworkConstants.NG_GITX_WEBHOOK_PUSH_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventServiceImpl;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.queuePoller.AbstractHsqsQueueProcessor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)

public class WebhookGitXPushEventQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject GitXWebhookEventServiceImpl gitXWebhookEventService;
  @Inject @Named("webhookGitXPushEventQueueConfig") HsqsDequeueConfig webhookGitXPushEventQueueConfig;

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    try {
      log.info("Started processing webhook gitx push event for item id {}", message.getItemId());
      WebhookDTO webhookDTO = RecastOrchestrationUtils.fromJson(message.getPayload(), WebhookDTO.class);
      try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookDTO.getEventId(),
               webhookDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        gitXWebhookEventService.processEvent(webhookDTO);
        return HsqsProcessMessageResponse.builder().success(true).accountId(webhookDTO.getAccountId()).build();
      }
    } catch (Exception e) {
      log.error(String.format("Error while processing event item id %s", message.getItemId()), e);
      throw new InternalServerErrorException("Exception while processing webhook gitx push event", e);
    }
  }

  @Override
  public String getTopicName() {
    return NG_GITX_WEBHOOK_PUSH_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return webhookGitXPushEventQueueConfig;
  }
}
