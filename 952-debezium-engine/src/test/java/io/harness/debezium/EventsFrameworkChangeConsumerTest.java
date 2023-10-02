/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.client.api.CfClient;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.rule.Owner;

import com.google.protobuf.InvalidProtocolBufferException;
import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.header.ConnectHeaders;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class EventsFrameworkChangeConsumerTest extends CategoryTest {
  private static final String DEFAULT_STRING = "default";
  @Mock Producer producer;
  private static final String collection = "coll";
  @Mock private static DebeziumProducerFactory producerFactory;
  @Mock private CfClient cfClient;
  ConsumerMode mode = ConsumerMode.SNAPSHOT;
  private static final EventsFrameworkChangeConsumerStreaming EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING =
      new EventsFrameworkChangeConsumerStreaming(ChangeConsumerConfig.builder()
                                                     .redisStreamSize(10)
                                                     .consumerType(ConsumerType.EVENTS_FRAMEWORK)
                                                     .eventsFrameworkConfiguration(null)
                                                     .build(),
          null, "coll", null);
  private static final String key = "key";
  private static final String value = "value";
  ChangeEvent<String, String> testRecord = new EmbeddedEngineChangeEvent<>(key, value, null, null);
  ChangeEvent<String, String> emptyRecord = new EmbeddedEngineChangeEvent<>(null, null, null, null);
  @Mock DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetValueOrDefault() {
    assertEquals(DEFAULT_STRING, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getValueOrDefault(emptyRecord));
    assertEquals(value, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getValueOrDefault(testRecord));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetKeyOrDefault() {
    assertEquals(DEFAULT_STRING, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getKeyOrDefault(emptyRecord));
    assertEquals(key, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getKeyOrDefault(testRecord));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetOperationType() {
    ConnectHeaders headers = new ConnectHeaders();
    headers.add("__op", "c", Schema.STRING_SCHEMA);
    Optional<OpType> opType = EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getOperationType(new SourceRecord(
        new HashMap<>(), new HashMap<>(), "", 0, Schema.BOOLEAN_SCHEMA, "", Schema.BOOLEAN_SCHEMA, "", 0L, headers));
    assertEquals(opType, Optional.of(OpType.CREATE));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetCollection() {
    assertEquals(collection, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getCollection());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleBatch() throws InterruptedException, InvalidProtocolBufferException {
    EventsFrameworkChangeConsumerStreaming eventsFrameworkChangeConsumerStreaming =
        new EventsFrameworkChangeConsumerStreaming(ChangeConsumerConfig.builder()
                                                       .redisStreamSize(10)
                                                       .consumerType(ConsumerType.EVENTS_FRAMEWORK)
                                                       .eventsFrameworkConfiguration(null)
                                                       .consumerMode(mode)
                                                       .build(),
            cfClient, "coll.mode", producerFactory);
    List<ChangeEvent<String, String>> records = new ArrayList<>();
    ConnectHeaders headers = new ConnectHeaders();
    headers.add("__op", "c", Schema.STRING_SCHEMA);
    ChangeEvent<String, String> testRecord = new EmbeddedEngineChangeEvent<>(key, value, null,
        new SourceRecord(new HashMap<>(), new HashMap<>(), "topic", 0, Schema.BOOLEAN_SCHEMA, "", Schema.BOOLEAN_SCHEMA,
            "", 0L, headers));
    records.add(testRecord);
    doReturn(producer).when(producerFactory).get("topic", 10, ConsumerMode.SNAPSHOT, null);
    doNothing().when(recordCommitter).markBatchFinished();
    doNothing().when(recordCommitter).markProcessed(testRecord);
    doReturn(true).when(cfClient).boolVariation(anyString(), any(), anyBoolean());
    eventsFrameworkChangeConsumerStreaming.handleBatch(records, recordCommitter);
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(captor.capture());
    DebeziumChangeEvent debeziumChangeEvent =
        Objects.requireNonNull(DebeziumChangeEvent.parseFrom(captor.getValue().getData()));
    assertEquals(debeziumChangeEvent.getKey(), key);
    assertEquals(debeziumChangeEvent.getValue(), value);
    assertEquals(debeziumChangeEvent.getOptype(), Optional.of(OpType.CREATE).get().toString());
    verify(recordCommitter, times(1)).markProcessed(testRecord);
    verify(recordCommitter, times(1)).markBatchFinished();
  }
}
