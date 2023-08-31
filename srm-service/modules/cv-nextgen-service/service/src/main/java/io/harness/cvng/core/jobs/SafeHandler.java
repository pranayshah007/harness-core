/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.mongo.iterator.MongoPersistenceIterator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SafeHandler<T> implements MongoPersistenceIterator.Handler<T> {
  public void handle(T entity) {
    try {
      handleUnsafely(entity);
    } catch (Exception exception) {
      log.warn("Exception while handling entity {}", entity.getClass().getSimpleName(), exception);
      // TODO: Add metrics for iterator execution
    }
  }

  public abstract void handleUnsafely(T entity);
}
