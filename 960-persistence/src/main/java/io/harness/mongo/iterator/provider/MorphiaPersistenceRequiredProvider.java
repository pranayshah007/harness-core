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
import io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.persistence.HPersistence;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mongodb.morphia.query.FilterOperator;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

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
  public ImmutableList<T> obtainNextBatch(
      Class<T> clazz, Optional<String> prevId, int batchLimit, MorphiaFilterExpander<T> filterExpander) {
    Query<T> batchQuery = createBatchQuery(clazz, prevId, filterExpander);

    List<T> batchResults = new ArrayList<>();
    for (T entity : batchQuery.fetch(new FindOptions().limit(batchLimit))) {
      batchResults.add(entity);
    }
    return ImmutableList.copyOf(batchResults);
  }

  @Override
  public T obtainNextInstance(long base, long throttled, Class<T> clazz, String fieldName,
      SchedulingType schedulingType, Duration targetInterval, MorphiaFilterExpander<T> filterExpander,
      boolean unsorted) {
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
  public T findInstance(Class<T> clazz, String fieldName, MorphiaFilterExpander<T> filterExpander) {
    Query<T> resultQuery = createQuery(clazz, fieldName, filterExpander, false).project(fieldName, true);
    return resultQuery.get();
  }

  @Override
  public void recoverAfterPause(Class<T> clazz, String fieldName) {
    persistence.update(persistence.createQuery(clazz).filter(fieldName, null),
        persistence.createUpdateOperations(clazz).unset(fieldName));
    persistence.update(persistence.createQuery(clazz).field(fieldName).sizeEq(0),
        persistence.createUpdateOperations(clazz).unset(fieldName));
  }

  private Query<T> createBatchQuery(Class<T> clazz, Optional<String> prevId, MorphiaFilterExpander<T> filterExpander) {
    Query<T> query = persistence.createQuery(clazz);

    query.order(Sort.ascending("_id"));
    if (prevId.isPresent()) {
      query.filter("_id >", prevId.get());
    }

    if (filterExpander != null) {
      filterExpander.filter(query);
    }

    return query;
  }
}
