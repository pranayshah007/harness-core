/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.listener;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventServiceImpl;
import io.harness.logging.AutoLogContext;
import io.harness.logging.WebhookEventAutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class GitXWebhookPushEventListener implements MessageListener {
  @Inject GitXWebhookEventServiceImpl gitXWebhookEventService;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      try (AutoLogContext ignore1 = new NgEventLogContext(message.getId(), OVERRIDE_ERROR)) {
        try {
          WebhookDTO webhookDTO = WebhookDTO.parseFrom(message.getMessage().getData());
          processWebhookEvent(webhookDTO);
        } catch (InvalidProtocolBufferException e) {
          log.error("Encountered error while deserialzing the webhook for the message {} in the GitXWebhookListener",
              message.getId(), e);
          throw new InvalidRequestException(
              String.format(
                  "Exception in unpacking/processing of WebhookDTO event for the push message %s in the GitXWebhookListener",
                  message.getId()),
              e);
        }
      }
    } else {
      log.error("Failed to parse the message {}.", message);
    }
    return true;
  }

  private void processWebhookEvent(WebhookDTO webhookDTO) {
    try (AutoLogContext ignore2 = new WebhookEventAutoLogContext(webhookDTO.getEventId(), OVERRIDE_ERROR)) {
      log.info("Starting processing of gitx webhook event {} in the GitXWebhookListener", webhookDTO.getEventId());
      gitXWebhookEventService.processEvent(webhookDTO);
    }
  }
}
