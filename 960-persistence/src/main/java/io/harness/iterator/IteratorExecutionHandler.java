/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import lombok.Builder;
import lombok.Value;

/**
 * IteratorExecutionHandler provides an interface that allows -
 * 1. Services to register their iterator and iterator handler.
 * 2. Configuration watcher to invoke a handler to apply the iterator configuration.
 */
public interface IteratorExecutionHandler {
  @Value
  @Builder
  class DynamicIteratorConfig {
    String name;
    boolean enabled;
    int threadPoolSize;
    int threadPoolIntervalInSeconds;
    String nextIterationMode;
    int targetIntervalInSeconds;
    int throttleIntervalInSeconds;
    String iteratorMode;
    int redisBatchSize;
    int redisLockTimeout;
  }

  /**
   * This method allows to register an iterator handler
   * for an iterator.
   * @param iteratorName Name of the iterator
   * @param iteratorHandler Handler for the iterator
   */
  void registerIteratorHandler(String iteratorName, IteratorBaseHandler iteratorHandler);

  /**
   * This method allows to start all the iterators
   * that are registered with the iterator handler.
   */
  void startIterators();

  /**
   * All the executors or handlers of iterator configuration
   * should implement this method. The executor should also
   * define failure strategies in case it fails to apply the
   * configuration.
   */
  void applyConfiguration(DynamicIteratorConfig iteratorConfigOption);
}
