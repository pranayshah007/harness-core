/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_BRANCH_HOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PUSH_EVENT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.CREATE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.DELETE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PUSH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.common.annotations.VisibleForTesting;
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
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private HsqsClientService hsqsClientService;
  private WebhookHelper webhookHelper;

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
        generateWebhookDTOAndEnqueue(webhookEvent);
      }
      return webhookEvent;
    } catch (Exception e) {
      throw new InvalidRequestException("Webhook event could not be saved for processing");
    }
  }

  @VisibleForTesting
  void generateWebhookDTOAndEnqueue(WebhookEvent webhookEvent) {
    if (isEmpty(webhookEvent.getUuid())) {
      webhookEvent.setUuid(generateUuid());
    }
    if (webhookEvent.getCreatedAt() == null) {
      webhookEvent.setCreatedAt(System.currentTimeMillis());
    }
    try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookEvent.getUuid(),
             webhookEvent.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String topic = nextGenConfiguration.getQueueServiceClientConfig().getTopic();
      String moduleName = topic;
      ParseWebhookResponse parseWebhookResponse = null;
      SourceRepoType sourceRepoType = webhookHelper.getSourceRepoType(webhookEvent);
      if (sourceRepoType != SourceRepoType.UNRECOGNIZED) {
        parseWebhookResponse = webhookHelper.invokeScmService(webhookEvent);
      }
      WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, sourceRepoType);
      enqueueWebhookEvents(webhookDTO, topic, moduleName, webhookEvent.getUuid());
    }
  }

  private void enqueueWebhookEvents(WebhookDTO webhookDTO, String topic, String moduleName, String uuid) {
    // Consumer for webhook events stream: WebhookEventQueueProcessor (in Pipeline service)
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic(topic + WEBHOOK_EVENT)
                                        .subTopic(webhookDTO.getAccountId())
                                        .producerName(moduleName + WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueResponse execute = hsqsClientService.enqueue(enqueueRequest);
    log.info("Webhook event queued. message id: {}, uuid: {}", execute.getItemId(), uuid);
    if (webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails()) {
      enqueueRequest = getEnqueueRequestBasedOnGitEvent(moduleName, topic, webhookDTO);
      if (enqueueRequest != null) {
        execute = hsqsClientService.enqueue(enqueueRequest);
        log.info("Webhook {} event queued. message id: {}", webhookDTO.getGitDetails().getEvent(), execute.getItemId());
      }
    }
  }

  private EnqueueRequest getEnqueueRequestBasedOnGitEvent(String moduleName, String topic, WebhookDTO webhookDTO) {
    // Consumer for push events stream: WebhookPushEventQueueProcessor (in NG manager)
    if (PUSH == webhookDTO.getGitDetails().getEvent()) {
      return EnqueueRequest.builder()
          .topic(topic + WEBHOOK_PUSH_EVENT)
          .subTopic(webhookDTO.getAccountId())
          .producerName(moduleName + WEBHOOK_PUSH_EVENT)
          .payload(RecastOrchestrationUtils.toJson(webhookDTO))
          .build();
    }
    // Consumer for branch hook events stream: WebhookBranchHookEventQueueProcessor (in NG manager)
    else if (CREATE_BRANCH == webhookDTO.getGitDetails().getEvent()
        || DELETE_BRANCH == webhookDTO.getGitDetails().getEvent()) {
      return EnqueueRequest.builder()
          .topic(topic + WEBHOOK_BRANCH_HOOK_EVENT)
          .subTopic(webhookDTO.getAccountId())
          .producerName(moduleName + WEBHOOK_BRANCH_HOOK_EVENT)
          .payload(RecastOrchestrationUtils.toJson(webhookDTO))
          .build();
    }
    // Here we can add more logic if needed to add more event topics.
    return null;
  }

  @Override
  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    return Boolean.TRUE.equals(upsertWebhookRequestDTO.getIsHarnessScm())
        ? harnessSCMWebhookService.upsertWebhook(upsertWebhookRequestDTO)
        : defaultWebhookService.upsertWebhook(upsertWebhookRequestDTO);
  }
}
