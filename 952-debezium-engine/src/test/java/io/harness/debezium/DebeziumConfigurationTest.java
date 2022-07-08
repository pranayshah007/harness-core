/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import java.util.Optional;
import java.util.Properties;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)

public class DebeziumConfigurationTest extends CategoryTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetDebeziumProperties() {
    DebeziumConfig debeziumConfig =
        new DebeziumConfig(false, "testConnector", "offset_file", "offsets", "false", "false", "6000", "1000", "10000",
            "3", "MongoDbConnectorClass", "rs0/host1", "shop", "", "", "false", "products", "", "2000");
    RedisConfig redisConfig = new RedisConfig();
    Properties expectedProps = new Properties();
    expectedProps.setProperty(DebeziumConfiguration.CONNECTOR_NAME, debeziumConfig.getConnectorName());
    expectedProps.setProperty(DebeziumConfiguration.OFFSET_STORAGE, RedisOffsetBackingStore.class.getName());
    expectedProps.setProperty(DebeziumConfiguration.OFFSET_STORAGE_FILE_FILENAME, JsonUtils.asJson(redisConfig));
    expectedProps.setProperty(
        DebeziumConfiguration.KEY_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getKeyConverterSchemasEnable());
    expectedProps.setProperty(
        DebeziumConfiguration.VALUE_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getValueConverterSchemasEnable());
    expectedProps.setProperty(
        DebeziumConfiguration.OFFSET_FLUSH_INTERVAL_MS, debeziumConfig.getOffsetFlushIntervalMillis());

    /* begin connector properties */
    expectedProps.setProperty(DebeziumConfiguration.CONNECTOR_CLASS, DebeziumConfiguration.MONGO_DB_CONNECTOR);
    expectedProps.setProperty(DebeziumConfiguration.MONGODB_HOSTS, debeziumConfig.getMongodbHosts());
    expectedProps.setProperty(DebeziumConfiguration.MONGODB_NAME, debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getMongodbUser())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> expectedProps.setProperty(DebeziumConfiguration.MONGODB_USER, x));
    Optional.ofNullable(debeziumConfig.getMongodbPassword())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> expectedProps.setProperty(DebeziumConfiguration.MONGODB_PASSWORD, x));
    expectedProps.setProperty(DebeziumConfiguration.MONGODB_SSL_ENABLED, debeziumConfig.getSslEnabled());
    expectedProps.setProperty(DebeziumConfiguration.DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    expectedProps.setProperty(DebeziumConfiguration.TRANSFORMS, "unwrap");
    expectedProps.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_TYPE,
        DebeziumConfiguration.DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    expectedProps.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    expectedProps.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    expectedProps.setProperty(DebeziumConfiguration.CONNECT_MAX_ATTEMPTS, debeziumConfig.getConnectMaxAttempts());
    expectedProps.setProperty(
        DebeziumConfiguration.CONNECT_BACKOFF_MAX_DELAY_MS, debeziumConfig.getConnectBackoffMaxDelayMillis());
    expectedProps.setProperty(
        DebeziumConfiguration.CONNECT_BACKOFF_INITIAL_DELAY_MS, debeziumConfig.getConnectBackoffInitialDelayMillis());
    expectedProps.setProperty(DebeziumConfiguration.SNAPSHOT_FETCH_SIZE, debeziumConfig.getSnapshotFetchSize());
    assertEquals(expectedProps, DebeziumConfiguration.getDebeziumProperties(debeziumConfig, redisConfig));
    expectedProps.setProperty(DebeziumConfiguration.OFFSET_STORAGE_KEY,
        DebeziumConstants.DEBEZIUM_OFFSET_PREFIX + debeziumConfig.getConnectorName() + "-"
            + "coll1");
    expectedProps.setProperty(DebeziumConfiguration.COLLECTION_INCLUDE_LIST, "coll1");
    assertEquals(expectedProps, DebeziumConfiguration.getDebeziumProperties(debeziumConfig, redisConfig, "coll1"));
  }
}