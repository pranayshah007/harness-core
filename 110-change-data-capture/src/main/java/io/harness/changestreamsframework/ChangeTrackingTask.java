/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreamsframework;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.PersistentEntity;

import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoInterruptedException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.Document;

@Value
@Slf4j
@OwnedBy(HarnessTeam.CE)
class ChangeTrackingTask implements Runnable {
  private ChangeStreamSubscriber changeStreamSubscriber;
  private MongoCollection<DBObject> collection;
  private ClientSession clientSession;
  private Class<? extends PersistentEntity> subscribedClass;
  private BsonDocument resumeToken;

  ChangeTrackingTask(ChangeStreamSubscriber changeStreamSubscriber, MongoCollection<DBObject> collection,
      ClientSession clientSession, String tokenParam, Class<? extends PersistentEntity> subscribedClass) {
    this.changeStreamSubscriber = changeStreamSubscriber;
    this.collection = collection;
    this.clientSession = clientSession;
    this.subscribedClass = subscribedClass;
    if (tokenParam != null) {
      this.resumeToken =
          Document.parse(tokenParam).toBsonDocument(BsonDocument.class, MongoClient.getDefaultCodecRegistry());
    } else {
      this.resumeToken = null;
    }
  }

  private void handleChange(final ChangeStreamDocument<DBObject> changeStreamDocument) {
    changeStreamSubscriber.onChange(changeStreamDocument);
  }

  private void openChangeStream(Consumer<ChangeStreamDocument<DBObject>> changeStreamDocumentConsumer) {
    ChangeStreamIterable<DBObject> changeStreamIterable;
    ChangeStreamIterable<DBObject> changeStreamIterableResumeToken;
    if (Objects.isNull(clientSession)) {
      changeStreamIterable = collection.watch();
      changeStreamIterableResumeToken = collection.watch();
    } else {
      changeStreamIterable = collection.watch(clientSession);
      changeStreamIterableResumeToken = collection.watch(clientSession);
    }

    changeStreamIterable =
        changeStreamIterable.fullDocument(FullDocument.UPDATE_LOOKUP).maxAwaitTime(1, TimeUnit.MINUTES);
    changeStreamIterableResumeToken =
        changeStreamIterableResumeToken.fullDocument(FullDocument.UPDATE_LOOKUP).maxAwaitTime(1, TimeUnit.MINUTES);

    MongoCursor<ChangeStreamDocument<DBObject>> mongoCursor = null;
    try {
      if (resumeToken == null) {
        log.info("Opening changeStream without resumeToken");
        mongoCursor = changeStreamIterable.iterator();
      } else {
        log.info("Opening changeStream with resumeToken");
        boolean isResumeTokenValid = true;
        try {
          mongoCursor = changeStreamIterableResumeToken.resumeAfter(resumeToken).iterator();
        } catch (Exception ex) {
          isResumeTokenValid = false;
          log.error("Resume Token Invalid :{}", ex);
        }
        if (!isResumeTokenValid) {
          log.error("Resume Token Invalid, Creating Change Stream Without Resume Token");
          mongoCursor = changeStreamIterable.iterator();
        }
      }
      log.info("Connection details for mongo cursor {}", mongoCursor.getServerCursor());
      mongoCursor.forEachRemaining(changeStreamDocumentConsumer);
    } finally {
      if (mongoCursor != null) {
        log.info("Closing mongo cursor");
        mongoCursor.close();
      }
    }
  }

  @Override
  public void run() {
    try {
      log.info("changeStream opened on {}", collection.getNamespace());
      openChangeStream(this::handleChange);
    } catch (MongoInterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Changestream on {} interrupted", collection.getNamespace(), e);
    } catch (RuntimeException e) {
      log.error("Unexpectedly {} changeStream shutting down", collection.getNamespace(), e);
    }
  }
}
