/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class BuildTriggerSignatureFilter implements TriggerFilter {
  private final BuildTriggerHelper buildTriggerHelper;
  private final NGTriggerService ngTriggerService;
  private final NGTriggerElementMapper ngTriggerElementMapper;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();

    List<String> signatures = new ArrayList<>();
    for (int i = 0; i < filterRequestData.getPollingResponse().getSignaturesCount(); i++) {
      signatures.add(filterRequestData.getPollingResponse().getSignatures(i));
    }

    List<NGTriggerEntity> ngTriggerEntities =
        ngTriggerService.findBuildTriggersByAccountIdAndSignature(filterRequestData.getAccountId(), signatures);
    if (isEmpty(ngTriggerEntities)) {
      String msg = "No trigger signature matched with AccountId nad Signature from Event: "
          + buildTriggerHelper.generatePollingDescriptor(filterRequestData.getPollingResponse());
      log.info(msg);
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null, msg, null))
          .build();
    } else {
      prepareTriggerDetails(matchedTriggers, ngTriggerEntities);
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }

    return mappingResponseBuilder.build();
  }

  private void prepareTriggerDetails(List<TriggerDetails> matchedTriggers, List<NGTriggerEntity> ngTriggerEntities) {
    for (NGTriggerEntity ngTriggerEntity : ngTriggerEntities) {
      try {
        matchedTriggers.add(ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity.getAccountId(),
            ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getYaml()));
      } catch (Exception e) {
        log.error("While processing PollingEvent, Failed to generate toTriggerDetails for Trigger: "
                + TriggerHelper.getTriggerRef(ngTriggerEntity),
            e);
      }
    }
  }
}
