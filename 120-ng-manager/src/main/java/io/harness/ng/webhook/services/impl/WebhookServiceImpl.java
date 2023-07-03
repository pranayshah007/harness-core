/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookServiceImpl implements WebhookService, WebhookEventService {
  private final HarnessSCMWebhookServiceImpl harnessSCMWebhookService;
  private final DefaultWebhookServiceImpl defaultWebhookService;
  private final WebhookEventRepository webhookEventRepository;

  @Override
  public WebhookEvent addEventToQueue(WebhookEvent webhookEvent) {
    try {
      log.info(
          "received webhook event with id {} in the accountId {}", webhookEvent.getUuid(), webhookEvent.getAccountId());
      return webhookEventRepository.save(webhookEvent);
    } catch (Exception e) {
      throw new InvalidRequestException("Webhook event could not be saved for processing");
    }
  }

  @Override
  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    return Boolean.TRUE.equals(upsertWebhookRequestDTO.getIsHarnessScm())
        ? harnessSCMWebhookService.upsertWebhook(upsertWebhookRequestDTO)
        : defaultWebhookService.upsertWebhook(upsertWebhookRequestDTO);
  }
}
