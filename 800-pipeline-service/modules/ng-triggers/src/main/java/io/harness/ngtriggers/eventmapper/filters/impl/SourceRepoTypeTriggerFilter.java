/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;

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
public class SourceRepoTypeTriggerFilter implements TriggerFilter {
  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder builder = initWebhookEventMappingResponse(filterRequestData);
    TriggerWebhookEvent triggerWebhookEvent = filterRequestData.getWebhookPayloadData().getOriginalEvent();
    List<TriggerDetails> filteredList = null;

    if (isNotEmpty(filterRequestData.getDetails())) {
      filteredList = filterRequestData.getDetails()
                         .stream()
                         .filter(trigger -> isValidSourceRepoType(triggerWebhookEvent, trigger.getNgTriggerEntity()))
                         .collect(toList());
    }

    if (isEmpty(filteredList)) {
      String msg = String.format("No enabled trigger found for sourceRepoType {} for project: {}",
          triggerWebhookEvent.getSourceRepoType(), filterRequestData.getAccountId());
      log.info(msg);
      builder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(
              NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE, triggerWebhookEvent, null, null, msg, null))
          .build();
    } else {
      addDetails(builder, filterRequestData, filteredList);
    }

    return builder.build();
  }

  @VisibleForTesting
  boolean isValidSourceRepoType(TriggerWebhookEvent triggerWebhookEvent, NGTriggerEntity triggerEntity) {
    if (triggerEntity.getMetadata().getWebhook() == null) {
      return false;
    }

    WebhookMetadata webhook = triggerEntity.getMetadata().getWebhook();
    return webhook.getType().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType());
  }
}
