/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_TRIGGER_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookServiceImpl implements WebhookService, WebhookEventService {
  private final HarnessSCMWebhookServiceImpl harnessSCMWebhookService;
  private final DefaultWebhookServiceImpl defaultWebhookService;
  private final WebhookEventRepository webhookEventRepository;
  private HsqsClientService hsqsClientService;

  NextGenConfiguration nextGenConfiguration;

  @Override
  public WebhookEvent addEventToQueue(WebhookEvent webhookEvent) {
    try {
      log.info(
          "received webhook event with id {} in the accountId {}", webhookEvent.getUuid(), webhookEvent.getAccountId());
      // TODO: add a check based on env to use iterators in community edition and on prem
      if (!nextGenConfiguration.isUseQueueServiceForWebhookTriggers()) {
        return webhookEventRepository.save(webhookEvent);
      } else {
        if (isEmpty(webhookEvent.getUuid())) {
          webhookEvent.setUuid(generateUuid());
        }
        if (webhookEvent.getCreatedAt() == null) {
          webhookEvent.setCreatedAt(System.currentTimeMillis());
        }
        String topic = nextGenConfiguration.getQueueServiceClientConfig().getTopic();
        String moduleName = topic;
        enqueueWebhookEvent(webhookEvent, topic, moduleName, webhookEvent.getUuid());
      }
      return webhookEvent;
    } catch (Exception e) {
      throw new InvalidRequestException("Webhook event could not be saved for processing");
    }
  }

  @VisibleForTesting
  void enqueueWebhookEvent(WebhookEvent webhookEvent, String topic, String moduleName, String uuid) {
    // Consumer for webhook trigger event stream: WebhookTriggerEventQueueProcessor (in NG Manager)
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic(topic + WEBHOOK_TRIGGER_EVENT)
                                        .subTopic(webhookEvent.getAccountId())
                                        .producerName(moduleName + WEBHOOK_TRIGGER_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookEvent))
                                        .build();
    EnqueueResponse execute = hsqsClientService.enqueue(enqueueRequest);
    log.info("Webhook event queued. message id: {}, uuid: {}", execute.getItemId(), uuid);
  }

  @Override
  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    return Boolean.TRUE.equals(upsertWebhookRequestDTO.getIsHarnessScm())
        ? harnessSCMWebhookService.upsertWebhook(upsertWebhookRequestDTO)
        : defaultWebhookService.upsertWebhook(upsertWebhookRequestDTO);
  }
}
