/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PUSH_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_TRIGGER_EVENT;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.webhookpayloads.webhookdata.GitDetails;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookTriggerEventQueueProcessorTest extends CategoryTest {
  @InjectMocks WebhookTriggerEventQueueProcessor webhookTriggerEventQueueProcessor;
  @Mock WebhookHelper webhookHelper;
  @Mock HsqsClientService hsqsClientService;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetTopicName() {
    assertEquals(webhookTriggerEventQueueProcessor.getTopicName(), "ng" + WEBHOOK_TRIGGER_EVENT);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOAndEnqueue() {
    WebhookEvent event = WebhookEvent.builder()
                             .accountId("accountId")
                             .uuid(generateUuid())
                             .createdAt(0L)
                             .headers(List.of(HeaderConfig.builder().key(X_GIT_HUB_EVENT).build()))
                             .build();
    doReturn(SourceRepoType.GITHUB).when(webhookHelper).getSourceRepoType(event);
    doReturn(null).when(webhookHelper).invokeScmService(event, SourceRepoType.GITHUB);
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setAccountId("accountId")
            .setGitDetails(GitDetails.newBuilder()
                               .setSourceRepoType(SourceRepoType.GITHUB)
                               .setEvent(WebhookEventType.PUSH)
                               .build())
            .setParsedResponse(ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build())
            .build();
    doReturn(webhookDTO).when(webhookHelper).generateWebhookDTO(event, null, SourceRepoType.GITHUB);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic("ng" + WEBHOOK_EVENT)
                                        .subTopic("accountId")
                                        .producerName("ng" + WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueRequest pushEnqueueRequest = EnqueueRequest.builder()
                                            .topic("ng" + WEBHOOK_PUSH_EVENT)
                                            .subTopic("accountId")
                                            .producerName("ng" + WEBHOOK_PUSH_EVENT)
                                            .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                            .build();
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(enqueueRequest);
    doReturn(EnqueueResponse.builder().itemId("itemId2").build()).when(hsqsClientService).enqueue(pushEnqueueRequest);
    assertThatCode(() -> webhookTriggerEventQueueProcessor.generateWebhookDTOAndEnqueue(event))
        .doesNotThrowAnyException();
    verify(hsqsClientService, times(1)).enqueue(enqueueRequest);
    verify(hsqsClientService, times(1)).enqueue(pushEnqueueRequest);
  }
}
