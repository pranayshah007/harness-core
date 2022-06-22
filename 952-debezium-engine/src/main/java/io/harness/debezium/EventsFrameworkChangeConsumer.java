/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;

import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.source.SourceRecord;

@Slf4j
public class EventsFrameworkChangeConsumer implements MongoCollectionChangeConsumer {
  private static final String OP_FIELD = "__op";
  private static final String DEFAULT_STRING = "default";

  private final String collectionName;
  private final DebeziumProducerFactory producerFactory;

  public EventsFrameworkChangeConsumer(String collectionName, DebeziumProducerFactory producerFactory) {
    this.collectionName = collectionName;
    this.producerFactory = producerFactory;
  }

  @Override
  public void handleBatch(List<ChangeEvent<String, String>> records,
      DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) throws InterruptedException {
    log.info("Handling a batch of {} records for collection {}", records.size(), collectionName);

    // Add the batch records to the stream(s)
    for (ChangeEvent<String, String> record : records) {
      Optional<OpType> opType = getOperationType(((EmbeddedEngineChangeEvent<String, String>) record).sourceRecord());

      DebeziumChangeEvent debeziumChangeEvent = DebeziumChangeEvent.newBuilder()
                                                    .setKey(getKeyOrDefault(record))
                                                    .setValue(getValueOrDefault(record))
                                                    .setOptype(opType.get().toString())
                                                    .build();

      Producer producer = producerFactory.get(record.destination());
      producer.send(Message.newBuilder().setData(debeziumChangeEvent.toByteString()).build());
      try {
        recordCommitter.markProcessed(record);
      } catch (InterruptedException e) {
        log.error("Exception Occurred while marking record as committed", e);
      }
    }
    recordCommitter.markBatchFinished();
  }

  private Optional<OpType> getOperationType(SourceRecord sourceRecord) {
    return Optional.ofNullable(sourceRecord.headers().lastWithName(OP_FIELD))
        .flatMap(x -> OpType.fromString((String) x.value()));
  }

  private String getKeyOrDefault(ChangeEvent<String, String> record) {
    return (record.key() != null) ? (record.key()) : DEFAULT_STRING;
  }

  private String getValueOrDefault(ChangeEvent<String, String> record) {
    return (record.value() != null) ? (record.value()) : DEFAULT_STRING;
  }

  @Override
  public String getCollection() {
    return collectionName;
  }
}
