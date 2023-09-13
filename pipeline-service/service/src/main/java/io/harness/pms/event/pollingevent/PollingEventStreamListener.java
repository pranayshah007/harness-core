/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorEventHandler;
import io.harness.pms.triggers.build.eventmapper.BuildTriggerEventMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PollingEventStreamListener extends PmsAbstractMessageListener<FacilitatorEvent, FacilitatorEventHandler> {
  @Inject private BuildTriggerEventMapper mapper;
  @Inject private TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;

  @Inject
  public PollingEventStreamListener(
      @Named(SDK_SERVICE_NAME) String serviceName, FacilitatorEventHandler facilitatorEventHandler) {
    super(serviceName, FacilitatorEvent.class, facilitatorEventHandler);
  }

  @Override
  public boolean handleMessage(Message message, Long readTs) {
    if (message != null && message.hasMessage()) {
      try {
        PollingResponse response = PollingResponse.parseFrom(message.getMessage().getData());
        try (AccountLogContext ignore1 = new AccountLogContext(response.getAccountId(), OVERRIDE_ERROR);
             AutoLogContext ignore2 = new NgPollingAutoLogContext(response.getPollingDocId(), OVERRIDE_ERROR);
             AutoLogContext ignore3 = new NgEventLogContext(message.getId(), OVERRIDE_ERROR);) {
          WebhookEventMappingResponse webhookEventMappingResponse = mapper.consumeBuildTriggerEvent(response);
          log.info("Started processing polling event for message id {}", message.getId());
          if (!webhookEventMappingResponse.isFailedToFindTrigger()) {
            List<TriggerEventResponse> responses = triggerEventExecutionHelper.processTriggersForActivation(
                webhookEventMappingResponse.getTriggers(), response);
            if (isNotEmpty(responses)) {
              responses.forEach(resp -> triggerEventHistoryRepository.save(TriggerEventResponseHelper.toEntity(resp)));
            }
          }
        }
      } catch (InvalidProtocolBufferException e) {
        throw new InvalidRequestException("Exception in unpacking/processing of WebhookDTO event", e);
      }
    }
    return true;
  }
  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}
