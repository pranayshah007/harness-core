/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo.iterator.provider;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.govern.Switch.unhandled;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentIterable;
import io.harness.mongo.iterator.BulkWriteOpsResults;
import io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType;
import io.harness.mongo.iterator.filter.SpringFilterExpander;

import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import dev.morphia.query.FilterOperator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(CDC)
public class SpringPersistenceRequiredProvider<T extends PersistentIterable>
    implements PersistenceProvider<T, SpringFilterExpander> {
  private final MongoTemplate persistence;

  public SpringPersistenceRequiredProvider(MongoTemplate persistence) {
    this.persistence = persistence;
  }

  private Query createQuery(String fieldName, SpringFilterExpander filterExpander, boolean unsorted) {
    Query query = new Query();
    if (!unsorted) {
      query.with(Sort.by(new Order(Sort.Direction.ASC, fieldName)));
    }
    if (filterExpander != null) {
      filterExpander.filter(query);
    }
    return query;
  }

  private Query createQuery(long now, String fieldName, SpringFilterExpander filterExpander, boolean unsorted) {
    Criteria criteria = new Criteria();
    Query query = createQuery(fieldName, filterExpander, unsorted);
    if (filterExpander == null) {
      query.addCriteria(Criteria.where(fieldName).lt(now));
    } else {
      query.addCriteria(criteria.andOperator(Criteria.where(fieldName).lt(now)));
    }
    return query;
  }

  @Override
  public void updateEntityField(T entity, List<Long> nextIterations, Class<T> clazz, String fieldName) {
    Update update = new Update();
    update.set(fieldName, nextIterations);
    persistence.updateFirst(new Query(Criteria.where("_id").is(entity.getUuid())), update, clazz);
  }

  @Override
  public T obtainNextInstance(long base, long throttled, Class<T> clazz, String fieldName,
      SchedulingType schedulingType, Duration targetInterval, SpringFilterExpander filterExpander, boolean unsorted,
      boolean isDelegateTaskMigrationEnabled) {
    long now = currentTimeMillis();
    Query query = createQuery(now, fieldName, filterExpander, unsorted);
    Update update = new Update();
    switch (schedulingType) {
      case REGULAR:
        update.set(fieldName, base + targetInterval.toMillis());
        break;
      case IRREGULAR:
        update.pop(fieldName, Update.Position.FIRST);
        break;
      case IRREGULAR_SKIP_MISSED:
        update.pull(fieldName, new BasicDBObject(FilterOperator.LESS_THAN_OR_EQUAL.val(), throttled));
        break;
      default:
        unhandled(schedulingType);
    }
    return persistence.findAndModify(
        query, update, FindAndModifyOptions.options().upsert(false).returnNew(false), clazz);
  }

  @Override
  public T findInstance(Class<T> clazz, String fieldName, SpringFilterExpander filterExpander, boolean unsorted,
      boolean isDelegateTaskMigrationEnabled) {
    return persistence.findOne(createQuery(fieldName, filterExpander, unsorted), clazz);
  }

  @Override
  public void recoverAfterPause(Class<T> clazz, String fieldName) {
    Update updateNull = new Update();
    updateNull.unset(fieldName);
    persistence.updateFirst(new Query(Criteria.where(fieldName).is(null)), updateNull, clazz);
    Update updateEmpty = new Update();
    updateEmpty.unset(fieldName);
    persistence.updateFirst(new Query(Criteria.where(fieldName).size(0)), updateEmpty, clazz);
  }

  @Override
  public Iterator<T> obtainNextInstances(Class<T> clazz, String fieldName, SpringFilterExpander filterExpander,
      boolean unsorted, int limit, boolean isDelegateTaskMigrationEnabled) {
    long now = currentTimeMillis();
    Query query = createQuery(now, fieldName, filterExpander, unsorted);
    query.limit(limit);

    List<T> docs = persistence.find(query, clazz);

    return docs.iterator();
  }

  @Override
  public BulkWriteOpsResults bulkWriteDocumentsMatchingIds(
      Class<T> clazz, List<String> ids, String fieldName, long base, Duration targetInterval) {
    // 1. Create an update operation to set the given field with given value
    Update update = new Update();
    update.set(fieldName, base + targetInterval.toMillis());

    // 2. Initialize an unordered bulk operation
    BulkOperations bulkOps = persistence.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);

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
      Query query = new Query(Criteria.where("_id").in(stringIds));
      bulkOps.updateMulti(query, update);
    }

    // 3b. Create a find query to match Object Ids
    if (!objectIds.isEmpty()) {
      Query query = new Query(Criteria.where("_id").in(objectIds));
      bulkOps.updateMulti(query, update);
    }

    // 4. Execute the Bulk write operation and return the results.
    BulkWriteResult bulkWriteResult = bulkOps.execute();
    return BulkWriteOpsResults.builder()
        .operationAcknowledged(bulkWriteResult.wasAcknowledged())
        .matchedCount(bulkWriteResult.getMatchedCount())
        .modifiedCount(bulkWriteResult.getModifiedCount())
        .build();
  }
}
