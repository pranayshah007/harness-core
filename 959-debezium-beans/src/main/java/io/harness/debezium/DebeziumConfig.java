/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.data.structure.EmptyPredicate;

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
   * Unique name for the connector. Attempting to register again with the same name will fail.
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
   * whether to include schema for keys as a part of the event
   */
  @JsonProperty("key.converter.schemas.enable") String keyConverterSchemasEnable;
  /**
   * whether to include schema for values as a part of the event
   */
  @JsonProperty("value.converter.schemas.enable") String valueConverterSchemasEnable;
  /**
   * Interval at which offset will be flushed to offset store
   */
  @JsonProperty("offset.flush.interval.ms") String offsetFlushIntervalMillis;
  /**
   * Positive integer value that specifies the initial delay when trying to reconnect to a primary after the first
   * failed connection attempt or when no primary is available.
   */
  @JsonProperty("connect.backoff.initial.delay.ms") private String connectBackoffInitialDelayMillis;

  /**
   * Positive integer value that specifies the maximum delay when trying to reconnect to a primary after repeated failed
   * connection attempts or when no primary is available.
   */
  @JsonProperty("connect.backoff.max.delay.ms") private String connectBackoffMaxDelayMillis;
  @JsonProperty("sleepInterval") private long sleepInterval;

  /**
   * Positive integer value that specifies the maximum number of failed connection attempts to a replica set primary
   * before an exception occurs and task is aborted.
   */
  @JsonProperty("connect.max.attempts") private String connectMaxAttempts;

  /**
   * The name of the Java class for the connector. Always use a value of io.debezium.connector.mongodb.MongoDbConnector
   * for the MongoDB connector.
   */
  @JsonProperty("connector.class") String connectorClass;
  @JsonProperty("producingCountPerBatch") long producingCountPerBatch;
  /**
   * A unique name that identifies the connector and/or MongoDB replica set or sharded cluster that this connector
   * monitors. Each server should be monitored by at most one Debezium connector, since this server name prefixes
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
   * Each identifier is of the form databaseName.collectionName. By default the connector will monitor all collections
   * except those in the local and admin databases. Must not be used with collection.exclude.list.
   */
  @JsonProperty("collection.include.list") private String collectionIncludeList;
  /**
   *
   Specifies the maximum number of documents that should be read in one go from each collection while taking a snapshot.
   The connector will read the collection contents in multiple batches of this size. Defaults to 0, which indicates that
   the server chooses an appropriate fetch size.
   */
  @JsonProperty("snapshot.fetch.size") private String snapshotFetchSize;
  /**
   * Specifies maximum topic size for redis stream
   */
  @JsonProperty("redisStreamSize") private int redisStreamSize;
  /**
   * Specifies the criteria for running a snapshot upon startup of the connector. The default is initial, and specifies
   * that the connector reads a snapshot when either no offset is found or if the oplog/change stream no longer contains
   * the previous offset. The never option specifies that the connector should never use snapshots, instead the
   * connector should proceed to tail the log.
   */
  @JsonProperty("snapshot.mode") private String snapshotMode;
  /**
   * To bypass the impedance mismatch in heterogeneous array, it is possible to encode the array in two different ways
   * using array.encoding configuration option. Value document will convert the array into a struct of structs in the
   * similar way as done by BSON serialization. The main struct contains fields named _0, _1, _2 etc. where the name
   * represents the index of the element in the array. Every element is then passed as the value for the given field.
   */
  @JsonProperty("transforms.unwrap.array.encoding") private String transformsUnwrapArrayEncoding;
  /**
   * Positive integer value that specifies the maximum number of records that the blocking queue can hold. When Debezium
   * reads events streamed from the database, it places the events in the blocking queue before it writes them to Kafka.
   * The blocking queue can provide backpressure for reading change events from the database in cases where the
   * connector ingests messages faster than it can write them to Kafka, or when Kafka becomes unavailable. Events that
   * are held in the queue are disregarded when the connector periodically records offsets.
   */
  @JsonProperty("max.queue.size") private String maxQueueSize;
  /**
   * Positive integer value that specifies the maximum size of each batch of events that should be processed during
   * each iteration of this connector.
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
   * Positive integer value that specifies the number of milliseconds the connector should wait during each iteration
   * for new change events to appear.
   */
  @JsonProperty("poll.interval.ms") private String pollIntervalMs;
  /**
   * Controls how frequently heartbeat messages are sent. This property contains an interval in milliseconds that
   * defines how frequently the connector sends messages into a heartbeat topic. This can be used to monitor whether the
   * connector is still receiving change events from the database.
   */
  @JsonProperty("heartbeat.interval.ms") private String heartbeatIntervalMs;
  /**
   * A comma-separated list of the fully-qualified names of fields that should be excluded from change event message
   * values.
   */
  @JsonProperty("field.exclude.list") private String fieldExcludeList;
  @JsonProperty("mongodb.connection.string") private String mongodbConnectionString;
  public List<String> getMonitoredCollections() {
    if (EmptyPredicate.isEmpty(collectionIncludeList)) {
      return new ArrayList<>();
    }
    return Arrays.asList(collectionIncludeList.split(","));
  }
}
