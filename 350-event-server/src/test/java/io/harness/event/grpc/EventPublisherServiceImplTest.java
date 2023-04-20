/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.grpc;

import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_START;
import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_STOP;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.SAHILDEEP;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.PublishMessage;
import io.harness.event.PublishRequest;
import io.harness.event.app.EventServiceConfig;
import io.harness.event.config.EventDataBatchQueryConfig;
import io.harness.event.payloads.Lifecycle;
import io.harness.event.service.impl.EventPublisherServiceImpl;
import io.harness.event.service.intfc.EventDataBulkWriteService;
import io.harness.event.service.intfc.LastReceivedPublishedMessageRepository;
import io.harness.grpc.auth.DelegateAuthServerInterceptor;
import io.harness.grpc.utils.HTimestamps;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.grpc.Context;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventPublisherServiceImplTest extends CategoryTest {
  private static final String TEST_ACC_ID = UUID.randomUUID().toString();
  private static final String TEST_DEL_ID = UUID.randomUUID().toString();

  @Mock private EventDataBulkWriteService eventDataBulkWriteService;
  @Mock private EventDataBatchQueryConfig eventDataBatchQueryConfig;
  @Mock private EventServiceConfig eventServiceConfig;
  @Mock private HPersistence hPersistence;
  @Mock private LastReceivedPublishedMessageRepository lastReceivedPublishedMessageRepository;
  @Mock private MessageProcessorRegistry messageProcessorRegistry;
  @Mock private MetricService metricService;

  @InjectMocks private EventPublisherServiceImpl publisherService;
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFailIfAccountIdIsNotSet() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> publisherService.publish(null, TEST_DEL_ID, Collections.emptyList(), 0));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPersistMessages() {
    Instant occurredAt = Instant.now().minus(20, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    when(eventServiceConfig.getEventDataBatchQueryConfig()).thenReturn(eventDataBatchQueryConfig);
    when(eventServiceConfig.getEventDataBatchQueryConfig().isEnableBatchWrite()).thenReturn(false);
    Context.current().withValue(DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY, TEST_ACC_ID).run(() -> {
      @SuppressWarnings("unchecked") // Casting as we can't use List<PublishedMessage> as the class type.
      ArgumentCaptor<List<PublishedMessage>> captor = ArgumentCaptor.forClass(List.class);
      PublishRequest publishRequest =
          PublishRequest.newBuilder()
              .addAllMessages(streamWithIndex(testMessages().stream())
                                  .map(pair
                                      -> PublishMessage.newBuilder()
                                             .setMessageId("id-" + pair.getLeft())
                                             .putAttributes("key1", "val1")
                                             .putAttributes("key2", pair.getRight().toString())
                                             .setCategory("")
                                             .setPayload(Any.pack(pair.getRight()))
                                             .setOccurredAt(HTimestamps.fromInstant(occurredAt))
                                             .build())
                                  .collect(toList()))
              .build();
      publisherService.publish(
          TEST_ACC_ID, TEST_DEL_ID, publishRequest.getMessagesList(), publishRequest.getMessagesCount());
      verify(hPersistence).saveIgnoringDuplicateKeys(captor.capture());
      List<PublishedMessage> captured = captor.getValue();
      updateToDefaultUUIDs(captured);
      assertThat(captured).containsExactlyElementsOf(
          streamWithIndex(testMessages().stream())
              .map(pair
                  -> PublishedMessage.builder()
                         .uuid("id-" + pair.getLeft())
                         .type(Lifecycle.class.getName())
                         .data(Any.pack(pair.getRight()).toByteArray())
                         .accountId(TEST_ACC_ID)
                         .attributes(ImmutableMap.of("key1", "val1", "key2", pair.getRight().toString()))
                         .category("")
                         .occurredAt(occurredAt.toEpochMilli())
                         .build())
              .collect(toList()));
    });
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void shouldPersistBulkMessages() {
    Instant occurredAt = Instant.now().minus(20, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    when(eventServiceConfig.getEventDataBatchQueryConfig()).thenReturn(eventDataBatchQueryConfig);
    when(eventServiceConfig.getEventDataBatchQueryConfig().isEnableBatchWrite()).thenReturn(true);
    Context.current().withValue(DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY, TEST_ACC_ID).run(() -> {
      @SuppressWarnings("unchecked") // Casting as we can't use List<PublishedMessage> as the class type.
      ArgumentCaptor<List<PublishedMessage>> captor = ArgumentCaptor.forClass(List.class);
      PublishRequest publishRequest =
          PublishRequest.newBuilder()
              .addAllMessages(streamWithIndex(testMessages().stream())
                                  .map(pair
                                      -> PublishMessage.newBuilder()
                                             .setMessageId("id-" + pair.getLeft())
                                             .putAttributes("key1", "val1")
                                             .putAttributes("key2", pair.getRight().toString())
                                             .setCategory("")
                                             .setPayload(Any.pack(pair.getRight()))
                                             .setOccurredAt(HTimestamps.fromInstant(occurredAt))
                                             .build())
                                  .collect(toList()))
              .build();
      publisherService.publish(
          TEST_ACC_ID, TEST_DEL_ID, publishRequest.getMessagesList(), publishRequest.getMessagesCount());
      verify(eventDataBulkWriteService).bulkInsertPublishedMessages(captor.capture());
      List<PublishedMessage> captured = captor.getValue();
      updateToDefaultUUIDs(captured);
      assertThat(captured).containsExactlyElementsOf(
          streamWithIndex(testMessages().stream())
              .map(pair
                  -> PublishedMessage.builder()
                         .uuid("id-" + pair.getLeft())
                         .type(Lifecycle.class.getName())
                         .data(Any.pack(pair.getRight()).toByteArray())
                         .accountId(TEST_ACC_ID)
                         .attributes(ImmutableMap.of("key1", "val1", "key2", pair.getRight().toString()))
                         .category("")
                         .occurredAt(occurredAt.toEpochMilli())
                         .build())
              .collect(toList()));
    });
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldRespondErrorWhenPersistFail() {
    RuntimeException exception = new RuntimeException("Persistence error");
    when(eventServiceConfig.getEventDataBatchQueryConfig()).thenReturn(eventDataBatchQueryConfig);
    when(eventServiceConfig.getEventDataBatchQueryConfig().isEnableBatchWrite()).thenReturn(false);
    doThrow(exception).when(hPersistence).saveIgnoringDuplicateKeys(anyList());
    PublishRequest publishRequest =
        PublishRequest.newBuilder()
            .addAllMessages(testMessages()
                                .stream()
                                .map(x -> PublishMessage.newBuilder().setPayload(Any.pack(x)).build())
                                .collect(toList()))
            .build();
    publisherService.publish(
        TEST_ACC_ID, TEST_DEL_ID, publishRequest.getMessagesList(), publishRequest.getMessagesCount());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void shouldRespondErrorWhenPersistBulkFail() {
    RuntimeException exception = new RuntimeException("Persistence error");
    when(eventServiceConfig.getEventDataBatchQueryConfig()).thenReturn(eventDataBatchQueryConfig);
    when(eventServiceConfig.getEventDataBatchQueryConfig().isEnableBatchWrite()).thenReturn(true);
    doThrow(exception).when(eventDataBulkWriteService).bulkInsertPublishedMessages(anyList());
    PublishRequest publishRequest =
        PublishRequest.newBuilder()
            .addAllMessages(testMessages()
                                .stream()
                                .map(x -> PublishMessage.newBuilder().setPayload(Any.pack(x)).build())
                                .collect(toList()))
            .build();
    publisherService.publish(
        TEST_ACC_ID, TEST_DEL_ID, publishRequest.getMessagesList(), publishRequest.getMessagesCount());
  }

  private List<Message> testMessages() {
    return Arrays.asList(Lifecycle.newBuilder().setType(EVENT_TYPE_START).setInstanceId("instance-1").build(),
        Lifecycle.newBuilder().setType(EVENT_TYPE_START).setInstanceId("instance-2").build(),
        Lifecycle.newBuilder().setType(EVENT_TYPE_STOP).setInstanceId("instance-2").build(),
        Lifecycle.newBuilder().setType(EVENT_TYPE_STOP).setInstanceId("instance-1").build());
  }

  private <T> Stream<ImmutablePair<Integer, T>> streamWithIndex(Stream<T> stream) {
    return Streams.zip(IntStream.iterate(0, i -> i + 1).boxed(), stream, ImmutablePair::of);
  }

  private void updateToDefaultUUIDs(List<PublishedMessage> publishedMessages) {
    for (int i = 0; i < publishedMessages.size(); i++) {
      publishedMessages.get(i).setUuid(String.format("id-%d", i));
    }
  }
}
