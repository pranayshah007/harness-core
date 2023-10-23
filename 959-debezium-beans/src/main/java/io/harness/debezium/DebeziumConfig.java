/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.EventsFrameworkConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DebeziumConfig {
  @JsonProperty("isEnabled") boolean isEnabled;
  /**
   * Unique name for the ConnectorDisconnectHandler. Attempting to register again with the same name will fail.
   * (This property is required by all Kafka Connect connectors.)
   */
  @JsonProperty("name") String connectorName;
  /**
   * path where the offset will be stored, this contains a a mongodb uri string in our case
   */
  @JsonProperty("offset.storage.file.filename") private String offsetStorageFileName;
  /**
   * Redis Key to store offsets
   */
  @JsonProperty("offset.storage.topic") private String offsetStorageTopic;
  /**
   * A unique name that identifies the ConnectorDisconnectHandler and/or MongoDB replica set or sharded cluster that this ConnectorDisconnectHandler
   * monitors. Each server should be monitored by at most one Debezium ConnectorDisconnectHandler, since this server name prefixes
   * all persisted Kafka topics emanating from the MongoDB replica set or cluster. Only alphanumeric characters
   * and underscores should be used.
   */
  @JsonProperty("mongodb.name") String mongodbName;
  /** Connector will use SSL to connect to MongoDB instances. */
  @JsonProperty("mongodb.ssl.enabled") String sslEnabled;
  /**
   * An optional comma-separated list of regular expressions that match database names to be monitored; any database
   * name not included in database.include.list is excluded from monitoring. By default all databases are monitored.
   * Must not be used with database.exclude.list.
   */
  @JsonProperty("database.include.list") private String databaseIncludeList;
  /**
   * An optional comma-separated list of regular expressions that match fully-qualified namespaces for MongoDB
   * collections to be monitored; any collection not included in collection.include.list is excluded from monitoring.
   * Each identifier is of the form databaseName.collectionName. By default the ConnectorDisconnectHandler will monitor all collections
   * except those in the local and admin databases. Must not be used with collection.exclude.list.
   */
  @JsonProperty("collection.include.list") private String collectionIncludeList;
  /**
   * Specifies maximum topic size for redis stream
   */
  @JsonProperty("redisStreamSize") private int redisStreamSize;
  /**
   * Specifies the criteria for running a snapshot upon startup of the ConnectorDisconnectHandler. The value can be one of these: ("all"
   * - both snapshot and streaming, "initial" - only snapshot, "never" - only streaming)
   */
  @JsonProperty("snapshot.mode") private String snapshotMode;
  /**
   * Positive integer value that specifies the maximum number of records that the blocking queue can hold. When Debezium
   * reads events streamed from the database, it places the events in the blocking queue before it writes them to Kafka.
   * The blocking queue can provide backpressure for reading change events from the database in cases where the
   * ConnectorDisconnectHandler ingests messages faster than it can write them to Kafka, or when Kafka becomes unavailable. Events that
   * are held in the queue are disregarded when the ConnectorDisconnectHandler periodically records offsets.
   */
  @JsonProperty("max.queue.size") private String maxQueueSize;
  /**
   * Positive integer value that specifies the maximum size of each batch of events that should be processed during
   * each iteration of this ConnectorDisconnectHandler.
   */
  @JsonProperty("max.batch.size") private String maxBatchSize;
  /**
   * A long integer value that specifies the maximum volume of the blocking queue in bytes. By default, volume limits
   * are not specified for the blocking queue. To specify the number of bytes that the queue can consume, set this
   * property to a positive long value. If max.queue.size is also set, writing to the queue is blocked when the size of
   * the queue reaches the limit specified by either property.
   */
  @JsonProperty("max.queue.size.in.bytes") private long maxQueueSizeInBytes;
  /**
   * Positive integer value that specifies the number of milliseconds the ConnectorDisconnectHandler should wait during each iteration
   * for new change events to appear.
   */
  @JsonProperty("poll.interval.ms") private String pollIntervalMs;
  /**
   * Controls how frequently heartbeat messages are sent. This property contains an interval in milliseconds that
   * defines how frequently the ConnectorDisconnectHandler sends messages into a heartbeat topic. This can be used to monitor whether the
   * ConnectorDisconnectHandler is still receiving change events from the database.
   */
  @JsonProperty("heartbeat.interval.ms") private String heartbeatIntervalMs;
  /**
   * A comma-separated list of the fully-qualified names of fields that should be excluded from change event message
   * values.
   */
  @JsonProperty("field.exclude.list") private String fieldExcludeList;
  @JsonProperty("mongodb.connection.string") private String mongodbConnectionString;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  public List<String> getMonitoredCollections() {
    if (EmptyPredicate.isEmpty(collectionIncludeList)) {
      return new ArrayList<>();
    }
    return Arrays.asList(collectionIncludeList.split(","));
  }
}
