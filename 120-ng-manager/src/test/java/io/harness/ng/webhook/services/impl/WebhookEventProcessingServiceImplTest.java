/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.rule.OwnerRule.MEET;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.rule.Owner;
import io.harness.service.WebhookParserSCMService;

import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class WebhookEventProcessingServiceImplTest extends CategoryTest {
  @InjectMocks WebhookEventProcessingServiceImpl webhookEventProcessingService;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock WebhookEventRepository webhookEventRepository;
  @Mock @Named(GIT_PUSH_EVENT_STREAM) private Producer gitPushEventProducer;
  @Mock WebhookParserSCMService webhookParserSCMService;
  @Mock WebhookHelper webhookHelper;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    webhookEventProcessingService.registerIterators(5);
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(WebhookEventProcessingService.class), any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle() {
    List<Producer> producers = new ArrayList<>();
    WebhookEvent webhookEvent =
        WebhookEvent.builder()
            .headers(Collections.singletonList(
                HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build()))
            .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    when(webhookHelper.getSourceRepoType(webhookEvent)).thenReturn(SourceRepoType.GITHUB);
    when(webhookHelper.invokeScmService(webhookEvent)).thenReturn(parseWebhookResponse);
    when(webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB))
        .thenReturn(WebhookDTO.newBuilder().build());
    producers.add(gitPushEventProducer);
    when(webhookHelper.getProducerListForEvent(webhookDTO)).thenReturn(producers);
    doReturn("")
        .when(gitPushEventProducer)
        .send(Message.newBuilder().setData(WebhookDTO.newBuilder().build().toByteString()).build());
    doNothing().when(webhookEventRepository).delete(any());
    webhookEventProcessingService.handle(webhookEvent);
    verify(webhookHelper, times(1)).getSourceRepoType(webhookEvent);

    doThrow(new InvalidRequestException("message"))
        .when(webhookHelper)
        .generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);
    webhookEventProcessingService.handle(webhookEvent);
    verify(webhookHelper, times(1)).getProducerListForEvent(webhookDTO);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishWebhookEventException() {
    List<Producer> producers = new ArrayList<>();
    WebhookEvent webhookEvent =
        WebhookEvent.builder()
            .headers(Collections.singletonList(
                HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build()))
            .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setEventId("eventId").build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    when(webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB))
        .thenReturn(webhookDTO);
    producers.add(gitPushEventProducer);
    when(webhookHelper.getProducerListForEvent(webhookDTO)).thenReturn(producers);
    doThrow(new EventsFrameworkDownException("message"))
        .when(gitPushEventProducer)
        .send(Message.newBuilder().setData(webhookDTO.toByteString()).build());
    webhookEventProcessingService.publishWebhookEvent(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);
    verify(webhookHelper, times(1)).getProducerListForEvent(webhookDTO);
  }
}
