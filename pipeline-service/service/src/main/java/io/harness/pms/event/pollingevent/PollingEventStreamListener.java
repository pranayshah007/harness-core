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

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.pms.triggers.build.eventmapper.BuildTriggerEventMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PollingEventStreamListener implements MessageListener {
  private BuildTriggerEventMapper mapper;
  private TriggerEventExecutionHelper triggerEventExecutionHelper;
  private TriggerEventHistoryRepository triggerEventHistoryRepository;

  @Inject
  public PollingEventStreamListener(BuildTriggerEventMapper mapper,
      TriggerEventExecutionHelper triggerEventExecutionHelper,
      TriggerEventHistoryRepository triggerEventHistoryRepository) {
    this.mapper = mapper;
    this.triggerEventExecutionHelper = triggerEventExecutionHelper;
    this.triggerEventHistoryRepository = triggerEventHistoryRepository;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      try {
        PollingResponse response = PollingResponse.parseFrom(message.getMessage().getData());
        try (AccountLogContext ignore1 = new AccountLogContext(response.getAccountId(), OVERRIDE_ERROR);
             AutoLogContext ignore2 = new NgPollingAutoLogContext(response.getPollingDocId(), OVERRIDE_ERROR);) {
          WebhookEventMappingResponse webhookEventMappingResponse = mapper.consumeBuildTriggerEvent(response);
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
}
