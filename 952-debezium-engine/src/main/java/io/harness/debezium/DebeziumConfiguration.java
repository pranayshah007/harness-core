/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.redis.RedisConfig;
import io.harness.serializer.JsonUtils;

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
  public static final String MONGODB_HOSTS = "mongodb.hosts";
  public static final String MONGODB_NAME = "mongodb.name";
  public static final String MONGODB_USER = "mongodb.user";
  public static final String MONGODB_PASSWORD = "mongodb.password";
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
  public static final String SNAPSHOT_FETCH_SIZE = "snapshot.fetch.size";

  public static Properties getDebeziumProperties(DebeziumConfig debeziumConfig, RedisConfig redisLockConfig) {
    Properties props = new Properties();
    props.setProperty(CONNECTOR_NAME, debeziumConfig.getConnectorName());
    props.setProperty(OFFSET_STORAGE, RedisOffsetBackingStore.class.getName());
    props.setProperty(OFFSET_STORAGE_FILE_FILENAME, JsonUtils.asJson(redisLockConfig));
    props.setProperty(KEY_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getKeyConverterSchemasEnable());
    props.setProperty(VALUE_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getValueConverterSchemasEnable());
    props.setProperty(OFFSET_FLUSH_INTERVAL_MS, debeziumConfig.getOffsetFlushIntervalMillis());

    /* begin connector properties */
    props.setProperty(CONNECTOR_CLASS, MONGO_DB_CONNECTOR);
    props.setProperty(MONGODB_HOSTS, debeziumConfig.getMongodbHosts());
    props.setProperty(MONGODB_NAME, debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getMongodbUser())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_USER, x));
    Optional.ofNullable(debeziumConfig.getMongodbPassword())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_PASSWORD, x));
    props.setProperty(MONGODB_SSL_ENABLED, debeziumConfig.getSslEnabled());
    props.setProperty(DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    props.setProperty(TRANSFORMS, "unwrap");
    props.setProperty(TRANSFORMS_UNWRAP_TYPE, DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    props.setProperty(TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    props.setProperty(TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    props.setProperty(CONNECT_BACKOFF_INITIAL_DELAY_MS, debeziumConfig.getConnectBackoffInitialDelayMillis());
    props.setProperty(CONNECT_BACKOFF_MAX_DELAY_MS, debeziumConfig.getConnectBackoffMaxDelayMillis());
    props.setProperty(CONNECT_MAX_ATTEMPTS, debeziumConfig.getConnectMaxAttempts());
    props.setProperty(SNAPSHOT_FETCH_SIZE, debeziumConfig.getSnapshotFetchSize());
    return props;
  }

  public static Properties getDebeziumProperties(
      DebeziumConfig debeziumConfig, RedisConfig redisLockConfig, String monitoredCollection) {
    Properties debeziumProperties = getDebeziumProperties(debeziumConfig, redisLockConfig);
    debeziumProperties.setProperty(DebeziumConfiguration.OFFSET_STORAGE_KEY,
        DebeziumConstants.DEBEZIUM_OFFSET_PREFIX + debeziumConfig.getConnectorName() + "-" + monitoredCollection);
    debeziumProperties.setProperty(DebeziumConfiguration.COLLECTION_INCLUDE_LIST, monitoredCollection);
    return debeziumProperties;
  }
}
