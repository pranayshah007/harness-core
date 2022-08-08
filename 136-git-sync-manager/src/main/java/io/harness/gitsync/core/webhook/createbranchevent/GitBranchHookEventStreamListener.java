/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.webhook.createbranchevent;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.core.service.webhookevent.GitBranchHookEventExecutionService;
import io.harness.logging.AutoLogContext;
import io.harness.logging.WebhookEventAutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class GitBranchHookEventStreamListener implements MessageListener {
  @Inject GitBranchHookEventExecutionService gitBranchHookEventExecutionService;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      try (AutoLogContext ignore1 = new NgEventLogContext(message.getId(), OVERRIDE_ERROR)) {
        WebhookDTO webhookDTO = WebhookDTO.parseFrom(message.getMessage().getData());
        processWebhookEvent(webhookDTO);
      } catch (InvalidProtocolBufferException e) {
        log.error(
            "Encountered error while deserialzing the webhook for the branch hook message {}", message.getId(), e);
        throw new InvalidRequestException("Exception in unpacking/processing of WebhookDTO event", e);
      }
    }
    return true;
  }

  private void processWebhookEvent(WebhookDTO webhookDTO) {
    try (AutoLogContext ignore2 = new WebhookEventAutoLogContext(webhookDTO.getEventId(), OVERRIDE_ERROR)) {
      log.info("Starting processing the webhook event {}", webhookDTO.getEventId());
      gitBranchHookEventExecutionService.processEvent(webhookDTO);
    }
  }
}
