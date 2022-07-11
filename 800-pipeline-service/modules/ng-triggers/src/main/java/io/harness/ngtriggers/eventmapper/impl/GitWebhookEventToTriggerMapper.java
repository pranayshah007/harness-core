/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PR;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PUSH;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse.ParsePayloadResponseBuilder;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.WebhookEventToTriggerMapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerFilterStore;
import io.harness.ngtriggers.helpers.WebhookEventPublisher;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class GitWebhookEventToTriggerMapper implements WebhookEventToTriggerMapper {
  private final WebhookEventPayloadParser webhookEventPayloadParser;
  private final TriggerFilterStore triggerFilterHelper;
  private final WebhookEventPublisher webhookEventPublisher;

  public WebhookEventMappingResponse mapWebhookEventToTriggers(TriggerMappingRequestData mappingRequestData) {
    TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();

    // 1. Parse Payload
    WebhookPayloadData webhookPayloadData = null;
    if (mappingRequestData.getWebhookDTO() == null) {
      ParsePayloadResponse parsePayloadResponse = convertWebhookResponse(triggerWebhookEvent);
      if (parsePayloadResponse.isExceptionOccured()) {
        return WebhookEventMappingResponse.builder()
            .webhookEventResponse(TriggerEventResponseHelper.prepareResponseForScmException(parsePayloadResponse))
            .build();
      }

      webhookPayloadData = parsePayloadResponse.getWebhookPayloadData();
    } else {
      webhookPayloadData = webhookEventPayloadParser.convertWebhookResponse(
          mappingRequestData.getWebhookDTO().getParsedResponse(), triggerWebhookEvent);
    }

    // this is how TI(Test Intelligence) receives its push and pr events today.
    // this is pending to be changed, TI should start consuming events same way as Trigger or Gitsync does.
    // So this can go away
    publishPushAndPrEvent(webhookPayloadData);

    // Generate list of all filters to be applied
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(webhookPayloadData.getOriginalEvent().getAccountId())
                                              .webhookPayloadData(webhookPayloadData)
                                              .build();
    List<TriggerFilter> triggerFilters =
        triggerFilterHelper.getWebhookTriggerFilters(filterRequestData.getWebhookPayloadData());

    // Apply filters
    WebhookEventMappingResponse webhookEventMappingResponse = null;
    TriggerFilter triggerFilterInAction = null;

    try {
      for (TriggerFilter triggerFilter : triggerFilters) {
        triggerFilterInAction = triggerFilter;
        webhookEventMappingResponse = triggerFilter.applyFilter(filterRequestData);
        if (webhookEventMappingResponse.isFailedToFindTrigger()) {
          return webhookEventMappingResponse;
        } else {
          // update with updated filter list for next filter
          filterRequestData.setDetails(webhookEventMappingResponse.getTriggers());
        }
      }
    } catch (Exception e) {
      log.error("Exception while evaluating Triggers: ", e);
      return triggerFilterInAction.getWebhookResponseForException(filterRequestData, e);
    }

    return webhookEventMappingResponse;
  }

  /**
   * This is temporary, added specifically to support TI use-case.
   * We only publish "PUSH" and "PR" git event.
   * This will become part of common service, where different subscribers can subscribe for
   * eventType, triggerType to receive events.
   * <p>
   * Then this can be removed.
   *
   * @param webhookPayloadData
   */
  @VisibleForTesting
  void publishPushAndPrEvent(WebhookPayloadData webhookPayloadData) {
    try {
      if (webhookPayloadData.getParseWebhookResponse().hasPush()) {
        webhookEventPublisher.publishGitWebhookEvent(webhookPayloadData, PUSH);
      } else if (webhookPayloadData.getParseWebhookResponse().hasPr()) {
        webhookEventPublisher.publishGitWebhookEvent(webhookPayloadData, PR);
      }
    } catch (Exception e) {
      log.error("Failed to send webhook event {} to events framework: {}",
          webhookPayloadData.getOriginalEvent().getUuid(), e);
    }
  }

  private String getProjectFqn(TriggerWebhookEvent triggerWebhookEvent) {
    return new StringBuilder(256)
        .append(triggerWebhookEvent.getAccountId())
        .append('/')
        .append(triggerWebhookEvent.getOrgIdentifier())
        .append('/')
        .append(triggerWebhookEvent.getProjectIdentifier())
        .toString();
  }

  // Add error handling
  @VisibleForTesting
  ParsePayloadResponse convertWebhookResponse(TriggerWebhookEvent triggerWebhookEvent) {
    ParsePayloadResponseBuilder builder = ParsePayloadResponse.builder();
    try {
      WebhookPayloadData webhookPayloadData = webhookEventPayloadParser.parseEvent(triggerWebhookEvent);
      builder.webhookPayloadData(webhookPayloadData).build();
    } catch (Exception e) {
      builder.exceptionOccured(true)
          .exception(e)
          .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
          .build();
    }

    return builder.build();
  }
}
