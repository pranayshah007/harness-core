/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo.iterator.provider;

import static io.harness.govern.Switch.unhandled;

import static java.lang.System.currentTimeMillis;

import io.harness.iterator.PersistentIterable;
import io.harness.mongo.iterator.BulkWriteOpsResults;
import io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.DBCollection;
import dev.morphia.query.FilterOperator;
import dev.morphia.query.FindOptions;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateOpsImpl;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

@Singleton
public class MorphiaPersistenceRequiredProvider<T extends PersistentIterable>
    implements PersistenceProvider<T, MorphiaFilterExpander<T>> {
  @Inject private HPersistence persistence;

  private Query<T> createQuery(
      Class<T> clazz, String fieldName, MorphiaFilterExpander<T> filterExpander, boolean unsorted) {
    Query<T> query = persistence.createQuery(clazz);

    if (!unsorted) {
      query.order(Sort.ascending(fieldName));
    }
    if (filterExpander != null) {
      filterExpander.filter(query);
    }
    return query;
  }

  private Query<T> createQuery(
      long now, Class<T> clazz, String fieldName, MorphiaFilterExpander<T> filterExpander, boolean unsorted) {
    Query<T> query = createQuery(clazz, fieldName, filterExpander, unsorted);
    if (filterExpander == null) {
      query.field(fieldName).lessThan(now);
    } else {
      query.and(query.criteria(fieldName).lessThan(now));
    }
    return query;
  }

  @Override
  public void updateEntityField(T entity, List<Long> nextIterations, Class<T> clazz, String fieldName) {
    UpdateOperations<T> operations = persistence.createUpdateOperations(clazz).set(fieldName, nextIterations);
    persistence.update(entity, operations);
  }

  @Override
  public T obtainNextInstance(long base, long throttled, Class<T> clazz, String fieldName,
      SchedulingType schedulingType, Duration targetInterval, MorphiaFilterExpander<T> filterExpander, boolean unsorted,
      boolean isDelegateTaskMigrationEnabled) {
    long now = currentTimeMillis();
    Query<T> query = createQuery(now, clazz, fieldName, filterExpander, unsorted);
    UpdateOperations<T> updateOperations = persistence.createUpdateOperations(clazz);
    switch (schedulingType) {
      case REGULAR:
        updateOperations.set(fieldName, base + targetInterval.toMillis());
        break;
      case IRREGULAR:
        updateOperations.removeFirst(fieldName);
        break;
      case IRREGULAR_SKIP_MISSED:
        updateOperations.removeAll(fieldName, new BasicDBObject(FilterOperator.LESS_THAN_OR_EQUAL.val(), throttled));
        break;
      default:
        unhandled(schedulingType);
    }
    return persistence.findAndModifySystemData(query, updateOperations, HPersistence.returnOldOptions);
  }

  @Override
  public T findInstance(Class<T> clazz, String fieldName, MorphiaFilterExpander<T> filterExpander, boolean unsorted,
      boolean isDelegateTaskMigrationEnabled) {
    Query<T> resultQuery = createQuery(clazz, fieldName, filterExpander, unsorted).project(fieldName, true);
    return resultQuery.get();
  }

  @Override
  public void recoverAfterPause(Class<T> clazz, String fieldName) {
    persistence.update(persistence.createQuery(clazz).filter(fieldName, null),
        persistence.createUpdateOperations(clazz).unset(fieldName));
    persistence.update(persistence.createQuery(clazz).field(fieldName).sizeEq(0),
        persistence.createUpdateOperations(clazz).unset(fieldName));
  }

  @Override
  public MorphiaIterator<T, T> obtainNextInstances(Class<T> clazz, String fieldName,
      MorphiaFilterExpander<T> filterExpander, boolean unsorted, int limit, boolean isDelegateTaskMigrationEnabled) {
    long now = currentTimeMillis();
    Query<T> query = createQuery(now, clazz, fieldName, filterExpander, unsorted);

    return query.fetch(new FindOptions().limit(limit));
  }

  @Override
  public BulkWriteOpsResults bulkWriteDocumentsMatchingIds(
      Class<T> clazz, List<String> ids, String fieldName, long base, Duration targetInterval) {
    // 1. Create an update operation to set the given field with given value
    UpdateOperations<T> updateOperations = persistence.createUpdateOperations(clazz);
    updateOperations.set(fieldName, base + targetInterval.toMillis());

    // 2. Initialize an unordered bulk operation
    DBCollection collection = persistence.getCollection(clazz);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    // 3. Create find query.
    /* The Mongo documents will have '_id' field of type ObjectId if Mongo assigned the id.
       It will have '_id' field of type String if the document was inserted manually.
       Thus, check if the given id is of type ObjectId or String and prepare find queries accordingly.
     */
    List<String> stringIds = new ArrayList<>();
    List<ObjectId> objectIds = new ArrayList<>();

    for (String id : ids) {
      if (ObjectId.isValid(id)) {
        objectIds.add(new ObjectId(id));
      } else {
        stringIds.add(id);
      }
    }

    // 3a. Create a find query to match String Ids
    if (!stringIds.isEmpty()) {
      Query<T> findQuery = persistence.createQuery(clazz);
      findQuery.criteria("_id").in(stringIds);
      bulkWriteOperation.find(findQuery.getQueryObject()).update(((UpdateOpsImpl) updateOperations).getOps());
    }

    // 3b. Create a find query to match Object Ids
    if (!objectIds.isEmpty()) {
      Query<T> findQuery = persistence.createQuery(clazz);
      findQuery.criteria("_id").in(objectIds);
      bulkWriteOperation.find(findQuery.getQueryObject()).update(((UpdateOpsImpl) updateOperations).getOps());
    }

    // 4. Execute the Bulk write operation and return the results.
    BulkWriteResult bulkWriteResult = bulkWriteOperation.execute();
    return BulkWriteOpsResults.builder()
        .operationAcknowledged(bulkWriteResult.isAcknowledged())
        .matchedCount(bulkWriteResult.getMatchedCount())
        .modifiedCount(bulkWriteResult.getModifiedCount())
        .build();
  }
}
