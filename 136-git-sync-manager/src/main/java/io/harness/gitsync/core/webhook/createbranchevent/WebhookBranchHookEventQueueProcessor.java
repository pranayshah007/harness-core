/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.webhook.createbranchevent;

import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_BRANCH_HOOK_EVENT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.core.service.webhookevent.GitBranchHookEventExecutionService;
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

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookBranchHookEventQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject GitBranchHookEventExecutionService gitBranchHookEventExecutionService;
  @Inject @Named("webhookBranchHookEventHsqsDequeueConfig") HsqsDequeueConfig webhookBranchHookEventHsqsDequeueConfig;

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    try {
      log.info("Started processing webhook event for item id {}", message.getItemId());
      WebhookDTO webhookDTO = RecastOrchestrationUtils.fromJson(message.getPayload(), WebhookDTO.class);
      try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookDTO.getEventId(),
               webhookDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        gitBranchHookEventExecutionService.processEvent(webhookDTO);
        return HsqsProcessMessageResponse.builder().success(true).accountId(webhookDTO.getAccountId()).build();
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Exception while processing webhook branch hook event", e);
    }
  }

  @Override
  public String getTopicName() {
    return "ng" + WEBHOOK_BRANCH_HOOK_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return webhookBranchHookEventHsqsDequeueConfig;
  }
}
