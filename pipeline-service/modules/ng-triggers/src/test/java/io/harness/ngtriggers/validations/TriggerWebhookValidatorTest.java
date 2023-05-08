/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.validations;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class TriggerWebhookValidatorTest extends CategoryTest {
  @Mock NGTriggerService ngTriggerService;
  @InjectMocks TriggerWebhookValidator triggerWebhookValidator;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void applyValidationsForCustomWebhookWithoutWebhookTokenTest() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("accountId")
                                                  .orgIdentifier("orgId")
                                                  .projectIdentifier("projId")
                                                  .pipelineIdentifier("pipelineId")
                                                  .triggerIdentifier("triggerId")
                                                  .build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().build();
    doReturn(Collections.singletonList(ngTriggerEntity))
        .when(ngTriggerService)
        .findTriggersForCustomWehbook(triggerWebhookEvent, false, true);
    triggerWebhookValidator.applyValidationsForCustomWebhook(triggerWebhookEvent, null);
    Mockito.doReturn(Collections.emptyList())
        .when(ngTriggerService)
        .findTriggersForCustomWehbook(triggerWebhookEvent, false, true);
    assertThatThrownBy(() -> triggerWebhookValidator.applyValidationsForCustomWebhook(triggerWebhookEvent, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "No enabled custom trigger found for Account:accountId, Org: orgId, Project: projId, Pipeline: pipelineId, Trigger: triggerId");
    ngTriggerEntity.setCustomWebhookToken("webhookToken");
    doReturn(Collections.singletonList(ngTriggerEntity))
        .when(ngTriggerService)
        .findTriggersForCustomWehbook(triggerWebhookEvent, false, true);
    assertThatThrownBy(() -> triggerWebhookValidator.applyValidationsForCustomWebhook(triggerWebhookEvent, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "This webhook url is no longer supported for newly created custom webhook triggers. We are migrating to new webhook urls using custom webhook tokens in the url.");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void applyValidationsForCustomWebhookWithWebhookTokenTest() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("accountId")
                                                  .orgIdentifier("orgId")
                                                  .projectIdentifier("projId")
                                                  .pipelineIdentifier("pipelineId")
                                                  .triggerIdentifier("triggerId")
                                                  .build();
    doReturn(Optional.ofNullable(NGTriggerEntity.builder().enabled(true).deleted(false).build()))
        .when(ngTriggerService)
        .findTriggersForCustomWebhookViaCustomWebhookToken("webhookToken");
    triggerWebhookValidator.applyValidationsForCustomWebhook(triggerWebhookEvent, "webhookToken");
    doReturn(Optional.empty()).when(ngTriggerService).findTriggersForCustomWebhookViaCustomWebhookToken("webhookToken");
    assertThatThrownBy(
        () -> triggerWebhookValidator.applyValidationsForCustomWebhook(triggerWebhookEvent, "webhookToken"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No custom trigger found for the used custom webhook token: webhookToken");
    doReturn(Optional.ofNullable(NGTriggerEntity.builder().enabled(false).deleted(false).build()))
        .when(ngTriggerService)
        .findTriggersForCustomWebhookViaCustomWebhookToken("webhookToken");
    assertThatThrownBy(
        () -> triggerWebhookValidator.applyValidationsForCustomWebhook(triggerWebhookEvent, "webhookToken"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No enabled custom trigger found for the used custom webhook token: webhookToken");
  }
}
