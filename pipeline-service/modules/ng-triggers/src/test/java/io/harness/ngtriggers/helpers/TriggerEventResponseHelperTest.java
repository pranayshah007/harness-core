/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.AMIRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.dtos.NGPipelineExecutionResponseDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TriggerEventResponseHelperTest extends CategoryTest {
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String triggerIdentifier = "triggerIdentifier";
  String pollingDocId = "pollingDocId";
  String message = "message";
  String uuid = "uuid";
  String payload = "payload";
  String build = "build";
  Long createdAt = 12L;
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testToResponseWithPollingInfo() {
    TriggerEventResponse.FinalStatus status = TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().accountId(accountId).uuid(uuid).createdAt(createdAt).payload(payload).build();
    NGPipelineExecutionResponseDTO ngPipelineExecutionResponseDTO = NGPipelineExecutionResponseDTO.builder().build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .identifier(triggerIdentifier)
                                          .targetIdentifier(pipelineId)
                                          .type(NGTriggerType.ARTIFACT)
                                          .build();
    NGTriggerConfigV2 ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                                              .source(NGTriggerSourceV2.builder()
                                                          .spec(ArtifactTriggerConfig.builder()
                                                                    .spec(AMIRegistrySpec.builder().build())
                                                                    .type(ArtifactType.AMI)
                                                                    .build())
                                                          .build())
                                              .build();
    TargetExecutionSummary targetExecutionSummary = TargetExecutionSummary.builder().targetId(pipelineId).build();

    TriggerEventResponse response = TriggerEventResponse.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .targetIdentifier(pipelineId)
                                        .eventCorrelationId(uuid)
                                        .payload(payload)
                                        .createdAt(createdAt)
                                        .finalStatus(status)
                                        .triggerIdentifier(triggerIdentifier)
                                        .message(message)
                                        .buildSourceType(ArtifactType.AMI.getValue())
                                        .build(build)
                                        .ngTriggerType(NGTriggerType.ARTIFACT)
                                        .targetExecutionSummary(targetExecutionSummary)
                                        .pollingDocId(pollingDocId)
                                        .build();
    TriggerEventResponse triggerEventResponse = TriggerEventResponseHelper.toResponseWithPollingInfo(status,
        triggerWebhookEvent, ngPipelineExecutionResponseDTO, ngTriggerEntity, ngTriggerConfigV2, message,
        targetExecutionSummary, pollingDocId, build);
    assertThat(triggerEventResponse).isEqualTo(response);

    // Without pipeline execution details
    TriggerEventResponse triggerEventResponse1 = TriggerEventResponseHelper.toResponseWithPollingInfo(status,
        triggerWebhookEvent, ngTriggerEntity, ngTriggerConfigV2, message, targetExecutionSummary, pollingDocId, build);
    assertThat(triggerEventResponse1).isEqualTo(response);
  }
}
