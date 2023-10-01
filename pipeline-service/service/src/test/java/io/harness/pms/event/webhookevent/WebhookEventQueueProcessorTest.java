/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookEventQueueProcessorTest extends CategoryTest {
  @Mock TriggerWebhookExecutionServiceV2 triggerWebhookExecutionServiceV2;
  @InjectMocks WebhookEventQueueProcessor webhookEventQueueProcessor;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetTopicName() {
    assertEquals(webhookEventQueueProcessor.getTopicName(), "ng" + WEBHOOK_EVENT);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessResponse() {
    String payload =
        "{\"__recast\":\"io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO\",\"__encodedValue\":\"{\\n  \\\"accountId\\\": \\\"kmpySmUISimoRrJL6NL73w\\\",\\n  \\\"eventId\\\": \\\"JrGCELEXS4CrZVrmQcya7A\\\",\\n  \\\"time\\\": \\\"1688639726243\\\"\\n}\"}";
    HsqsProcessMessageResponse processMessageResponse =
        webhookEventQueueProcessor.processResponse(DequeueResponse.builder().itemId("itemId").payload(payload).build());
    WebhookDTO webhookDTO = RecastOrchestrationUtils.fromJson(payload, WebhookDTO.class);
    verify(triggerWebhookExecutionServiceV2, times(1)).processEvent(webhookDTO);
    assertTrue(processMessageResponse.getSuccess());
    assertEquals(processMessageResponse.getAccountId(), "kmpySmUISimoRrJL6NL73w");
    assertThatThrownBy(()
                           -> webhookEventQueueProcessor.processResponse(
                               DequeueResponse.builder().itemId("itemId").payload("payload").build()))
        .hasMessage("Exception while processing webhook event")
        .isInstanceOf(InvalidRequestException.class);
  }
}
