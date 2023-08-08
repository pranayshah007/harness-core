/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.redis.RedisConfig;
import io.harness.serializer.JsonUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumConfiguration {
  public static final String MONGO_DB_CONNECTOR = "io.debezium.connector.mongodb.MongoDbConnector";
  public static final String CONNECTOR_NAME = "name";
  public static final String OFFSET_STORAGE = "offset.storage";
  public static final String OFFSET_STORAGE_KEY = "offset.storage.topic";
  public static final String OFFSET_STORAGE_FILE_FILENAME = "offset.storage.file.filename";
  public static final String KEY_CONVERTER_SCHEMAS_ENABLE = "key.converter.schemas.enable";
  public static final String VALUE_CONVERTER_SCHEMAS_ENABLE = "value.converter.schemas.enable";
  public static final String OFFSET_FLUSH_INTERVAL_MS = "offset.flush.interval.ms";
  public static final String CONNECTOR_CLASS = "connector.class";
  public static final String MONGODB_NAME = "mongodb.name";
  public static final String MONGODB_SSL_ENABLED = "mongodb.ssl.enabled";
  public static final String DATABASE_INCLUDE_LIST = "database.include.list";
  public static final String COLLECTION_INCLUDE_LIST = "collection.include.list";
  public static final String TRANSFORMS = "transforms";
  public static final String TRANSFORMS_UNWRAP_TYPE = "transforms.unwrap.type";
  public static final String TRANSFORMS_UNWRAP_DROP_TOMBSTONES = "transforms.unwrap.drop.tombstones";
  public static final String TRANSFORMS_UNWRAP_ADD_HEADERS = "transforms.unwrap.add.headers";
  public static final String DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE =
      "io.debezium.connector.mongodb.transforms.ExtractNewDocumentState";
  public static final String CONNECT_BACKOFF_INITIAL_DELAY_MS = "connect.backoff.initial.delay.ms";
  public static final String CONNECT_BACKOFF_MAX_DELAY_MS = "connect.backoff.max.delay.ms";
  public static final String CONNECT_MAX_ATTEMPTS = "connect.max.attempts";
  public static final String SNAPSHOT_MODE = "snapshot.mode";
  public static final String TRANSFORMS_UNWRAP_ARRAY_ENCODING = "transforms.unwrap.array.encoding";
  public static final String MAX_QUEUE_SIZE = "max.queue.size";
  public static final String MAX_BATCH_SIZE = "max.batch.size";
  public static final String MAX_QUEUE_SIZE_IN_BYTES = "max.queue.size.in.bytes";
  public static final String POLL_INTERVAL_MS = "poll.interval.ms";
  public static final String FIELD_EXCLUDE_LIST = "field.exclude.list";
  public static final String HEARTBEAT_INTERVAL_MS = "heartbeat.interval.ms";
  public static final String MONGODB_CONNECTION_STRING = "mongodb.connection.string";
  public static final String CURSOR_PIPELINE = "cursor.pipeline";

  public static Properties getDebeziumProperties(DebeziumConfig debeziumConfig, RedisConfig redisLockConfig) {
    Properties props = new Properties();
    if (!debeziumConfig.getSnapshotMode().equals("all")) {
      props.setProperty(SNAPSHOT_MODE, debeziumConfig.getSnapshotMode());
    } else {
      props.setProperty(SNAPSHOT_MODE, "initial");
    }
    props.setProperty(CONNECTOR_NAME, debeziumConfig.getConnectorName());
    props.setProperty(OFFSET_STORAGE, RedisOffsetBackingStore.class.getName());
    props.setProperty(OFFSET_STORAGE_FILE_FILENAME, JsonUtils.asJson(redisLockConfig));
    props.setProperty(KEY_CONVERTER_SCHEMAS_ENABLE, "false");
    props.setProperty(VALUE_CONVERTER_SCHEMAS_ENABLE, "false");
    props.setProperty(OFFSET_FLUSH_INTERVAL_MS, "5000");
    props.setProperty(TRANSFORMS_UNWRAP_ARRAY_ENCODING, "document");
    props.setProperty(MAX_BATCH_SIZE, debeziumConfig.getMaxBatchSize());
    props.setProperty(MAX_QUEUE_SIZE, debeziumConfig.getMaxQueueSize());
    props.setProperty(MAX_QUEUE_SIZE_IN_BYTES, String.valueOf(debeziumConfig.getMaxQueueSizeInBytes()));
    props.setProperty(POLL_INTERVAL_MS, debeziumConfig.getPollIntervalMs());
    props.setProperty(HEARTBEAT_INTERVAL_MS, debeziumConfig.getHeartbeatIntervalMs());
    props.setProperty("topic.prefix", debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getFieldExcludeList())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(FIELD_EXCLUDE_LIST, x));
    Optional.ofNullable(debeziumConfig.getMongodbConnectionString())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_CONNECTION_STRING, x));
    Optional.ofNullable(debeziumConfig.getSslEnabled())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_SSL_ENABLED, x));
    // Set this value to -2, because debezium library has a logic to retry indefinitely if this value is >1, this is
    // useful for us to recover from state when oplog is rotated
    props.setProperty("errors.max.retries", "-2");

    /* begin connector properties */
    props.setProperty(CONNECTOR_CLASS, MONGO_DB_CONNECTOR);
    props.setProperty(MONGODB_NAME, debeziumConfig.getMongodbName());
    props.setProperty(DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    props.setProperty(TRANSFORMS, "unwrap");
    props.setProperty(TRANSFORMS_UNWRAP_TYPE, DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    props.setProperty(TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    props.setProperty(TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    props.setProperty(CONNECT_BACKOFF_INITIAL_DELAY_MS, "1000");
    props.setProperty(CONNECT_BACKOFF_MAX_DELAY_MS, "10000");
    props.setProperty(CONNECT_MAX_ATTEMPTS, "3");
    return props;
  }

  public static Properties getDebeziumProperties(
      DebeziumConfig debeziumConfig, RedisConfig redisLockConfig, String monitoredCollection) {
    Properties debeziumProperties = getDebeziumProperties(debeziumConfig, redisLockConfig);
    debeziumProperties.setProperty(DebeziumConfiguration.OFFSET_STORAGE_KEY,
        DebeziumConstants.DEBEZIUM_OFFSET_PREFIX + debeziumConfig.getConnectorName() + "-" + monitoredCollection);
    debeziumProperties.setProperty(DebeziumConfiguration.COLLECTION_INCLUDE_LIST, monitoredCollection);
    addMongoCursorPipelineConfigs(debeziumProperties, debeziumConfig.getFieldExcludeList());
    return debeziumProperties;
  }

  public static void addMongoCursorPipelineConfigs(Properties properties, String fieldExcludeList) {
    if (!isEmpty(fieldExcludeList)) {
      List<String> excludedFields = Arrays.asList(fieldExcludeList.split(","));
      StringBuilder cursor = new StringBuilder();
      cursor.append(new StringBuilder().append("[{ $project: { "));
      for (int i = 0; i < excludedFields.size(); i++) {
        String excludedFieldName = excludedFields.get(i).split("\\.")[2];
        if (i == excludedFields.size() - 1) {
          cursor.append(new StringBuilder().append("\"fullDocument.").append(excludedFieldName).append("\": 0 "));
        } else {
          cursor =
              cursor.append(new StringBuilder().append("\"fullDocument.").append(excludedFieldName).append("\": 0, "));
        }
      }
      cursor = cursor.append(new StringBuilder().append("} } ]"));
      properties.setProperty(CURSOR_PIPELINE, cursor.toString());
    }
  }
}
