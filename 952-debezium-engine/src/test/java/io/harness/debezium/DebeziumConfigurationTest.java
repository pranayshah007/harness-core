/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.category.element.UnitTests;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import java.util.Optional;
import java.util.Properties;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DebeziumConfigurationTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetDebeziumProperties() {
    DebeziumConfig debeziumConfig =
        new DebeziumConfig(false, "testConnector", "offset_file", "offsets", "false", "false", "6000", "1000", "10000",
            "3", "MongoDbConnectorClass", "rs0/host1", "shop", "", "", "false", "products", "", "2000");
    RedisConfig redisConfig = new RedisConfig();
    Properties expected_props = new Properties();
    expected_props.setProperty(DebeziumConfiguration.CONNECTOR_NAME, debeziumConfig.getConnectorName());
    expected_props.setProperty(DebeziumConfiguration.OFFSET_STORAGE, RedisOffsetBackingStore.class.getName());
    expected_props.setProperty(DebeziumConfiguration.OFFSET_STORAGE_FILE_FILENAME, JsonUtils.asJson(redisConfig));
    expected_props.setProperty(
        DebeziumConfiguration.KEY_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getKeyConverterSchemasEnable());
    expected_props.setProperty(
        DebeziumConfiguration.VALUE_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getValueConverterSchemasEnable());
    expected_props.setProperty(
        DebeziumConfiguration.OFFSET_FLUSH_INTERVAL_MS, debeziumConfig.getOffsetFlushIntervalMillis());

    /* begin connector properties */
    expected_props.setProperty(DebeziumConfiguration.CONNECTOR_CLASS, DebeziumConfiguration.MONGO_DB_CONNECTOR);
    expected_props.setProperty(DebeziumConfiguration.MONGODB_HOSTS, debeziumConfig.getMongodbHosts());
    expected_props.setProperty(DebeziumConfiguration.MONGODB_NAME, debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getMongodbUser())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> expected_props.setProperty(DebeziumConfiguration.MONGODB_USER, x));
    Optional.ofNullable(debeziumConfig.getMongodbPassword())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> expected_props.setProperty(DebeziumConfiguration.MONGODB_PASSWORD, x));
    expected_props.setProperty(DebeziumConfiguration.MONGODB_SSL_ENABLED, debeziumConfig.getSslEnabled());
    expected_props.setProperty(DebeziumConfiguration.DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS, "unwrap");
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_TYPE,
        DebeziumConfiguration.DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    expected_props.setProperty(DebeziumConfiguration.CONNECT_MAX_ATTEMPTS, debeziumConfig.getConnectMaxAttempts());
    expected_props.setProperty(
        DebeziumConfiguration.CONNECT_BACKOFF_MAX_DELAY_MS, debeziumConfig.getConnectBackoffMaxDelayMillis());
    expected_props.setProperty(
        DebeziumConfiguration.CONNECT_BACKOFF_INITIAL_DELAY_MS, debeziumConfig.getConnectBackoffInitialDelayMillis());
    expected_props.setProperty(DebeziumConfiguration.SNAPSHOT_FETCH_SIZE, debeziumConfig.getSnapshotFetchSize());
    assertEquals(expected_props, DebeziumConfiguration.getDebeziumProperties(debeziumConfig, redisConfig));
    expected_props.setProperty(DebeziumConfiguration.OFFSET_STORAGE_KEY,
        DebeziumConstants.DEBEZIUM_OFFSET_PREFIX + debeziumConfig.getConnectorName() + "-"
            + "coll1");
    expected_props.setProperty(DebeziumConfiguration.COLLECTION_INCLUDE_LIST, "coll1");
    assertEquals(expected_props, DebeziumConfiguration.getDebeziumProperties(debeziumConfig, redisConfig, "coll1"));
  }
}
