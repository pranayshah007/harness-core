/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.gitsync.gitxwebhooks.helper;

import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookTriggerHelper {
  @Inject @Named(WEBHOOK_EVENTS_STREAM) private Producer eventProducer;

  public void startTriggerExecution(WebhookDTO webhookDTO) {
    try {
      log.info(String.format(
          "Starting the trigger execution after gitx Webhook processing for the event %s", webhookDTO.getEventId()));
      eventProducer.send(Message.newBuilder().setData(webhookDTO.toByteString()).build());
    } catch (Exception exception) {
      log.error("Faced exception while sequentially executing the trigger for the event: {} ", webhookDTO.getEventId(),
          exception);
    }
  }
}
