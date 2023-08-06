/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
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
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_REPO;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.POLLING_EVENT_WITH_NO_VERSIONS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_DID_NOT_EXECUTE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_SUCCESSFUL;
import static io.harness.ngtriggers.beans.target.TargetType.PIPELINE;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static io.grpc.Status.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse.ParsePayloadResponseBuilder;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.dtos.NGPipelineExecutionResponseDTO;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;

import io.grpc.StatusRuntimeException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WebhookEventResponseHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void toResponse() {
    FinalStatus status = TARGET_EXECUTION_REQUESTED;
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId("accountId")
                                          .projectIdentifier("projectId")
                                          .orgIdentifier("orgId")
                                          .identifier("triggerId")
                                          .targetIdentifier("targetId")
                                          .targetType(PIPELINE)
                                          .build();
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().createdAt(123l).payload("payload").accountId("accountId").build();

    NGPipelineExecutionResponseDTO ngPipelineExecutionResponseDTO = NGPipelineExecutionResponseDTO.builder().build();
    TargetExecutionSummary summary = TargetExecutionSummary.builder().build();
    String message = "msg";

    TriggerEventResponse webhookEventResponse = TriggerEventResponseHelper.toResponse(
        status, triggerWebhookEvent, ngPipelineExecutionResponseDTO, ngTriggerEntity, message, summary);

    assertThat(webhookEventResponse).isNotNull();
    assertThat(webhookEventResponse.getAccountId()).isEqualTo("accountId");
    assertThat(webhookEventResponse.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(webhookEventResponse.getProjectIdentifier()).isEqualTo("projectId");
    assertThat(webhookEventResponse.getTargetIdentifier()).isEqualTo("targetId");
    assertThat(webhookEventResponse.getTriggerIdentifier()).isEqualTo("triggerId");

    assertThat(webhookEventResponse.getPayload()).isEqualTo("payload");
    assertThat(webhookEventResponse.getCreatedAt()).isEqualTo(123l);
    assertThat(webhookEventResponse.getMessage()).isEqualTo("msg");

    assertThat(webhookEventResponse.getTargetExecutionSummary()).isEqualTo(summary);
    assertThat(webhookEventResponse.isExceptionOccurred()).isFalse();

    webhookEventResponse =
        TriggerEventResponseHelper.toResponse(status, triggerWebhookEvent, null, ngTriggerEntity, message, summary);
    assertThat(webhookEventResponse.isExceptionOccurred()).isTrue();

    // test is NgTriggerEntity is null, no NPE is thrown
    webhookEventResponse =
        TriggerEventResponseHelper.toResponse(status, triggerWebhookEvent, null, null, message, summary);
    assertThat(webhookEventResponse).isNotNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsFinalStatusAnEvent() {
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(INVALID_PAYLOAD)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(NO_MATCHING_TRIGGER_FOR_REPO)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(INVALID_RUNTIME_INPUT_YAML)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(TARGET_DID_NOT_EXECUTE)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(NO_MATCHING_TRIGGER_FOR_EVENT_ACTION)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(NO_ENABLED_TRIGGER_FOR_PROJECT)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(EXCEPTION_WHILE_PROCESSING)).isTrue();
    assertThat(TriggerEventResponseHelper.isFinalStatusAnEvent(FAILED_TO_FETCH_PR_DETAILS)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPrepareResponseForScmException() {
    TriggerWebhookEvent event =
        TriggerWebhookEvent.builder().createdAt(123l).payload("payload").accountId("accountId").build();

    ParsePayloadResponseBuilder parsePayloadResponse =
        ParsePayloadResponse.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(event).build())
            .exceptionOccured(true)
            .exception(new InvalidRequestException("test"));

    TriggerEventResponse webhookEventResponse =
        TriggerEventResponseHelper.prepareResponseForScmException(parsePayloadResponse.build());
    assertThat(webhookEventResponse.isExceptionOccurred()).isTrue();
    assertThat(webhookEventResponse.getMessage()).isEqualTo("test");
    assertThat(webhookEventResponse.getFinalStatus()).isEqualTo(INVALID_PAYLOAD);
    assertThat(webhookEventResponse.getPayload()).isEqualTo("payload");

    parsePayloadResponse.exception(new StatusRuntimeException(UNAVAILABLE));
    webhookEventResponse = TriggerEventResponseHelper.prepareResponseForScmException(parsePayloadResponse.build());
    assertThat(webhookEventResponse.isExceptionOccurred()).isTrue();
    assertThat(webhookEventResponse.getFinalStatus()).isEqualTo(SCM_SERVICE_CONNECTION_FAILED);
    assertThat(webhookEventResponse.getPayload()).isEqualTo("payload");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPrepareTargetExecutionSummary() {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(NGTriggerEntity.builder()
                                                             .accountId("accountId")
                                                             .projectIdentifier("projectId")
                                                             .orgIdentifier("orgId")
                                                             .identifier("triggerId")
                                                             .targetIdentifier("targetId")
                                                             .targetType(PIPELINE)
                                                             .build())
                                        .build();

    String runtimeInputYaml = "runtime";

    TargetExecutionSummary targetExecutionSummary = TriggerEventResponseHelper.prepareTargetExecutionSummary(
        (NGPipelineExecutionResponseDTO) null, triggerDetails, runtimeInputYaml);
    assertThat(targetExecutionSummary).isNotNull();
    assertThat(targetExecutionSummary.getTriggerId()).isEqualTo("triggerId");
    assertThat(targetExecutionSummary.getTargetId()).isEqualTo("targetId");

    targetExecutionSummary = TriggerEventResponseHelper.prepareTargetExecutionSummary(
        NGPipelineExecutionResponseDTO.builder()
            .planExecution(PlanExecution.builder()
                               .uuid("planUuid")
                               .startTs(123l)
                               .status(RUNNING)
                               .metadata(ExecutionMetadata.newBuilder().setRunSequence(1).build())
                               .build())
            .build(),
        triggerDetails, runtimeInputYaml);
    assertThat(targetExecutionSummary).isNotNull();
    assertThat(targetExecutionSummary.getTriggerId()).isEqualTo("triggerId");
    assertThat(targetExecutionSummary.getTargetId()).isEqualTo("targetId");
    assertThat(targetExecutionSummary.getPlanExecutionId()).isEqualTo("planUuid");
    assertThat(targetExecutionSummary.getExecutionStatus()).isEqualTo(RUNNING.name());
    assertThat(targetExecutionSummary.getStartTs()).isEqualTo(123l);
    assertThat(targetExecutionSummary.getRunSequence()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testTriggerEventStatusHelper() {
    assertThat(TriggerEventStatusHelper.toStatus(NEW_ARTIFACT_EVENT_PROCESSED))
        .isEqualTo(TriggerEventStatus.builder()
                       .status(TriggerEventStatus.FinalResponse.SUCCESS)
                       .message(NEW_ARTIFACT_EVENT_PROCESSED.getMessage())
                       .build());
    assertThat(TriggerEventStatusHelper.toStatus(NEW_MANIFEST_EVENT_PROCESSED))
        .isEqualTo(TriggerEventStatus.builder()
                       .status(TriggerEventStatus.FinalResponse.SUCCESS)
                       .message(NEW_MANIFEST_EVENT_PROCESSED.getMessage())
                       .build());
    assertThat(TriggerEventStatusHelper.toStatus(TRIGGER_CONFIRMATION_SUCCESSFUL))
        .isEqualTo(TriggerEventStatus.builder()
                       .status(TriggerEventStatus.FinalResponse.SUCCESS)
                       .message(TRIGGER_CONFIRMATION_SUCCESSFUL.getMessage())
                       .build());
    assertThat(TriggerEventStatusHelper.toStatus(TARGET_EXECUTION_REQUESTED))
        .isEqualTo(TriggerEventStatus.builder()
                       .status(TriggerEventStatus.FinalResponse.SUCCESS)
                       .message(TARGET_EXECUTION_REQUESTED.getMessage())
                       .build());
    assertThat(TriggerEventStatusHelper.toStatus(POLLING_EVENT_WITH_NO_VERSIONS))
        .isEqualTo(TriggerEventStatus.builder()
                       .status(TriggerEventStatus.FinalResponse.FAILED)
                       .message(POLLING_EVENT_WITH_NO_VERSIONS.getMessage())
                       .build());
    assertThat(TriggerEventStatusHelper.toStatus(null))
        .isEqualTo(TriggerEventStatus.builder()
                       .status(TriggerEventStatus.FinalResponse.FAILED)
                       .message("Unknown status")
                       .build());
  }
}
