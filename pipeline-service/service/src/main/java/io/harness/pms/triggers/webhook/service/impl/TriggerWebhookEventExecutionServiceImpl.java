/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerWebhookEventExecutionServiceImpl implements TriggerWebhookEventExecutionService {
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject NGTriggerRepository ngTriggerRepository;
  @Inject private TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;

  @Override
  public void processEvent(TriggerExecutionDTO triggerExecutionDTO) {
    try {
      TriggerWebhookEvent triggerWebhookEvent =
          ngTriggerElementMapper
              .toNGTriggerWebhookEvent(triggerExecutionDTO.getWebhookDto().getAccountId(), null, null,
                  triggerExecutionDTO.getWebhookDto().getJsonPayload(),
                  prepareHeaders(triggerExecutionDTO.getWebhookDto()))
              .uuid(triggerExecutionDTO.getWebhookDto().getEventId())
              .createdAt(triggerExecutionDTO.getWebhookDto().getTime())
              .build();
      Optional<NGTriggerEntity> optionalNGTriggerEntity =
          ngTriggerRepository
              .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                  triggerExecutionDTO.getAccountId(), triggerExecutionDTO.getOrgIdentifier(),
                  triggerExecutionDTO.getProjectIdentifier(), triggerExecutionDTO.getTargetIdentifier(),
                  triggerExecutionDTO.getTriggerIdentifier(), true);
      List<TriggerEventResponse> eventResponses = new ArrayList<>();
      if (optionalNGTriggerEntity.isPresent()) {
        NGTriggerEntity ngTriggerEntity = optionalNGTriggerEntity.get();
        NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity);
        TriggerDetails triggerDetails = TriggerDetails.builder()
                                            .ngTriggerEntity(ngTriggerEntity)
                                            .ngTriggerConfigV2(ngTriggerConfigV2)
                                            .authenticated(triggerExecutionDTO.getAuthenticated())
                                            .build();
        ngTriggerWebhookExecutionHelper.updateWebhookRegistrationStatusAndTriggerPipelineExecution(
            triggerExecutionDTO.getWebhookDto().getParsedResponse(), triggerWebhookEvent, eventResponses,
            triggerDetails);
      }

      if (isNotEmpty(eventResponses)) {
        eventResponses = eventResponses.stream().filter(Objects::nonNull).collect(toList());
      }

      saveTriggerExecutionHistoryRecords(eventResponses);
    } catch (Exception e) {
      log.error(
          "Exception while processing Trigger for webhook event with trigger identifier {} and pipeline identifier {}",
          triggerExecutionDTO.getTriggerIdentifier(), triggerExecutionDTO.getTargetIdentifier(), e);
    }
  }

  private void saveTriggerExecutionHistoryRecords(List<TriggerEventResponse> responseList) {
    responseList.forEach(response -> {
      try {
        triggerEventHistoryRepository.save(TriggerEventResponseHelper.toEntity(response));
      } catch (Exception e) {
        log.error("Failed to generate and save TriggerExecutionHistoryRecord: " + response);
      }
    });
  }

  public List<HeaderConfig> prepareHeaders(WebhookDTO webhookDTO) {
    List<EventHeader> headersList = webhookDTO.getHeadersList();

    return headersList.stream()
        .map(eventHeader
            -> HeaderConfig.builder()
                   .key(eventHeader.getKey())
                   .values(eventHeader.getValuesList().stream().collect(toList()))
                   .build())
        .collect(toList());
  }
}
