/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.exception.InvalidRequestException;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.queuePoller.AbstractHsqsQueueProcessor;
import io.harness.serializer.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.LinkedHashMap;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookEventQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject TriggerWebhookExecutionServiceV2 triggerWebhookExecutionServiceV2;
  @Inject @Named("webhookEventHsqsDequeueConfig") HsqsDequeueConfig webhookEventHsqsDequeueConfig;

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    try {
      log.info("Started processing webhook event for item id {}", message.getItemId());
      WebhookDTO webhookDTO = RecastOrchestrationUtils.fromJson(message.getPayload(), WebhookDTO.class);
      try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookDTO.getEventId(),
               webhookDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        if (webhookDTO.getGitDetails().getEvent().equals(WebhookEventType.PR)
            && webhookDTO.getGitDetails().getSourceRepoType().equals(SourceRepoType.GITLAB)) {
          webhookDTO = updateWebhookForGitlabPr(webhookDTO);
        }
        triggerWebhookExecutionServiceV2.processEvent(webhookDTO);
        return HsqsProcessMessageResponse.builder().success(true).accountId(webhookDTO.getAccountId()).build();
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Exception while processing webhook event", e);
    }
  }

  // scm-service is setting the wrong action, so we need to set it manually for gitlab pull request edit action
  private WebhookDTO updateWebhookForGitlabPr(WebhookDTO webhookDTO) {
    if (JsonUtils.asMap(webhookDTO.getJsonPayload()) instanceof LinkedHashMap) {
      if (((LinkedHashMap<?, ?>) JsonUtils.asMap(webhookDTO.getJsonPayload())).get("object_attributes")
              instanceof LinkedHashMap) {
        String action =
            (String) ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) JsonUtils.asMap(webhookDTO.getJsonPayload()))
                          .get("object_attributes"))
                .get("action");
        if (Objects.equals(action, "update")) {
          ParseWebhookResponse parseWebhookResponse = webhookDTO.getParsedResponse();
          PullRequestHook pullRequestHook = parseWebhookResponse.getPr();
          PullRequestHook newPullRequestHook = pullRequestHook.toBuilder().setAction(Action.UPDATE).build();
          ParseWebhookResponse newParseWebhookResponse =
              parseWebhookResponse.toBuilder().setPr(newPullRequestHook).build();
          return webhookDTO.toBuilder().setParsedResponse(newParseWebhookResponse).build();
        }
      }
    }
    return webhookDTO;
  }

  @Override
  public String getTopicName() {
    return "ng" + WEBHOOK_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return webhookEventHsqsDequeueConfig;
  }
}
