/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.service.NGTriggerService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class AccountTriggerFilter implements TriggerFilter {
  private final NGTriggerService ngTriggerService;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder builder = initWebhookEventMappingResponse(filterRequestData);
    TriggerWebhookEvent triggerWebhookEvent = filterRequestData.getWebhookPayloadData().getOriginalEvent();
    List<NGTriggerEntity> triggersForAccount =
        ngTriggerService.findTriggersForWehbookBySourceRepoType(triggerWebhookEvent, false, true);

    if (isEmpty(triggersForAccount)) {
      String errorMsg = new StringBuilder(256)
                            .append("No enabled trigger found for Account:")
                            .append(triggerWebhookEvent.getAccountId())
                            .append(", SourceRepoType: ")
                            .append(triggerWebhookEvent.getSourceRepoType())
                            .toString();
      log.info(errorMsg);
      builder.failedToFindTrigger(true).webhookEventResponse(TriggerEventResponseHelper.toResponse(
          NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO, triggerWebhookEvent, null, null, errorMsg, null));
    } else {
      addDetails(builder, filterRequestData,
          triggersForAccount.stream()
              .map(entity -> TriggerDetails.builder().ngTriggerEntity(entity).build())
              .collect(toList()));
    }

    return builder.build();
  }
}
