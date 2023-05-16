/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.triggers.webhook.service;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookEventExecutionServiceImpl;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TriggerWebhookEventExecutionServiceImplTest extends CategoryTest {
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock NGTriggerRepository ngTriggerRepository;
  @Mock TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @InjectMocks TriggerWebhookEventExecutionServiceImpl triggerWebhookEventExecutionService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessEvent() {
    TriggerExecutionDTO triggerExecutionDTO =
        TriggerExecutionDTO.newBuilder()
            .setAccountId("accId")
            .setOrgIdentifier("orgId")
            .setProjectIdentifier("projId")
            .setTriggerIdentifier("triggerId")
            .setTargetIdentifier("pipId")
            .setWebhookDto(WebhookDTO.newBuilder()
                               .addHeaders(EventHeader.newBuilder().setKey("key1").addValues("value1").build())
                               .build())
            .build();
    doReturn(TriggerWebhookEvent.builder())
        .when(ngTriggerElementMapper)
        .toNGTriggerWebhookEvent(any(), any(), any(), any(), any());
    doReturn(Optional.of(NGTriggerEntity.builder()
                             .accountId("accountId")
                             .orgIdentifier("orgId")
                             .projectIdentifier("projId")
                             .targetIdentifier("targetId")
                             .identifier("triggerId")
                             .build()))
        .when(ngTriggerRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    doReturn(NGTriggerConfigV2.builder().build())
        .when(ngTriggerElementMapper)
        .toTriggerConfigV2((NGTriggerEntity) any());
    doReturn(NGTriggerEntity.builder().build()).when(ngTriggerRepository).updateValidationStatus(any(), any());
    doNothing()
        .when(ngTriggerWebhookExecutionHelper)
        .updateWebhookRegistrationStatusAndTriggerPipelineExecution(any(), any(), any(), any());
    triggerWebhookEventExecutionService.processEvent(triggerExecutionDTO);
  }
}
