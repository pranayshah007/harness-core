/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.changestreams;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.persistence.PersistentEntity;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.mongodb.ClientSessionOptions;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TagSet;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;

/**
 * Mongo Change Stream Manager, provides the functionality
 * to open change streams on multiple collections, pass a handler
 * for individual collections and close the change streams.
 *
 * @author Utkarsh
 */

@OwnedBy(PL)
@Slf4j
public class ChangeTracker {
  private MongoConfig mongoConfig;
  private ChangeEventFactory changeEventFactory;
  private TagSet mongoTagSet;
  private ExecutorService executorService;
  private Set<ChangeTrackingTask> changeTrackingTasks;
  private Set<Future<?>> changeTrackingTasksFuture;
  private MongoDatabase mongoDatabase;
  private MongoClient mongoClient;
  private ReadPreference readPreference;
  private ClientSession clientSession;

  @Inject
  public ChangeTracker(MongoConfig mongoConfig, ChangeEventFactory changeEventFactory, TagSet mongoTagSet) {
    this.mongoConfig = mongoConfig;
    this.changeEventFactory = changeEventFactory;
    this.mongoTagSet = mongoTagSet;
  }

  private String getCollectionName(Class<? extends PersistentEntity> clazz) {
    return clazz.getAnnotation(Entity.class).value();
  }

  private MongoClientURI mongoClientUri() {
    final String mongoClientUrl = mongoConfig.getUri();
    if (Objects.isNull(mongoTagSet)) {
      readPreference = ReadPreference.secondaryPreferred();
    } else {
      readPreference = ReadPreference.secondary(mongoTagSet);
    }
    return new MongoClientURI(mongoClientUrl,
        MongoClientOptions.builder(MongoModule.getDefaultMongoClientOptions(mongoConfig))
            .readPreference(readPreference));
  }

  private void connectToMongoDatabase() {
    MongoClientURI uri = mongoClientUri();
    mongoClient = new MongoClient(uri);
    if (readPreference.getClass() == ReadPreference.secondaryPreferred().getClass()) {
      clientSession = null;
    } else {
      clientSession = mongoClient.startSession(ClientSessionOptions.builder().build());
    }
    final String databaseName = uri.getDatabase();
    log.info("ChangeStream: Database is {}", databaseName);
    mongoDatabase =
        mongoClient.getDatabase(databaseName).withReadConcern(ReadConcern.MAJORITY).withReadPreference(readPreference);
  }

  private void createChangeStreamTasks(Set<ChangeTrackingInfo<?>> changeTrackingInfos, CountDownLatch latch) {
    changeTrackingTasks = new HashSet<>();
    for (ChangeTrackingInfo<?> changeTrackingInfo : changeTrackingInfos) {
      MongoCollection<DBObject> collection =
          mongoDatabase.getCollection(getCollectionName(changeTrackingInfo.getMorphiaClass()))
              .withDocumentClass(DBObject.class)
              .withReadPreference(readPreference);
      log.info("Connection details for mongo collection {}", collection.getReadPreference());

      ChangeStreamSubscriber changeStreamSubscriber = getChangeStreamSubscriber(changeTrackingInfo);
      ChangeTrackingTask changeTrackingTask = new ChangeTrackingTask(changeStreamSubscriber, collection, clientSession,
          latch, changeTrackingInfo.getResumeToken(), changeTrackingInfo.getPipeline());
      changeTrackingTasks.add(changeTrackingTask);
    }
  }

  private void openChangeStreams(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    executorService =
        Executors.newFixedThreadPool(8, new ThreadFactoryBuilder().setNameFormat("change-tracker-%d").build());
    CountDownLatch latch = new CountDownLatch(changeTrackingInfos.size());
    createChangeStreamTasks(changeTrackingInfos, latch);
    changeTrackingTasksFuture = new HashSet<>();

    if (!executorService.isShutdown()) {
      for (ChangeTrackingTask changeTrackingTask : changeTrackingTasks) {
        Future f = executorService.submit(changeTrackingTask);
        changeTrackingTasksFuture.add(f);
      }
    }
  }

  private boolean shouldProcessChange(ChangeStreamDocument<DBObject> changeStreamDocument) {
    return changeStreamDocument.getFullDocument() != null
        || changeStreamDocument.getOperationType() == OperationType.DELETE;
  }

  private <T extends PersistentEntity> ChangeStreamSubscriber getChangeStreamSubscriber(
      ChangeTrackingInfo<T> changeTrackingInfo) {
    return changeStreamDocument -> {
      if (shouldProcessChange(changeStreamDocument)) {
        ChangeEvent<T> changeEvent =
            changeEventFactory.fromChangeStreamDocument(changeStreamDocument, changeTrackingInfo.getMorphiaClass());
        changeTrackingInfo.getChangeSubscriber().onChange(changeEvent);
      }
    };
  }

  public void start(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    connectToMongoDatabase();
    openChangeStreams(changeTrackingInfos);
  }

  public boolean checkIfAnyChangeTrackerIsAlive() {
    for (Future<?> f : changeTrackingTasksFuture) {
      if (!f.isDone()) {
        return true;
      }
    }
    return false;
  }

  public void stop() {
    log.info("Trying to close changeTrackingTasks");
    for (Future<?> f : changeTrackingTasksFuture) {
      f.cancel(true);
    }
    if (executorService != null) {
      executorService.shutdownNow();
    }
    if (mongoClient != null) {
      mongoClient.close();
    }
    if (clientSession != null) {
      clientSession.close();
    }
  }
}
