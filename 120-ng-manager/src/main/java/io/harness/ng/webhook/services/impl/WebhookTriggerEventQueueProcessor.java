/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_BRANCH_HOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PUSH_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_TRIGGER_EVENT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.CREATE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.DELETE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PUSH;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.queuePoller.AbstractHsqsQueueProcessor;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookTriggerEventQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject @Named("webhookTriggerEventHsqsDequeueConfig") HsqsDequeueConfig webhookTriggerEventHsqsDequeueConfig;
  @Inject private WebhookHelper webhookHelper;
  private HsqsClientService hsqsClientService;

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    try {
      log.info("Started processing webhook trigger event for item id {}", message.getItemId());
      WebhookEvent webhookEvent = RecastOrchestrationUtils.fromJson(message.getPayload(), WebhookEvent.class);
      try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookEvent.getUuid(),
               webhookEvent.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        generateWebhookDTOAndEnqueue(webhookEvent);
        return HsqsProcessMessageResponse.builder().success(true).accountId(webhookEvent.getAccountId()).build();
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Exception while processing webhook event", e);
    }
  }

  @VisibleForTesting
  void generateWebhookDTOAndEnqueue(WebhookEvent webhookEvent) {
    try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookEvent.getUuid(),
             webhookEvent.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String topic = "ng";
      String moduleName = "ng";
      ParseWebhookResponse parseWebhookResponse = null;
      SourceRepoType sourceRepoType = webhookHelper.getSourceRepoType(webhookEvent);
      if (sourceRepoType != SourceRepoType.UNRECOGNIZED) {
        parseWebhookResponse = webhookHelper.invokeScmService(webhookEvent);
      }
      WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, sourceRepoType);
      enqueueWebhookDTOs(webhookDTO, topic, moduleName, webhookEvent.getUuid());
    }
  }

  private void enqueueWebhookDTOs(WebhookDTO webhookDTO, String topic, String moduleName, String uuid) {
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
  public String getTopicName() {
    return "ng" + WEBHOOK_TRIGGER_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return webhookTriggerEventHsqsDequeueConfig;
  }
}
