/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_REQUEST_PAYLOAD_DETAILS;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.GIT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.name.Named;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WebhookEventPublisherTest extends CategoryTest {
  @Mock @Named(WEBHOOK_REQUEST_PAYLOAD_DETAILS) private Producer eventProducer;
  @InjectMocks private WebhookEventPublisher webhookEventPublisher;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPublishGitWebhookEvent() {
    WebhookEventType eventType = WebhookEventType.PR;
    WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder()
                                                .parseWebhookResponse(ParseWebhookResponse.newBuilder().build())
                                                .originalEvent(TriggerWebhookEvent.builder()
                                                                   .accountId("acctId")
                                                                   .uuid("uuid")
                                                                   .sourceRepoType("GIT")
                                                                   .payload("payload")
                                                                   .build())
                                                .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder()
                                .setWebhookEventType(eventType)
                                .setWebhookTriggerType(GIT)
                                .setJsonPayload(webhookPayloadData.getOriginalEvent().getPayload())
                                .setParsedResponse(webhookPayloadData.getParseWebhookResponse())
                                .build();
    Message expectedMessage =
        Message.newBuilder()
            .putAllMetadata(ImmutableMap.of("accountId", webhookPayloadData.getOriginalEvent().getAccountId(),
                "correlationId", webhookPayloadData.getOriginalEvent().getUuid(), "sourceRepoType",
                webhookPayloadData.getOriginalEvent().getSourceRepoType()))
            .setData(webhookDTO.toByteString())
            .build();
    doReturn(null).when(eventProducer).send(any());
    webhookEventPublisher.publishGitWebhookEvent(webhookPayloadData, eventType);
    verify(eventProducer, times(1)).send(expectedMessage);
  }
}
