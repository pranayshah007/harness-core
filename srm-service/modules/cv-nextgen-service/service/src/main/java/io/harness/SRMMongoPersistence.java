/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.cvng.CVConstants;
import io.harness.mongo.MongoPersistence;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import dev.morphia.AdvancedDatastore;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SRMMongoPersistence extends MongoPersistence implements SRMPersistence {
  @Inject
  public SRMMongoPersistence(@Named("primaryDatastore") AdvancedDatastore primaryDatastore,
      HarnessConnectionPoolListener harnessConnectionPoolListener) {
    super(primaryDatastore, harnessConnectionPoolListener);
  }

  @Override
  public <T extends PersistentEntity> BulkWriteResult upsertBatch(
      Class<T> cls, List<T> entities, List<String> excludeFields) throws IllegalAccessException {
    final DBCollection collection = getCollection(cls);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    Class<?> myClass = cls;
    List<Field> fields = new ArrayList<>();
    while (myClass != null) {
      fields.addAll(Arrays.asList(myClass.getDeclaredFields()));
      myClass = myClass.getSuperclass();
    }
    for (T entity : entities) {
      if (entity instanceof UuidAware) {
        UuidAware uuidAware = (UuidAware) entity;
        onSave(entity);
        DBObject dbObject = createQuery(cls).filter("uuid", uuidAware.getUuid()).getQueryObject();
        DBObject updateObj = new BasicDBObject();
        for (Field field : fields) {
          if (excludeFields != null && excludeFields.contains(field.getName())) {
            continue;
          }
          field.setAccessible(true);
          Object value = field.get(entity);
          if (value != null) {
            updateObj.put(field.getName(), value);
          }
        }
        bulkWriteOperation.find(dbObject).upsert().updateOne(new BasicDBObject(CVConstants.SET_KEY, updateObj));

      } else {
        throw new IllegalAccessException("Entity doesn't has uuid");
      }
    }
    return bulkWriteOperation.execute();
  }
}
