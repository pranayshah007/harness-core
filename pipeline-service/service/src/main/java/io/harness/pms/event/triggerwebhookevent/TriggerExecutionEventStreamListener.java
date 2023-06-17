/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.triggerwebhookevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorEventHandler;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class TriggerExecutionEventStreamListener
    extends PmsAbstractMessageListener<FacilitatorEvent, FacilitatorEventHandler> {
  @Inject TriggerWebhookEventExecutionService triggerWebhookEventExecutionService;

  @Inject
  public TriggerExecutionEventStreamListener(@Named(SDK_SERVICE_NAME) String serviceName,
      FacilitatorEventHandler handler, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(serviceName, FacilitatorEvent.class, handler, executorService);
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      try {
        log.info("Started processing trigger webhook event for message id {}", message.getId());
        TriggerExecutionDTO triggerExecutionDTO = TriggerExecutionDTO.parseFrom(message.getMessage().getData());
        try (NgTriggerAutoLogContext ignore0 =
                 new NgTriggerAutoLogContext("eventId", triggerExecutionDTO.getWebhookDto().getEventId(),
                     triggerExecutionDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          triggerWebhookEventExecutionService.processEvent(triggerExecutionDTO);
        }
      } catch (InvalidProtocolBufferException ex) {
        throw new InvalidRequestException(
            "Exception in unpacking/processing of TriggerExecutionDTO event with messageId : " + message.getId(), ex);
      } catch (Exception e) {
        throw new InvalidRequestException(
            "Exception while processing TriggerExecutionDto event with messageId : " + message.getId(), e);
      }
    }
    return true;
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}
