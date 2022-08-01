/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.WebhookEventToTriggerMapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerFilterStore;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class CustomWebhookEventToTriggerMapper implements WebhookEventToTriggerMapper {
  private final TriggerFilterStore triggerFilterStore;

  public WebhookEventMappingResponse mapWebhookEventToTriggers(TriggerMappingRequestData mappingRequestData) {
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().originalEvent(mappingRequestData.getTriggerWebhookEvent()).build();

    // Generate list of all filters to be applied
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(webhookPayloadData.getOriginalEvent().getAccountId())
                                              .webhookPayloadData(webhookPayloadData)
                                              .isCustomTrigger(true)
                                              .build();
    List<TriggerFilter> triggerFilters =
        triggerFilterStore.getWebhookTriggerFilters(filterRequestData.getWebhookPayloadData());

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
      return triggerFilterInAction.getWebhookResponseForException(filterRequestData, e);
    }

    return webhookEventMappingResponse;
  }
}
