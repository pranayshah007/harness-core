/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.build.eventmapper;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.POLLING_EVENT_WITH_NO_VERSIONS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ngtriggers.beans.dto.eventmapping.UnMatchedTriggerInfo;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerFilterStore;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class BuildTriggerEventMapper {
  private final NGTriggerService ngTriggerService;
  private final TriggerValidationHandler triggerValidationHandler;
  private final TriggerEventExecutionHelper triggerEventExecutionHelper;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final TriggerEventHistoryRepository triggerEventHistoryRepository;
  private final BuildTriggerHelper buildTriggerHelper;
  private final TriggerFilterStore triggerFilterStore;

  public WebhookEventMappingResponse consumeBuildTriggerEvent(PollingResponse pollingResponse) {
    String pollingDescriptor = buildTriggerHelper.generatePollingDescriptor(pollingResponse);

    log.info("Received polling response for signatures {} of pollingEvent {}", pollingResponse.getSignaturesList(),
        pollingDescriptor);
    int signaturesCount = pollingResponse.getSignaturesCount();
    if (signaturesCount <= 0) {
      String msg =
          String.format("Received PollingResponse with 0 signatures. No trigger evaluation needed for PollingEvent: %s",
              pollingDescriptor);
      log.warn(msg);
      return generateWebhookEventMappingResponseUsingError(
          NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES, pollingResponse, msg);
    }

    if (!pollingResponse.hasBuildInfo()
        || (pollingResponse.hasBuildInfo() && pollingResponse.getBuildInfo().getVersionsCount() == 0)) {
      String msg =
          String.format("Received PollingResponse with 0 versions. No trigger evaluation needed for PollingEvent: %s",
              pollingDescriptor);
      log.warn(msg);
      return generateWebhookEventMappingResponseUsingError(POLLING_EVENT_WITH_NO_VERSIONS, pollingResponse, msg);
    }

    List<TriggerFilter> triggerFilters = triggerFilterStore.getBuildTriggerFiltersDefaultList();
    // Apply filters
    WebhookEventMappingResponse webhookEventMappingResponse = null;
    TriggerFilter triggerFilterInAction = null;
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId(pollingResponse.getAccountId())
            .pollingResponse(pollingResponse)
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .originalEvent(TriggerWebhookEvent.builder()
                                                       .accountId(pollingResponse.getAccountId())
                                                       .createdAt(System.currentTimeMillis())
                                                       .uuid(UUIDGenerator.generateUuid())
                                                       .payload(pollingDescriptor)
                                                       .build())
                                    .build())
            .build();
    try {
      for (TriggerFilter triggerFilter : triggerFilters) {
        triggerFilterInAction = triggerFilter;
        webhookEventMappingResponse = triggerFilter.applyFilter(filterRequestData);
        saveEventHistoryForNotMatchedTriggers(webhookEventMappingResponse);
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

  private void saveEventHistoryForNotMatchedTriggers(WebhookEventMappingResponse webhookEventMappingResponse) {
    for (UnMatchedTriggerInfo unMatchedTriggerInfo : webhookEventMappingResponse.getUnMatchedTriggerInfoList()) {
      triggerEventHistoryRepository.save(
          TriggerEventHistory.builder()
              .triggerIdentifier(unMatchedTriggerInfo.getUnMatchedTriggers().getNgTriggerEntity().getIdentifier())
              .pollingDocId(unMatchedTriggerInfo.getUnMatchedTriggers()
                                .getNgTriggerEntity()
                                .getMetadata()
                                .getBuildMetadata()
                                .getPollingConfig()
                                .getPollingDocId())
              .projectIdentifier(
                  unMatchedTriggerInfo.getUnMatchedTriggers().getNgTriggerEntity().getProjectIdentifier())
              .finalStatus(unMatchedTriggerInfo.getFinalStatus().toString())
              .message(unMatchedTriggerInfo.getMessage())
              .orgIdentifier(unMatchedTriggerInfo.getUnMatchedTriggers().getNgTriggerEntity().getOrgIdentifier())
              .targetIdentifier(unMatchedTriggerInfo.getUnMatchedTriggers().getNgTriggerEntity().getTargetIdentifier())
              .triggerIdentifier(unMatchedTriggerInfo.getUnMatchedTriggers().getNgTriggerEntity().getIdentifier())
              .accountId(unMatchedTriggerInfo.getUnMatchedTriggers().getNgTriggerEntity().getAccountId())
              .executionNotAttempted(true)
              .build());
    }
  }

  private WebhookEventMappingResponse generateWebhookEventMappingResponseUsingError(
      TriggerEventResponse.FinalStatus finalStatus, PollingResponse pollingResponse, String msg) {
    return WebhookEventMappingResponse.builder()
        .failedToFindTrigger(true)
        .webhookEventResponse(TriggerEventResponseHelper.toResponse(finalStatus,
            TriggerWebhookEvent.builder()
                .accountId(pollingResponse.getAccountId())
                .createdAt(System.currentTimeMillis())
                .payload(buildTriggerHelper.generatePollingDescriptor(pollingResponse))
                .uuid(UUIDGenerator.generateUuid())
                .build(),
            null, null, msg, null))
        .build();
  }
}
