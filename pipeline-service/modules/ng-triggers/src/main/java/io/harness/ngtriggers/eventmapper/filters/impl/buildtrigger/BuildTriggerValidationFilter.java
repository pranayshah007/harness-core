/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.ALL_MAPPED_TRIGGER_FAILED_VALIDATION_FOR_POLLING_EVENT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.UnMatchedTriggerInfo;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.ngtriggers.validations.ValidationResult;

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
public class BuildTriggerValidationFilter implements TriggerFilter {
  private final TriggerValidationHandler triggerValidationHandler;
  private final BuildTriggerHelper buildTriggerHelper;
  private final NGTriggerService ngTriggerService;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    List<UnMatchedTriggerInfo> unMatchedTriggersInfoList = new ArrayList<>();

    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      try {
        if (checkTriggerEligibility(trigger)) {
          matchedTriggers.add(trigger);
        } else {
          UnMatchedTriggerInfo unMatchedTriggerInfo =
              UnMatchedTriggerInfo.builder()
                  .unMatchedTriggers(trigger)
                  .finalStatus(TriggerEventResponse.FinalStatus.VALIDATION_FAILED_FOR_TRIGGER)
                  .message(trigger.getNgTriggerEntity().getIdentifier()
                      + " didn't match polling event after event condition evaluation")
                  .build();
          unMatchedTriggersInfoList.add(unMatchedTriggerInfo);
        }
      } catch (Exception e) {
        log.error(getTriggerSkipMessage(trigger.getNgTriggerEntity()), e);
      }
    }

    mappingResponseBuilder.unMatchedTriggerInfoList(unMatchedTriggersInfoList);

    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched polling event after event condition evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(
              TriggerEventResponseHelper.toResponse(ALL_MAPPED_TRIGGER_FAILED_VALIDATION_FOR_POLLING_EVENT,
                  filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
                  "All Mapped Triggers failed validation for Event: "
                      + buildTriggerHelper.generatePollingDescriptor(filterRequestData.getPollingResponse()),
                  null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }

  boolean checkTriggerEligibility(TriggerDetails triggerDetails) {
    boolean result = true;
    try {
      ValidationResult validationResult = triggerValidationHandler.applyValidations(triggerDetails);
      ngTriggerService.updateTriggerWithValidationStatus(triggerDetails.getNgTriggerEntity(), validationResult, true);

      if (!validationResult.isSuccess()) {
        log.error("Error while requesting pipeline execution for Build Trigger: "
            + TriggerHelper.getTriggerRef(triggerDetails.getNgTriggerEntity()));
        result = false;
      }
    } catch (Exception e) {
      log.error(String.format("Failed while validating trigger: %s during Build Event Processing",
                    TriggerHelper.getTriggerRef(triggerDetails.getNgTriggerEntity())),
          e);
      result = false;
    }

    return result;
  }
}
