/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeEvent.ChangeEventBuilder;
import io.harness.changestreamsframework.ChangeType;
import io.harness.persistence.PersistentEntity;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;

@OwnedBy(CE)
@Slf4j
public class CDCEntityBulkTaskConverter {
  public static <T extends PersistentEntity> ChangeEvent<T> convert(Class<T> entityType, Document document) {
    DBObject dbObject = toDBObject(document);

    ChangeEventBuilder<T> builder = ChangeEvent.builder();
    return builder.fullDocument(dbObject)
        .changeType(ChangeType.INSERT)
        .entityType(entityType)
        .uuid(getUuidFromDocument(document))
        .build();
  }

  public static DBObject toDBObject(Document document) {
    return BasicDBObject.parse(document.toJson());
  }

  private static String getUuidFromDocument(Document document) {
    if (document.get("_id") instanceof String) {
      return (String) document.get("_id");
    }
    return ((ObjectId) document.get("_id")).toString();
  }
}
