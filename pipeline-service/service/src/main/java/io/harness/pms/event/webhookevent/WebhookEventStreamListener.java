/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorEventHandler;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.serializer.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class WebhookEventStreamListener extends PmsAbstractMessageListener<FacilitatorEvent, FacilitatorEventHandler> {
  @Inject TriggerWebhookExecutionServiceV2 triggerWebhookExecutionServiceV2;

  @Inject
  public WebhookEventStreamListener(@Named(SDK_SERVICE_NAME) String serviceName,
      FacilitatorEventHandler facilitatorEventHandler, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(serviceName, FacilitatorEvent.class, facilitatorEventHandler, executorService);
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      try {
        log.info("Started processing webhook event for message id {}", message.getId());
        WebhookDTO webhookDTO = WebhookDTO.parseFrom(message.getMessage().getData());
        try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookDTO.getEventId(),
                 webhookDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          if (webhookDTO.getGitDetails().getEvent().equals(WebhookEventType.PR)
              && webhookDTO.getGitDetails().getSourceRepoType().equals(SourceRepoType.GITLAB)) {
            webhookDTO = updateWebhookForGitlabPr(webhookDTO);
          }
          triggerWebhookExecutionServiceV2.processEvent(webhookDTO);
        }
      } catch (InvalidProtocolBufferException e) {
        throw new InvalidRequestException("Exception in unpacking/processing of WebhookDTO event", e);
      } catch (Exception e) {
        throw new InvalidRequestException("Exception while processing webhook event", e);
      }
    }
    return true;
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
}