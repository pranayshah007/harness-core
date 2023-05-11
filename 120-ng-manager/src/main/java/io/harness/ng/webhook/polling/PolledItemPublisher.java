/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.polling;

import static io.harness.eventsframework.EventsFrameworkConstants.POLLING_EVENTS_STREAM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.ng.webhook.resources.NgWebhookResource;
import io.harness.polling.contracts.PollingResponse;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PolledItemPublisher {
  @Inject @Named(POLLING_EVENTS_STREAM) private Producer eventProducer;

  @Inject NgWebhookResource ngWebhookResource;

  public void publishPolledItems(PollingResponse pollingResponse) {
    String messageId =
        eventProducer.send(Message.newBuilder()
                               .putAllMetadata(ImmutableMap.of("accountId", pollingResponse.getAccountId()))
                               .setData(pollingResponse.toByteString())
                               .build());
    log.info("Published the webhook polled item with message id {} , pollingDocumentId {} ", messageId,
        pollingResponse.getPollingDocId());
  }

  public void sendWebhookRequest(String accountId, List<GitPollingWebhookData> redeliveries) {
    redeliveries.stream().forEach(webhookItem -> {
      String payload = webhookItem.getPayload();
      MultivaluedMap<String, String> headers = webhookItem.getHeaders();
      log.info("Processing the webhook redelivery for account {} and delivery id {} ", accountId,
          webhookItem.getDeliveryId());
      ngWebhookResource.processWebhook(accountId, payload, headers);
    });
  }
}
