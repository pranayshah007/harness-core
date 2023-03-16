/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import java.time.Duration;
import java.util.HashMap;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IteratorExecutionHandlerImpl implements IteratorExecutionHandler {
  private final IteratorConfigWatcher iteratorConfigWatcher;
  private final HashMap<String, IteratorBaseHandler> iteratorHandlerMap;
  @Getter private final HashMap<String, IteratorState> iteratorState;

  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2; // The factor by how much the batchSize should be increased
  private static final int REDIS_LOCK_TIMEOUT_SECONDS = 5; // The lockTimeout is the duration a lock is held
  public static final String REDIS_BATCH = "REDIS_BATCH";

  /**
   * Enum represents the different states that an iterator can be at -
   * 1. INIT: The iterator has been registered
   * 2. RUNNING: The iterator is currently running
   * 3. NOT_RUNNING: The iterator is currently not running
   */
  protected enum IteratorStateValues { INIT, RUNNING, NOT_RUNNING }

  /**
   * IteratorState maintains both the current state
   * and the current configuration of an iterator.
   */
  @Value
  @Builder
  protected static class IteratorState {
    IteratorStateValues iteratorStateValue;
    DynamicIteratorConfig iteratorConfigOption;
  }

  /**
   * Constructor that initializes the -
   * 1. iteratorConfigWatcher - Watcher service that looks for iterator configuration changes
   * 2. iteratorHandlerMap - It maps iterator name to its handler
   * 3. iteratorState - It maintains the current state of an iterator
   * @param iteratorConfigPath The path that contains the configuration file. Watcher will monitor this path.
   * @param iteratorConfigFile The file that contains the configuration. Watcher will read this file.
   */
  public IteratorExecutionHandlerImpl(String iteratorConfigPath, String iteratorConfigFile) {
    this.iteratorConfigWatcher = new IteratorConfigWatcher(this, iteratorConfigPath, iteratorConfigFile);
    this.iteratorHandlerMap = new HashMap<String, IteratorBaseHandler>();
    this.iteratorState = new HashMap<String, IteratorState>();
  }

  /**
   * Method to insert an iterator handler into the HashMap
   * @param iteratorName Name of the iterator
   * @param iteratorHandler The iterator handler object
   */
  @Override
  public void registerIteratorHandler(String iteratorName, IteratorBaseHandler iteratorHandler) {
    iteratorHandlerMap.put(iteratorName, iteratorHandler);
    iteratorState.put(iteratorName, IteratorState.builder().iteratorStateValue(IteratorStateValues.INIT).build());
  }

  /**
   * Method to start all the iterators currently registered
   */
  @Override
  public void startIterators() {
    /*
     * 1. Start the iterators by reading the initial default configuration
     * */
    DynamicIteratorConfig[] configOptions = iteratorConfigWatcher.readIteratorConfiguration();

    if (configOptions.length == 0) {
      log.error("No configuration received for the iterators - not starting the registered iterators");
      return;
    }

    for (DynamicIteratorConfig configOption : configOptions) {
      applyIteratorConfig(configOption);
    }

    /*
     * 2. Start iterator config watcher for dynamic changes of iterator configurations
     * */
    iteratorConfigWatcher.startIteratorConfigWatcher();
  }

  /**
   * Method to apply a new configuration for the iterator
   * @param iteratorConfigOption Contains the new iterator configuration, also provides
   *                            the iterator name on which the config has to be applied.
   */
  @Override
  public void applyConfiguration(DynamicIteratorConfig iteratorConfigOption) {
    // If iterator state doesn't exist the log an error and return.
    if (!iteratorState.containsKey(iteratorConfigOption.getName())) {
      log.error("Iterator state doesn't exist for iterator {}", iteratorConfigOption.getName());
      return;
    }

    /*
     * Check the current state of the iterator.
     *    a. If its in INIT state then its only registered and the configuration
     *        for the iterator is not yet applied. Thus, apply the configuration.
     *    b. If it is not in INIT state then the iterator configuration had previously
     *        been applied and now a new configuration needs to be applied. Thus, check
     *        if there is any change from the previous configuration - if there is no
     *        change then ignore else apply the new configuration for the iterator.
     */
    IteratorState iterator = iteratorState.get(iteratorConfigOption.getName());
    if (iterator.iteratorStateValue == IteratorStateValues.INIT) {
      // The iterator has only been registered and configuration is not yet applied.
      applyIteratorConfig(iteratorConfigOption);
    } else {
      if (iteratorConfigOption.equals(iterator.iteratorConfigOption)) {
        // No change in the iterator config, thus ignore.
        return;
      } else {
        /*
         * There is a change in the iterator config thus apply it.
         * We have a simple failure strategy currently - attempt to apply the
         * configuration 3 times with a delay of 1 second between each attempt.
         */
        int configAttempts = 3;
        for (int attempt = 0; attempt < configAttempts; attempt++) {
          if (applyIteratorConfig(iteratorConfigOption)) {
            // Configuration applied successfully, so return
            return;
          }
          sleep(ofMillis(1000));
        }
      }
    }
  }

  /**
   * Helper method that applies the given iterator configuration.
   */
  private boolean applyIteratorConfig(DynamicIteratorConfig configOption) {
    String iteratorName = configOption.getName();
    if (!iteratorState.containsKey(iteratorName)) {
      log.info("Iterator {} not registered, thus not starting ", iteratorName);
      return false;
    }
    IteratorState iterator = iteratorState.get(iteratorName);
    IteratorBaseHandler iteratorBaseHandler = iteratorHandlerMap.get(iteratorName);

    /*
     * If the iterator is currently running then shut it
     * down first before applying the new configuration.
     * */
    if (iterator.iteratorStateValue == IteratorStateValues.RUNNING) {
      // Shutdown the current iterator executor service
      iteratorBaseHandler.stopExecutor();
      if (!iteratorBaseHandler.isExecutorTerminated()) {
        return false;
      }
    }

    IteratorStateValues iteratorStateValue;
    if (configOption.isEnabled()) {
      log.info("Iterator {} is enabled - starting it up", configOption.getName());

      if (REDIS_BATCH.equals(configOption.getIteratorMode())) {
        createAndStartRedisBatchModeIterator(configOption);
      } else {
        createAndStartPumpLoopModeIterator(configOption);
      }

      iteratorStateValue = IteratorStateValues.RUNNING;
    } else {
      log.info("Iterator {} is not enabled - not starting it", configOption.getName());
      iteratorStateValue = IteratorStateValues.NOT_RUNNING;
    }

    // Record the current configuration of the iterator in the iteratorState
    iteratorState.put(iteratorName,
        IteratorState.builder().iteratorStateValue(iteratorStateValue).iteratorConfigOption(configOption).build());
    return true;
  }

  /**
   * Helper method to return a Duration based on the given granularity and interval.
   * The supported granularity are - Seconds, Minutes and Hours only.
   * @param interval Time interval
   * @return Time Duration
   */
  private Duration getIntervalDuration(int interval) {
    return Duration.ofSeconds(interval);
  }

  /**
   * Helper method to get next iteration interval for the iterator.
   * An iterator can have its next iteration determined either by throttling
   * or by giving a fixed target interval.
   * @param iteratorConfigOption The iterator's configuration
   * @return Time Duration based on next iteration option
   */
  private Duration getNextIterationInterval(DynamicIteratorConfig iteratorConfigOption) {
    switch (iteratorConfigOption.getNextIterationMode()) {
      case "TARGET":
        return getIntervalDuration(iteratorConfigOption.getTargetIntervalInSeconds());
      case "THROTTLE":
        return getIntervalDuration(iteratorConfigOption.getThrottleIntervalInSeconds());
      default:
        // Either of the above 2 iteration mode should be set, else its invalid configuration
        return Duration.ofMinutes(0);
    }
  }

  /**
   * Helper method to create and start Redis Batch mode iterator.
   *
   * @param config provides the necessary configuration for the iterator.
   */
  private void createAndStartRedisBatchModeIterator(DynamicIteratorConfig config) {
    int redisBatchSize = config.getRedisBatchSize();
    int redisLockTimeout = config.getRedisLockTimeout();
    if (redisBatchSize == 0) {
      redisBatchSize = BATCH_SIZE_MULTIPLY_FACTOR * config.getThreadPoolSize();
    }

    if (redisLockTimeout == 0) {
      redisLockTimeout = REDIS_LOCK_TIMEOUT_SECONDS;
    }
    iteratorHandlerMap.get(config.getName())
        .createAndStartRedisBatchIterator(PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                                              .name(config.getName())
                                              .poolSize(config.getThreadPoolSize())
                                              .batchSize(redisBatchSize)
                                              .lockTimeout(redisLockTimeout)
                                              .interval(getIntervalDuration(config.getThreadPoolIntervalInSeconds()))
                                              .build(),
            getNextIterationInterval(config));
  }

  /**
   * Helper method to create and start Pump or Loop mode iterator.
   *
   * @param config provides the necessary configuration for the iterator.
   */
  private void createAndStartPumpLoopModeIterator(DynamicIteratorConfig config) {
    iteratorHandlerMap.get(config.getName())
        .createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                    .name(config.getName())
                                    .poolSize(config.getThreadPoolSize())
                                    .interval(getIntervalDuration(config.getThreadPoolIntervalInSeconds()))
                                    .build(),
            getNextIterationInterval(config));
  }
}
