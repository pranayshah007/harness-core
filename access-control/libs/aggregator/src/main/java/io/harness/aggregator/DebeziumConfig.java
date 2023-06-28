/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class DebeziumConfig {
  /**
   * Unique name for the connector. Attempting to register again with the same name will fail.
   * (This property is required by all Kafka Connect connectors.)
   */
  @JsonProperty("name") private String connectorName;
  /**
   * path where the offset will be stored, this contains a a mongodb uri string in our case
   */
  @JsonProperty("offset.storage.file.filename") private String offsetStorageFileName;
  /**
   * whether to include schema for keys as a part of the event
   */
  @JsonProperty("key.converter.schemas.enable") private String keyConverterSchemasEnable;
  /**
   * whether to include schema for values as a part of the event
   */
  @JsonProperty("value.converter.schemas.enable") private String valueConverterSchemasEnable;
  /**
   * Interval at which offset will be flushed to offset store
   */
  @JsonProperty("offset.flush.interval.ms") private String offsetFlushIntervalMillis;

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

  /**
   * Positive integer value that specifies the maximum number of failed connection attempts to a replica set primary
   * before an exception occurs and task is aborted.
   */
  @JsonProperty("connect.max.attempts") private String connectMaxAttempts;

  /**
   * The name of the Java class for the connector. Always use a value of io.debezium.connector.mongodb.MongoDbConnector
   * for the MongoDB connector.
   */
  @JsonProperty("connector.class") private String connectorClass;
  /**
   * The comma-separated list of hostname and port pairs (in the form 'host' or 'host:port') of the MongoDB servers in
   * the replica set. The list can contain a single hostname and port pair. If mongodb.members.auto.discover is set to
   * false, then the host and port pair should be prefixed with the replica set name (e.g., rs0/localhost:27017)
   */
  @JsonProperty("mongodb.hosts") private String mongodbHosts;
  /**
   * A unique name that identifies the connector and/or MongoDB replica set or sharded cluster that this connector
   * monitors. Each server should be monitored by at most one Debezium connector, since this server name prefixes
   * all persisted Kafka topics emanating from the MongoDB replica set or cluster. Only alphanumeric characters
   * and underscores should be used.
   */
  @JsonProperty("mongodb.name") private String mongodbName;
  @JsonProperty("mongodb.user") private String mongodbUser;
  @JsonProperty("mongodb.password") private String mongodbPassword;
  /** Connector will use SSL to connect to MongoDB instances. */
  @JsonProperty("mongodb.ssl.enabled") private String sslEnabled;
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
   *
   * Positive integer value that specifies the maximum size of each batch of events
   * that should be processed during each iteration of this connector.
   */
  @JsonProperty("max.batch.size") private String maxStreamBatchSize;

  /**
   * Specifies the mongodbConnectionUrl used by MongoOffsetBackingStore to connect to Db for storing Debezium offset.
   */
  @JsonProperty("mongodbConnectionUrl") private String mongodbConnectionUrl;

  /**
   * Specifies the mongodbConnectionProtocol used by MongoOffsetBackingStore to connect to Db for storing Debezium
   * offset. The protocol for connectint to Atlas is mongodb+srv: whereas for local Replica set it has to be mongodb:
   * Refer for details https://www.mongodb.com/docs/manual/reference/connection-string
   */
  @JsonProperty("mongodbConnectionProtocol") private String mongodbConnectionProtocol;
}