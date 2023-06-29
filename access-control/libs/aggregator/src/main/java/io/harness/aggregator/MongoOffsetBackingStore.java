/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import io.harness.aggregator.models.MongoReconciliationOffset;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.MemoryOffsetBackingStore;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PL)
@NoArgsConstructor
@Slf4j
public class MongoOffsetBackingStore extends MemoryOffsetBackingStore {
  private MongoTemplate mongoTemplate;
  private MongoClient mongoClient;
  private String collectionName;
  private static final String CREDENTIALS_SEPARATOR = ":";
  private static final String CREDENTIALS_HOST_SEPARATOR = "@";

  @Override
  public void configure(WorkerConfig workerConfig) {
    super.configure(workerConfig);
    collectionName = workerConfig.getString("offset.storage.topic");

    StringBuilder connectionUri = new StringBuilder();
    String mongodbConnectionProtocol = workerConfig.originals().get("mongodbConnectionProtocol").toString();
    String mongodbConnectionUrl = workerConfig.originals().get("mongodbConnectionUrl").toString();
    connectionUri.append(mongodbConnectionProtocol);
    String userName = "";
    String password = "";
    try {
      userName = workerConfig.originals().get("mongodb.user").toString();
      password = workerConfig.originals().get("mongodb.password").toString();
      connectionUri.append(userName).append(CREDENTIALS_SEPARATOR).append(password).append(CREDENTIALS_HOST_SEPARATOR);
    } catch (Exception ex) {
      // Ignore as in local user and password is not present.
    }
    connectionUri.append(mongodbConnectionUrl);
    MongoClientURI uri = new MongoClientURI(connectionUri.toString());

    MongoClientSettings mongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionUri.toString()))
            .retryWrites(true)
            .applyToSocketSettings(builder -> builder.connectTimeout(30000, TimeUnit.MILLISECONDS))
            .applyToClusterSettings(builder -> builder.serverSelectionTimeout(90000, TimeUnit.MILLISECONDS))
            .applyToSocketSettings(builder -> builder.readTimeout(360000, TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder -> builder.maxConnectionIdleTime(600000, TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder -> builder.maxSize(300))
            .readPreference(ReadPreference.primary())
            .build();
    mongoClient = MongoClients.create(mongoClientSettings);

    mongoTemplate = new MongoTemplate(mongoClient, Objects.requireNonNull(uri.getDatabase()));
  }

  @Override
  public void start() {
    super.start();
    log.info("Starting Mongo offset backing store...");
    load();
  }

  private void load() {
    this.data = new HashMap<>();
    MongoReconciliationOffset mongoReconciliationOffset =
        mongoTemplate.findOne(new Query().with(Sort.by(Sort.Order.desc(MongoReconciliationOffset.keys.createdAt))),
            MongoReconciliationOffset.class, collectionName);
    if (mongoReconciliationOffset != null) {
      this.data.put(
          ByteBuffer.wrap(mongoReconciliationOffset.getKey()), ByteBuffer.wrap(mongoReconciliationOffset.getValue()));
    } else {
      log.info("No offset found in the database, will start a full sync.");
    }
  }

  @Override
  public void stop() {
    log.info("Stopped Mongo offset backing store...");
    try {
      super.stop();
    } catch (Exception e) {
      log.error("Failed to stop mongo offset backing store", e);
    } finally {
      try {
        mongoClient.close();
      } catch (Exception e) {
        log.error("Failed to close the mongo client for Aggregator offset backing store.");
      }
    }
  }

  @Override
  protected void save() {
    for (Map.Entry<ByteBuffer, ByteBuffer> mapEntry : data.entrySet()) {
      byte[] key = (mapEntry.getKey() != null) ? mapEntry.getKey().array() : null;
      byte[] value = (mapEntry.getValue() != null) ? mapEntry.getValue().array() : null;
      MongoReconciliationOffset mongoReconciliationOffset = mongoTemplate.save(
          MongoReconciliationOffset.builder().key(key).value(value).createdAt(System.currentTimeMillis()).build(),
          collectionName);
      log.info("Saved offset in db is: {}", mongoReconciliationOffset);
    }
  }
}
