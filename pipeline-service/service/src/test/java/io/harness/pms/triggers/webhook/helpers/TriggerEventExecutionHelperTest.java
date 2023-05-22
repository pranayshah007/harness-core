/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.beans.FeatureName.SPG_SEND_TRIGGER_PIPELINE_FOR_WEBHOOKS_ASYNC;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.execution.PlanExecution;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.WebhookEventMapperHelper;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TriggerEventExecutionHelperTest extends CategoryTest {
  @Inject @InjectMocks TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Mock TriggerExecutionHelper triggerExecutionHelper;
  @Mock WebhookEventMapperHelper webhookEventMapperHelper;
  @Mock TriggerWebhookEventPublisher triggerWebhookEventPublisher;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NGTriggerRepository ngTriggerRepository;
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projectId = "proj";
  private final String pipelineId = "target";
  private TriggerDetails triggerDetails;
  private PollingResponse pollingResponse;
  private NGTriggerEntity ngTriggerEntity;
  private TriggerWebhookEvent triggerWebhookEvent;

  @Before
  public void setUp() {
    triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("CUSTOM")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload("{branch: main}")
            .build();
    MockitoAnnotations.initMocks(this);

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId("acc")
                          .orgIdentifier("org")
                          .projectIdentifier("proj")
                          .targetIdentifier("target")
                          .identifier("trigger")
                          .type(NGTriggerType.ARTIFACT)
                          .build();

    triggerDetails = TriggerDetails.builder()
                         .ngTriggerEntity(ngTriggerEntity)
                         .ngTriggerConfigV2(NGTriggerConfigV2.builder().inputYaml("inputSetYaml").build())
                         .build();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testTriggerEventPipelineExecution() {
    PlanExecution planExecution = PlanExecution.builder().planId("planId").build();
    pollingResponse =
        PollingResponse.newBuilder().setBuildInfo(BuildInfo.newBuilder().addVersions("v1").build()).build();
    doReturn(planExecution)
        .when(triggerExecutionHelper)
        .resolveRuntimeInputAndSubmitExecutionReques(any(), any(), any());
    TriggerEventResponse triggerEventResponse =
        triggerEventExecutionHelper.triggerEventPipelineExecution(triggerDetails, pollingResponse);
    assertThat(triggerEventResponse.getAccountId()).isEqualTo(accountId);
    assertThat(triggerEventResponse.getNgTriggerType()).isEqualTo(NGTriggerType.ARTIFACT);
    assertThat(triggerEventResponse.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(triggerEventResponse.getProjectIdentifier()).isEqualTo(projectId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleTriggerWebhookEvent() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder()
                                    .isSubscriptionConfirmation(true)
                                    .accountId("accountId")
                                    .createdAt(10L)
                                    .sourceRepoType("BITBUCKET")
                                    .attemptCount(0)
                                    .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setEventId("eventId").build();
    TriggerMappingRequestData triggerMappingRequestData =
        TriggerMappingRequestData.builder().triggerWebhookEvent(event).webhookDTO(webhookDTO).build();
    List<TriggerDetails> list = new ArrayList<>();
    list.add(TriggerDetails.builder()
                 .ngTriggerEntity(NGTriggerEntity.builder()
                                      .accountId("accountId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projId")
                                      .targetIdentifier("targetId")
                                      .identifier("triggerId")
                                      .build())
                 .ngTriggerConfigV2(NGTriggerConfigV2.builder().build())
                 .build());
    WebhookEventMappingResponse webhookEventMappingResponse =
        WebhookEventMappingResponse.builder()
            .failedToFindTrigger(false)
            .parseWebhookResponse(ParseWebhookResponse.newBuilder().build())
            .triggers(list)
            .build();
    when(webhookEventMapperHelper.mapWebhookEventToTriggers(triggerMappingRequestData))
        .thenReturn(webhookEventMappingResponse);
    when(pmsFeatureFlagService.isEnabled("accountId", SPG_SEND_TRIGGER_PIPELINE_FOR_WEBHOOKS_ASYNC)).thenReturn(true);
    triggerEventExecutionHelper.handleTriggerWebhookEvent(triggerMappingRequestData);
    verify(triggerWebhookEventPublisher, times(1)).publishTriggerWebhookEvent(any());
    doReturn(NGTriggerEntity.builder().build()).when(ngTriggerRepository).updateValidationStatus(any(), any());
    when(pmsFeatureFlagService.isEnabled("accountId", SPG_SEND_TRIGGER_PIPELINE_FOR_WEBHOOKS_ASYNC)).thenReturn(false);
    triggerEventExecutionHelper.handleTriggerWebhookEvent(triggerMappingRequestData);
    verify(ngTriggerRepository, times(1)).updateValidationStatus(any(), any());
  }
}
