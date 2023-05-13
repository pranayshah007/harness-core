/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.eventsframework.EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerWebhookEventPublisher {
  @Inject @Named(TRIGGER_EXECUTION_EVENTS_STREAM) private Producer eventProducer;

  public void publishTriggerWebhookEvent(TriggerExecutionDTO triggerExecutionDTO) {
    String messageId =
        eventProducer.send(Message.newBuilder()
                               .putAllMetadata(ImmutableMap.of("accountId", triggerExecutionDTO.getAccountId()))
                               .setData(triggerExecutionDTO.toByteString())
                               .build());
    log.info("Published the Trigger webhook event item with message id {} ", messageId);
  }
}
