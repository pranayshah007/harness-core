/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.beans.FeatureName;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;

import com.google.common.annotations.VisibleForTesting;
import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.Header;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.source.SourceRecord;

@Slf4j
public abstract class EventsFrameworkChangeConsumer implements MongoCollectionChangeConsumer {
  private static final String OP_FIELD = "__op";
  private static final String DEFAULT_STRING = "default";

  final String collectionName;
  final DebeziumProducerFactory producerFactory;
  int cnt;
  int redisStreamSize;
  CfClient cfClient;
  EventsFrameworkConfiguration configuration;
  ConsumerMode mode;

  public EventsFrameworkChangeConsumer(ChangeConsumerConfig changeConsumerConfig, CfClient cfClient, String collection,
      DebeziumProducerFactory debeziumProducerFactory) {
    this.mode = changeConsumerConfig.getConsumerMode();
    this.configuration = changeConsumerConfig.getEventsFrameworkConfiguration();
    this.collectionName = collection;
    this.producerFactory = debeziumProducerFactory;
    this.redisStreamSize = changeConsumerConfig.getRedisStreamSize();
    this.cfClient = cfClient;
  }

  @Override
  public void handleBatch(List<ChangeEvent<String, String>> records,
      DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) throws InterruptedException {
    log.info("Handling a batch of {} records for collection {}", records.size(), collectionName);
    Collections.reverse(records);
    Map<String, ChangeEvent<String, String>> recordsMap = new HashMap<>();
    for (ChangeEvent<String, String> record : records) {
      if (!recordsMap.containsKey(record.key())) {
        recordsMap.put(record.key(), record);
      }
    }
    // Add the batch records to the stream(s)
    for (ChangeEvent<String, String> record : recordsMap.values()) {
      cnt++;
      Optional<OpType> opType =
          getOperationType(((EmbeddedEngineChangeEvent<String, String, List<Header>>) record).sourceRecord());
      if (!opType.isEmpty()) {
        DebeziumChangeEvent debeziumChangeEvent = DebeziumChangeEvent.newBuilder()
                                                      .setKey(getKeyOrDefault(record))
                                                      .setValue(getValueOrDefault(record))
                                                      .setOptype(opType.get().toString())
                                                      .setTimestamp(System.currentTimeMillis())
                                                      .build();
        String collection = Arrays.stream(collectionName.split("\\.")).collect(Collectors.toList()).get(1);
        boolean debeziumEnabled = cfClient.boolVariation(FeatureName.DEBEZIUM_ENABLED.toString(),
            Target.builder().identifier(collection + "." + mode).build(), false);
        Producer producer = producerFactory.get(record.destination(), redisStreamSize, mode, configuration);
        if (debeziumEnabled) {
          producer.send(Message.newBuilder().setData(debeziumChangeEvent.toByteString()).build());
        }
      }
      try {
        recordCommitter.markProcessed(record);
      } catch (InterruptedException e) {
        log.error("Exception Occurred while marking record as committed", e);
      }
    }
    recordCommitter.markBatchFinished();
  }

  @VisibleForTesting
  Optional<OpType> getOperationType(SourceRecord sourceRecord) {
    return Optional.ofNullable(sourceRecord.headers().lastWithName(OP_FIELD))
        .flatMap(x -> OpType.fromString((String) x.value()));
  }

  String getKeyOrDefault(ChangeEvent<String, String> record) {
    return (record.key() != null) ? (record.key()) : DEFAULT_STRING;
  }

  String getValueOrDefault(ChangeEvent<String, String> record) {
    return (record.value() != null) ? (record.value()) : DEFAULT_STRING;
  }

  @Override
  public String getCollection() {
    return collectionName;
  }
}
