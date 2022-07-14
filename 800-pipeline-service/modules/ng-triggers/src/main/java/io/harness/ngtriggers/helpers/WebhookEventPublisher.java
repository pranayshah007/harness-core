/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_REQUEST_PAYLOAD_DETAILS;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.GIT;

import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class WebhookEventPublisher {
  @Inject @Named(WEBHOOK_REQUEST_PAYLOAD_DETAILS) private Producer eventProducer;

  public void publishGitWebhookEvent(WebhookPayloadData webhookPayloadData, WebhookEventType webhookEventType) {
    WebhookDTO webhookDTO = WebhookDTO.newBuilder()
                                .setWebhookEventType(webhookEventType)
                                .setWebhookTriggerType(GIT)
                                .setJsonPayload(webhookPayloadData.getOriginalEvent().getPayload())
                                .setParsedResponse(webhookPayloadData.getParseWebhookResponse())
                                .build();

    eventProducer.send(
        Message.newBuilder()
            .putAllMetadata(ImmutableMap.of("accountId", webhookPayloadData.getOriginalEvent().getAccountId(),
                "correlationId", webhookPayloadData.getOriginalEvent().getUuid(), "sourceRepoType",
                webhookPayloadData.getOriginalEvent().getSourceRepoType()))
            .setData(webhookDTO.toByteString())
            .build());
  }
}
