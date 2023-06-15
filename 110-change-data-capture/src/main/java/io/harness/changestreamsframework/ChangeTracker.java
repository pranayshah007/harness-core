/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreamsframework;

import io.harness.CDCStateEntity;
import io.harness.ChangeDataCaptureServiceConfig;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.mongodb.ClientSessionOptions;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import dev.morphia.annotations.Entity;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ChangeTracker {
  @Inject private ChangeDataCaptureServiceConfig mainConfiguration;
  @Inject private ChangeEventFactory changeEventFactory;
  @Inject private MongoConfig mongoConfig;
  @Inject private WingsPersistence wingsPersistence;
  private ExecutorService executorService;
  private Set<Future<ChangeTrackingInfo<?>>> changeTrackingTasksFuture;
  private MongoClient mongoClient;
  private ReadPreference readPreference;
  private ClientSession clientSession;

  public String getCollectionName(Class<? extends PersistentEntity> clazz) {
    return clazz.getAnnotation(Entity.class).value();
  }

  public String getChangeDataCaptureDataStore(Class<? extends PersistentEntity> clazz) {
    return clazz.getAnnotationsByType(ChangeDataCapture.class)[0].dataStore();
  }

  private MongoClientURI mongoClientUri(String dataStore) {
    String mongoClientUrl;
    switch (dataStore) {
      case "events":
        mongoClientUrl = mainConfiguration.getEventsMongo().getUri();
        break;
      case "pms-harness":
        mongoClientUrl = mainConfiguration.getPmsMongo().getUri();
        break;
      case "ng-harness":
        mongoClientUrl = mainConfiguration.getNgMongo().getUri();
        break;
      case "cvng":
        mongoClientUrl = mainConfiguration.getCvngMongo().getUri();
        break;
      default:
        mongoClientUrl = mainConfiguration.getHarnessMongo().getUri();
        break;
    }

    TagSet mongoTagSet = getMongoTagSet();
    if (mongoTagSet == null) {
      readPreference = ReadPreference.secondaryPreferred();
    } else {
      readPreference = ReadPreference.secondary(mongoTagSet);
    }
    return new MongoClientURI(mongoClientUrl,
        MongoClientOptions.builder(MongoModule.getDefaultMongoClientOptions(mongoConfig))
            .serverSelectionTimeout(mainConfiguration.getHarnessMongo().getServerSelectionTimeout())
            .readPreference(readPreference));
  }

  private TagSet getMongoTagSet() {
    if (!mainConfiguration.getMongoTagsConfig().getTagKey().equals("none")) {
      return new TagSet(new Tag(
          mainConfiguration.getMongoTagsConfig().getTagKey(), mainConfiguration.getMongoTagsConfig().getTagValue()));
    }
    return null;
  }

  public MongoDatabase connectToMongoDatabase(String dataStore) {
    MongoClientURI uri = mongoClientUri(dataStore);
    mongoClient = new MongoClient(uri);
    if (readPreference.getClass() == ReadPreference.secondaryPreferred().getClass()) {
      clientSession = null;
    } else {
      clientSession = mongoClient.startSession(ClientSessionOptions.builder().build());
    }
    final String databaseName = uri.getDatabase();
    log.info("Database is {}", databaseName);
    return mongoClient.getDatabase(databaseName)
        .withReadConcern(ReadConcern.MAJORITY)
        .withReadPreference(readPreference);
  }

  private ChangeTrackingTask createChangeStreamTask(ChangeTrackingInfo<?> changeTrackingInfo) {
    MongoDatabase mongoDatabase =
        connectToMongoDatabase(getChangeDataCaptureDataStore(changeTrackingInfo.getMorphiaClass()));

    MongoCollection<DBObject> collection =
        mongoDatabase.getCollection(getCollectionName(changeTrackingInfo.getMorphiaClass()))
            .withDocumentClass(DBObject.class)
            .withReadPreference(readPreference);

    log.info("Connection details for mongo collection {}", collection.getReadPreference());

    ChangeStreamSubscriber changeStreamSubscriber = getChangeStreamSubscriber(changeTrackingInfo);
    String token = getResumeToken(changeTrackingInfo.getMorphiaClass());
    return new ChangeTrackingTask(changeStreamSubscriber, collection, clientSession, token,
        changeTrackingInfo.getMorphiaClass(), mainConfiguration.getChangeStreamBatchSize());
  }

  private void openChangeStreams(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    executorService = Executors.newFixedThreadPool(
        changeTrackingInfos.size(), new ThreadFactoryBuilder().setNameFormat("change-tracker-%d").build());
    changeTrackingTasksFuture = new HashSet<>();

    if (!executorService.isShutdown()) {
      for (ChangeTrackingInfo<?> changeTrackingInfo : changeTrackingInfos) {
        ChangeTrackingTask changeTrackingTask = createChangeStreamTask(changeTrackingInfo);
        Future<ChangeTrackingInfo<?>> f = executorService.submit(changeTrackingTask, changeTrackingInfo);
        changeTrackingTasksFuture.add(f);
      }
    }
  }

  private boolean shouldProcessChange(ChangeStreamDocument<DBObject> changeStreamDocument) {
    return changeStreamDocument.getFullDocument() != null
        || changeStreamDocument.getOperationType() == OperationType.DELETE;
  }

  private <T extends PersistentEntity> boolean hasTrackedFieldBeenChanged(
      ChangeEvent<T> changeEvent, Class<T> subscribedClass) {
    ChangeDataCapture[] annotations = subscribedClass.getAnnotationsByType(ChangeDataCapture.class);
    Set<String> subscribedFields =
        Arrays.stream(annotations).map(ChangeDataCapture::fields).flatMap(Stream::of).collect(Collectors.toSet());

    if (subscribedFields.isEmpty()) {
      return true;
    }

    if (changeEvent.getChangeType() == ChangeType.UPDATE && changeEvent.getChanges() != null) {
      Set<String> changedFields = changeEvent.getChanges().keySet();
      return !Collections.disjoint(changedFields, subscribedFields);
    }

    return true;
  }

  private <T extends PersistentEntity> ChangeStreamSubscriber getChangeStreamSubscriber(
      ChangeTrackingInfo<T> changeTrackingInfo) {
    return changeStreamDocument -> {
      if (shouldProcessChange(changeStreamDocument)) {
        ChangeEvent<T> changeEvent =
            changeEventFactory.fromChangeStreamDocument(changeStreamDocument, changeTrackingInfo.getMorphiaClass());
        if (hasTrackedFieldBeenChanged(changeEvent, changeTrackingInfo.getMorphiaClass())) {
          changeTrackingInfo.getChangeSubscriber().onChange(changeEvent);
        }
      }
    };
  }

  public void start(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    openChangeStreams(changeTrackingInfos);
  }

  public boolean checkIfAnyChangeTrackerIsAlive() {
    changeTrackingTasksFuture =
        changeTrackingTasksFuture.stream().map(this::recreateTaskIfCompleted).collect(Collectors.toSet());
    return changeTrackingTasksFuture.stream().anyMatch(f -> !f.isDone());
  }

  private Future<ChangeTrackingInfo<?>> recreateTaskIfCompleted(Future<ChangeTrackingInfo<?>> future) {
    if (future.isDone() && !executorService.isShutdown()) {
      try {
        ChangeTrackingInfo<?> info = future.get();
        ChangeTrackingTask newTask =
            createChangeStreamTask(getChangeTrackingInfo(info.getMorphiaClass(), info.getChangeSubscriber()));
        return executorService.submit(newTask, info);
      } catch (Exception e) {
        log.warn("Failed to recreate change stream tracker thread", e);
      }
    }
    return future;
  }

  private <T extends PersistentEntity> ChangeTrackingInfo<T> getChangeTrackingInfo(
      Class subscribedClass, ChangeSubscriber changeSubscriber) {
    return new ChangeTrackingInfo<T>(subscribedClass, changeSubscriber);
  }

  private String getResumeToken(Class<?> morphiaClass) {
    CDCStateEntity cdcStateEntityState = wingsPersistence.get(CDCStateEntity.class, morphiaClass.getCanonicalName());
    String token = null;
    if (cdcStateEntityState != null) {
      token = cdcStateEntityState.getLastSyncedToken();
    }
    return token;
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
  }
}
