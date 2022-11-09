/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.changestreams;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.mongo.changestreams.ChangeEvent.ChangeEventBuilder;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;

@OwnedBy(PL)
public class ChangeEventFactory {
  @Inject private HPersistence persistence;

  private static Document convertBsonDocumentToDocument(BsonDocument bsonDocument) {
    Codec<Document> codec = MongoClient.getDefaultCodecRegistry().get(Document.class);
    return codec.decode(new BsonDocumentReader(bsonDocument), DecoderContext.builder().build());
  }

  private static DBObject convertBsonDocumentToDBObject(BsonDocument bsonDocument) {
    Document changesDocument = convertBsonDocumentToDocument(bsonDocument);
    return new BasicDBObject(changesDocument);
  }

  private static String getResumeTokenAsJson(ChangeStreamDocument<DBObject> changeStreamDocument) {
    return changeStreamDocument.getResumeToken().toJson();
  }

  private static String getUuidfromChangeStream(ChangeStreamDocument<DBObject> changeStreamDocument) {
    Document uuidDocument = convertBsonDocumentToDocument(changeStreamDocument.getDocumentKey());
    return (String) uuidDocument.get("_id");
  }

  private static ChangeType getChangeTypefromChangeStream(ChangeStreamDocument<DBObject> changeStreamDocument) {
    return ChangeType.valueOf(changeStreamDocument.getOperationType().getValue().toUpperCase());
  }

  private static DBObject getChangeDocumentfromChangeStream(ChangeStreamDocument<DBObject> changeStreamDocument) {
    BsonDocument changesBsonDocument = changeStreamDocument.getUpdateDescription().getUpdatedFields();
    return convertBsonDocumentToDBObject(changesBsonDocument);
  }

  private <T extends PersistentEntity> ChangeEvent<T> buildInsertChangeEvent(
      ChangeEventBuilder<T> changeEventBuilder, DBObject fullDocument, Class<T> entityClass) {
    T entityObject = persistence.convertToEntity(entityClass, fullDocument);
    changeEventBuilder.fullDocument(entityObject);
    changeEventBuilder.changes(null);
    changeEventBuilder.changeType(ChangeType.INSERT);
    return changeEventBuilder.build();
  }

  private <T extends PersistentEntity> ChangeEvent<T> buildUpdateChangeEvent(ChangeEventBuilder<T> changeEventBuilder,
      ChangeStreamDocument<DBObject> changeStreamDocument, Class<T> entityClass) {
    T fullDocument = persistence.convertToEntity(entityClass, changeStreamDocument.getFullDocument());
    DBObject dbObject = getChangeDocumentfromChangeStream(changeStreamDocument);
    changeEventBuilder.fullDocument(fullDocument);
    changeEventBuilder.changes(dbObject);
    return changeEventBuilder.build();
  }

  private <T extends PersistentEntity> ChangeEvent<T> buildReplaceChangeEvent(ChangeEventBuilder<T> changeEventBuilder,
      ChangeStreamDocument<DBObject> changeStreamDocument, Class<T> entityClass) {
    T fullDocument = persistence.convertToEntity(entityClass, changeStreamDocument.getFullDocument());
    changeEventBuilder.fullDocument(fullDocument);
    changeEventBuilder.changes(null);
    changeEventBuilder.changeType(ChangeType.UPDATE);
    return changeEventBuilder.build();
  }

  <T extends PersistentEntity> ChangeEvent<T> fromChangeStreamDocument(
      ChangeStreamDocument<DBObject> changeStreamDocument, Class<T> entityClass) {
    ChangeType changeType = getChangeTypefromChangeStream(changeStreamDocument);

    ChangeEventBuilder<T> changeEventBuilder = ChangeEvent.<T>builder();
    changeEventBuilder.entityType(entityClass);
    changeEventBuilder.changeType(changeType);
    changeEventBuilder.token(getResumeTokenAsJson(changeStreamDocument));
    changeEventBuilder.uuid(getUuidfromChangeStream(changeStreamDocument));

    ChangeEvent<T> changeEvent;
    switch (changeType) {
      case INSERT:
        changeEvent = buildInsertChangeEvent(changeEventBuilder, changeStreamDocument.getFullDocument(), entityClass);
        break;
      case REPLACE:
        changeEvent = buildReplaceChangeEvent(changeEventBuilder, changeStreamDocument, entityClass);
        break;
      case UPDATE:
        changeEvent = buildUpdateChangeEvent(changeEventBuilder, changeStreamDocument, entityClass);
        break;
      case DELETE:
        changeEvent = changeEventBuilder.build();
        break;
      case INVALIDATE:
        throw new UnexpectedException(String.format(
            "Invalidate event received. Should not have happened. Event %s", changeStreamDocument.toString()));
      default:
        throw new UnsupportedOperationException("Unknown OperationType received while processing ChangeStreamEvent");
    }
    return changeEvent;
  }
}
