/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventStatusHelper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class NGTriggerEventHistoryMapper {
  public NGTriggerEventHistoryDTO toTriggerEventHistoryDto(
      TriggerEventHistory triggerEventHistory, NGTriggerEntity triggerEntity) {
    return NGTriggerEventHistoryDTO.builder()
        .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
        .accountId(triggerEventHistory.getAccountId())
        .orgIdentifier(triggerEventHistory.getOrgIdentifier())
        .projectIdentifier(triggerEventHistory.getProjectIdentifier())
        .targetIdentifier(triggerEventHistory.getTargetIdentifier())
        .eventCorrelationId(triggerEventHistory.getEventCorrelationId())
        .payload(triggerEventHistory.getPayload())
        .eventCreatedAt(triggerEventHistory.getEventCreatedAt())
        .finalStatus(
            EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus(), null))
        .triggerEventStatus(TriggerEventStatusHelper.toStatus(
            EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus(), null)))
        .message(triggerEventHistory.getMessage())
        .targetExecutionSummary(triggerEventHistory.getTargetExecutionSummary())
        .type(triggerEntity.getType())
        .build();
  }
}
