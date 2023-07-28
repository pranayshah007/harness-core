/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.service.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerWebhookConfirmationHelper;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerWebhookExecutionServiceImplV2 implements TriggerWebhookExecutionServiceV2 {
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Inject private TriggerWebhookConfirmationHelper ngTriggerWebhookConfirmationHelper;
  @Inject TriggerEventHistoryRepository triggerEventHistoryRepository;

  @Override
  public void processEvent(WebhookDTO webhookDTO) {
    try {
      WebhookEventProcessingResult result;

      TriggerWebhookEvent event =
          ngTriggerElementMapper
              .toNGTriggerWebhookEvent(webhookDTO.getAccountId(), null, null, webhookDTO.getJsonPayload(),
                  prepareHeaders(webhookDTO), webhookDTO.getPrincipal())
              .uuid(webhookDTO.getEventId())
              .createdAt(webhookDTO.getTime())
              .build();
      log.info("Processing webhook event with id {}", webhookDTO.getEventId());
      if (event.isSubscriptionConfirmation()) {
        result = ngTriggerWebhookConfirmationHelper.handleTriggerWebhookConfirmationEvent(event);
      } else {
        result = ngTriggerWebhookExecutionHelper.handleTriggerWebhookEvent(
            TriggerMappingRequestData.builder().triggerWebhookEvent(event).webhookDTO(webhookDTO).build());
      }

      List<TriggerEventResponse> responseList = result.getResponses();

      // Remove any null values if present in list
      if (isNotEmpty(responseList)) {
        responseList = responseList.stream().filter(Objects::nonNull).collect(toList());
      }

      saveTriggerExecutionHistoryRecords(responseList);
    } catch (Exception e) {
      log.error("Exception while processing webhook event. Please check", e);
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
