/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.Constants.TRIGGER_ERROR_LOG;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;

import java.util.List;

@OwnedBy(PIPELINE)
public interface TriggerFilter {
  WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData);

  default WebhookEventMappingResponseBuilder initWebhookEventMappingResponse(FilterRequestData filterRequestData) {
    return WebhookEventMappingResponse.builder().isCustomTrigger(filterRequestData.isCustomTrigger());
  }

  default String getTriggerSkipMessage(NGTriggerEntity ngTriggerEntity) {
    String triggerRef = new StringBuilder(128)
                            .append(ngTriggerEntity.getAccountId())
                            .append(':')
                            .append(ngTriggerEntity.getOrgIdentifier())
                            .append(':')
                            .append(ngTriggerEntity.getProjectIdentifier())
                            .append(':')
                            .append(ngTriggerEntity.getTargetIdentifier())
                            .append(':')
                            .append(ngTriggerEntity.getIdentifier())
                            .toString();

    return new StringBuilder(128)
        .append(TRIGGER_ERROR_LOG)
        .append("Exception while evaluating Trigger: ")
        .append(triggerRef)
        .append(", Filter: ")
        .append(getClass().getSimpleName())
        .append(", Skipping this one.")
        .toString();
  }

  default WebhookEventMappingResponse getWebhookResponseForException(FilterRequestData filterRequestData, Exception e) {
    return WebhookEventMappingResponse.builder()
        .failedToFindTrigger(true)
        .isCustomTrigger(filterRequestData.isCustomTrigger())
        .webhookEventResponse(TriggerEventResponseHelper.toResponse(EXCEPTION_WHILE_PROCESSING,
            filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
            new StringBuilder(256)
                .append("Exception occurred while Processing Trigger Filter: ")
                .append(getClass().getSimpleName())
                .append(", for Account: ")
                .append(filterRequestData.getWebhookPayloadData().getOriginalEvent().getAccountId())
                .append(". Exception: ")
                .append(e)
                .toString(),
            null))
        .build();
  }

  default void addDetails(WebhookEventMappingResponseBuilder webhookEventMappingResponseBuilder,
      FilterRequestData filterRequestData, List<TriggerDetails> detailsList) {
    if (filterRequestData.getWebhookPayloadData() != null
        && filterRequestData.getWebhookPayloadData().getParseWebhookResponse() != null) {
      webhookEventMappingResponseBuilder.parseWebhookResponse(
          filterRequestData.getWebhookPayloadData().getParseWebhookResponse());
    }

    if (isEmpty(detailsList)) {
      webhookEventMappingResponseBuilder.failedToFindTrigger(true);
      webhookEventMappingResponseBuilder.triggers(emptyList());
    } else {
      webhookEventMappingResponseBuilder.failedToFindTrigger(false);
      webhookEventMappingResponseBuilder.triggers(detailsList);
    }
  }
}
