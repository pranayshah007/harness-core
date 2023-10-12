/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo.iterator.provider;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType;

import io.harness.iterator.PersistentIterable;
import io.harness.mongo.iterator.BulkWriteOpsResults;
import io.harness.mongo.iterator.filter.FilterExpander;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;

public interface PersistenceProvider<T extends PersistentIterable, F extends FilterExpander> {
  void updateEntityField(T entity, List<Long> nextIterations, Class<T> clazz, String fieldName);

  T obtainNextInstance(long base, long throttled, Class<T> clazz, String fieldName, SchedulingType schedulingType,
      Duration targetInterval, F filterExpander, boolean unsorted, boolean isDelegateTaskMigrationEnabled);

  T findInstance(
      Class<T> clazz, String fieldName, F filterExpander, boolean unsorted, boolean isDelegateTaskMigrationEnabled);

  void recoverAfterPause(Class<T> clazz, String fieldName);

  Iterator<T> obtainNextInstances(Class<T> clazz, String fieldName, F filterExpander, boolean unsorted, int limit);

  BulkWriteOpsResults bulkWriteDocumentsMatchingIds(
      Class<T> clazz, List<String> ids, String fieldName, long base, Duration targetInterval);
}
