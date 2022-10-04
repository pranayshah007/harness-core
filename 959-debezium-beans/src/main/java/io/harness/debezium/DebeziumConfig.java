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
   * The comma-separated list of hostname and port pairs (in the form 'host' or 'host:port') of the MongoDB servers in
   * the replica set. The list can contain a single hostname and port pair. If mongodb.members.auto.discover is set to
   * false, then the host and port pair should be prefixed with the replica set name (e.g., rs0/localhost:27017)
   */
  @JsonProperty("mongodb.hosts") String mongodbHosts;
  /**
   * A unique name that identifies the connector and/or MongoDB replica set or sharded cluster that this connector
   * monitors. Each server should be monitored by at most one Debezium connector, since this server name prefixes
   * all persisted Kafka topics emanating from the MongoDB replica set or cluster. Only alphanumeric characters
   * and underscores should be used.
   */
  @JsonProperty("mongodb.name") String mongodbName;
  @JsonProperty("mongodb.user") String mongodbUser;
  @JsonProperty("mongodb.password") String mongodbPassword;
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
  public List<String> getMonitoredCollections() {
    if (EmptyPredicate.isEmpty(collectionIncludeList)) {
      return new ArrayList<>();
    }
    return Arrays.asList(collectionIncludeList.split(","));
  }
}
