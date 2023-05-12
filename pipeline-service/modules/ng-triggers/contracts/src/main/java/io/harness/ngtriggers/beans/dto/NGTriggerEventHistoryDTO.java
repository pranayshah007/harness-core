/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("NGTriggerEventHistoryResponse")
@OwnedBy(PIPELINE)
public class NGTriggerEventHistoryDTO extends NGTriggerEventHistoryBaseDTO {
  String triggerIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String targetIdentifier;
  TargetExecutionSummary targetExecutionSummary;
  NGTriggerType type;

  public NGTriggerEventHistoryDTO(String accountId, String eventCorrelationId, String payload, Long eventCreatedAt,
      TriggerEventResponse.FinalStatus finalStatus, String message, Boolean exceptionOccurred, Long createdAt,
      String triggerIdentifier, String orgIdentifier, String projectIdentifier, String targetIdentifier,
      TargetExecutionSummary targetExecutionSummary, NGTriggerType type) {
    super(accountId, eventCorrelationId, payload, eventCreatedAt, finalStatus, message, exceptionOccurred, createdAt);
    this.triggerIdentifier = triggerIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.targetIdentifier = targetIdentifier;
    this.targetExecutionSummary = targetExecutionSummary;
    this.type = type;
  }
}
