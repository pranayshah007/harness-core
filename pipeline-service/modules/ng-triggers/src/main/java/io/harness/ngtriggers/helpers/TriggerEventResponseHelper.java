/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_DETAILS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_PAYLOAD;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NEW_ARTIFACT_EVENT_PROCESSED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NEW_MANIFEST_EVENT_PROCESSED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_PROJECT;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_EVENT_ACTION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_HEADER_CONDITIONS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_REPO;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_DID_NOT_EXECUTE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_SUCCESSFUL;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.VALIDATION_FAILED_FOR_TRIGGER;

import io.harness.execution.PlanExecution;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse;
import io.harness.ngtriggers.dtos.NGPipelineExecutionResponseDTO;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.EnumSet;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TriggerEventResponseHelper {
  public TriggerEventResponse toResponse(TriggerEventResponse.FinalStatus status,
      TriggerWebhookEvent triggerWebhookEvent, NGPipelineExecutionResponseDTO pipelineExecutionResponseDTO,
      NGTriggerEntity ngTriggerEntity, String message, TargetExecutionSummary targetExecutionSummary) {
    TriggerEventResponse response =
        TriggerEventResponse.builder()
            .accountId(triggerWebhookEvent.getAccountId())
            .orgIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getOrgIdentifier())
            .projectIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getProjectIdentifier())
            .targetIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getTargetIdentifier())
            .eventCorrelationId(triggerWebhookEvent.getUuid())
            .payload(triggerWebhookEvent.getPayload())
            .createdAt(triggerWebhookEvent.getCreatedAt())
            .finalStatus(status)
            .triggerIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getIdentifier())
            .message(message)
            .ngTriggerType(ngTriggerEntity == null ? null : ngTriggerEntity.getType())
            .targetExecutionSummary(targetExecutionSummary)
            .build();
    if (pipelineExecutionResponseDTO == null) {
      response.setExceptionOccurred(true);
      return response;
    }
    response.setExceptionOccurred(false);
    return response;
  }

  public TriggerEventResponse toResponse(TriggerEventResponse.FinalStatus status,
      TriggerWebhookEvent triggerWebhookEvent, NGTriggerEntity ngTriggerEntity, String message,
      TargetExecutionSummary targetExecutionSummary) {
    TriggerEventResponse response =
        TriggerEventResponse.builder()
            .accountId(triggerWebhookEvent.getAccountId())
            .orgIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getOrgIdentifier())
            .projectIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getProjectIdentifier())
            .targetIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getTargetIdentifier())
            .eventCorrelationId(triggerWebhookEvent.getUuid())
            .payload(triggerWebhookEvent.getPayload())
            .createdAt(triggerWebhookEvent.getCreatedAt())
            .finalStatus(status)
            .triggerIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getIdentifier())
            .message(message)
            .targetExecutionSummary(targetExecutionSummary)
            .ngTriggerType(ngTriggerEntity.getType())
            .build();
    response.setExceptionOccurred(false);
    return response;
  }

  public TriggerEventResponse toResponse(TriggerEventResponse.FinalStatus status, NGTriggerEntity ngTriggerEntity,
      String message, TargetExecutionSummary targetExecutionSummary) {
    TriggerEventResponse response =
        TriggerEventResponse.builder()
            .accountId(ngTriggerEntity.getAccountId())
            .orgIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getOrgIdentifier())
            .projectIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getProjectIdentifier())
            .targetIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getTargetIdentifier())
            .createdAt(System.currentTimeMillis())
            .finalStatus(status)
            .triggerIdentifier(ngTriggerEntity == null ? null : ngTriggerEntity.getIdentifier())
            .message(message)
            .targetExecutionSummary(targetExecutionSummary)
            .ngTriggerType(ngTriggerEntity.getType())
            .build();
    response.setExceptionOccurred(false);
    return response;
  }

  public boolean isFinalStatusAnEvent(TriggerEventResponse.FinalStatus status) {
    Set<FinalStatus> set = EnumSet.of(INVALID_PAYLOAD, INVALID_RUNTIME_INPUT_YAML, TARGET_DID_NOT_EXECUTE,
        TARGET_EXECUTION_REQUESTED, NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE, NO_ENABLED_TRIGGER_FOR_PROJECT,
        NO_MATCHING_TRIGGER_FOR_REPO, NO_MATCHING_TRIGGER_FOR_EVENT_ACTION, NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS,
        NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS, NO_MATCHING_TRIGGER_FOR_HEADER_CONDITIONS,
        EXCEPTION_WHILE_PROCESSING, FAILED_TO_FETCH_PR_DETAILS, TRIGGER_CONFIRMATION_FAILED,
        TRIGGER_CONFIRMATION_SUCCESSFUL, VALIDATION_FAILED_FOR_TRIGGER, NEW_ARTIFACT_EVENT_PROCESSED,
        NEW_MANIFEST_EVENT_PROCESSED);
    return set.contains(status);
  }

  public TriggerEventHistory toEntity(TriggerEventResponse response) {
    return TriggerEventHistory.builder()
        .accountId(response.getAccountId())
        .orgIdentifier(response.getOrgIdentifier())
        .projectIdentifier(response.getProjectIdentifier())
        .targetIdentifier(response.getTargetIdentifier())
        .eventCorrelationId(response.getEventCorrelationId())
        .payload(response.getPayload())
        .eventCreatedAt(response.getCreatedAt())
        .finalStatus(response.getFinalStatus().toString())
        .message(response.getMessage())
        .exceptionOccurred(response.isExceptionOccurred())
        .triggerIdentifier(response.getTriggerIdentifier())
        .targetExecutionSummary(response.getTargetExecutionSummary())
        .build();
  }

  public TriggerEventResponse prepareResponseForScmException(ParsePayloadResponse parsePayloadReponse) {
    TriggerEventResponse.FinalStatus status = INVALID_PAYLOAD;
    Exception exception = parsePayloadReponse.getException();
    if (StatusRuntimeException.class.isAssignableFrom(exception.getClass())) {
      StatusRuntimeException e = (StatusRuntimeException) exception;

      if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        status = SCM_SERVICE_CONNECTION_FAILED;
      }
    }
    return toResponse(status, parsePayloadReponse.getWebhookPayloadData().getOriginalEvent(), null, null,
        exception.getMessage(), null);
  }

  public TargetExecutionSummary prepareTargetExecutionSummary(
      NGPipelineExecutionResponseDTO ngPipelineExecutionResponseDTO, TriggerDetails triggerDetails,
      String runtimeInputYaml) {
    if (ngPipelineExecutionResponseDTO == null) {
      return TargetExecutionSummary.builder()
          .triggerId(triggerDetails.getNgTriggerEntity().getIdentifier())
          .targetId(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
          .runtimeInput(runtimeInputYaml)
          .build();
    } else {
      return TargetExecutionSummary.builder()
          .targetId(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
          .planExecutionId(ngPipelineExecutionResponseDTO.getPlanExecution().getUuid())
          .executionStatus(ngPipelineExecutionResponseDTO.getPlanExecution().getStatus().name())
          .triggerId(triggerDetails.getNgTriggerEntity().getIdentifier())
          .runtimeInput(runtimeInputYaml)
          .startTs(ngPipelineExecutionResponseDTO.getPlanExecution().getStartTs())
          .build();
    }
  }

  public TargetExecutionSummary prepareTargetExecutionSummary(
      PlanExecution planExecution, TriggerDetails triggerDetails, String runtimeInputYaml) {
    if (planExecution == null) {
      return TargetExecutionSummary.builder()
          .triggerId(triggerDetails.getNgTriggerEntity().getIdentifier())
          .targetId(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
          .runtimeInput(runtimeInputYaml)
          .build();
    } else {
      return TargetExecutionSummary.builder()
          .targetId(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
          .planExecutionId(planExecution.getUuid())
          .executionStatus(planExecution.getStatus().name())
          .triggerId(triggerDetails.getNgTriggerEntity().getIdentifier())
          .runtimeInput(runtimeInputYaml)
          .startTs(planExecution.getStartTs())
          .build();
    }
  }
}
