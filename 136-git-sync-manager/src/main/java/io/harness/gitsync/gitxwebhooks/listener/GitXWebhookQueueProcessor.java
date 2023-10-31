/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.listener;

import static io.harness.eventsframework.EventsFrameworkConstants.GITX_WEBHOOK_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookEventLogContext;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventProcessService;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.queuePoller.AbstractHsqsQueueProcessor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject GitXWebhookEventProcessService gitXWebhookEventProcessService;
  @Inject @Named("gitXWebhookEventQueueConfig") HsqsDequeueConfig gitXWebhookEventQueueConfig;
  private static final String QUEUE_TOPIC_PREFIX = "ng";
  private static final String PROCESSING_ERROR_MESSAGE = "Error while processing event item id %s";

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    try {
      log.info("Started processing webhook event for item id {}", message.getItemId());
      WebhookDTO webhookDTO = RecastOrchestrationUtils.fromJson(message.getPayload(), WebhookDTO.class);
      try (GitXWebhookEventLogContext ignore0 = new GitXWebhookEventLogContext(webhookDTO)) {
        gitXWebhookEventProcessService.processEvent(webhookDTO);
        return HsqsProcessMessageResponse.builder().success(true).accountId(webhookDTO.getAccountId()).build();
      }
    } catch (Exception e) {
      log.error(String.format(PROCESSING_ERROR_MESSAGE, message.getItemId()), e);
      throw new InvalidRequestException(String.format(PROCESSING_ERROR_MESSAGE, message.getItemId()), e);
    }
  }

  @Override
  public String getTopicName() {
    return QUEUE_TOPIC_PREFIX + GITX_WEBHOOK_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return gitXWebhookEventQueueConfig;
  }
}
