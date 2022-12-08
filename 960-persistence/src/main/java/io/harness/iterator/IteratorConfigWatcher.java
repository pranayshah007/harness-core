/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import io.harness.config.DynamicConfigWatcher;
import io.harness.manage.ManagedExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IteratorConfigWatcher extends DynamicConfigWatcher {
  private final ManagedExecutorService managedExecutorService;
  private final IteratorExecutionHandler iteratorExecutionHandler;
  private final String iteratorConfigFile;

  /**
   * Constructor for the iteratorConfigWatcher
   * @param iteratorExecutionHandler Object reference to the iteratorExecutionHandler which will
   *                                apply the iterator configuration
   * @param iteratorConfigPath The path to watch or monitor for iterator configuration changes
   * @param iteratorConfigFile The file to read for new configuration when changes are made
   */
  public IteratorConfigWatcher(
      IteratorExecutionHandler iteratorExecutionHandler, String iteratorConfigPath, String iteratorConfigFile) {
    super(iteratorConfigPath);
    ExecutorService executorService =
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("IteratorConfigWatcher").build());
    this.iteratorExecutionHandler = iteratorExecutionHandler;
    this.iteratorConfigFile = iteratorConfigFile;
    this.managedExecutorService = new ManagedExecutorService(executorService);
  }

  /**
   * Method to start the Iterator monitor task
   */
  public void startIteratorConfigWatcher() {
    // Start the iterator config watcher task
    managedExecutorService.submit(this);
  }

  /**
   * Method to stop the Iterator monitor task
   */
  public void stopIteratorConfigWatcher(int awaitTime) {
    managedExecutorService.shutdown();
    try {
      if (!managedExecutorService.awaitTermination(awaitTime, TimeUnit.MILLISECONDS)) {
        managedExecutorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      managedExecutorService.shutdownNow();
    }
  }

  /**
   * Helper method to read the iteratorConfigFile, parse the contents and return it
   * @return Array of iterator configurations parsed from the config file
   */
  public IteratorExecutionHandler.DynamicIteratorConfig[] readIteratorConfiguration() {
    ObjectMapper objectMapper = new ObjectMapper();
    Path dir = Paths.get(iteratorConfigFile);

    IteratorExecutionHandler.DynamicIteratorConfig[] configOptions = {};
    try {
      configOptions = objectMapper.readValue(dir.toFile(), IteratorExecutionHandler.DynamicIteratorConfig[].class);
    } catch (IOException e) {
      log.error("Received exception while reading iteratorConfigFile {} ", iteratorConfigFile, e);
    }

    return configOptions;
  }

  /**
   * This method overrides the base class handleConfigEvent
   * to provide an iterator specific event handling.
   */
  @Override
  protected void handleConfigEvent() {
    // Invoke the iterator execution handler method
    // Ideally, we should post this to a JobQ and it should be
    // handled by another thread but not needed currently.
    IteratorExecutionHandler.DynamicIteratorConfig[] configOptions = readIteratorConfiguration();

    if (configOptions.length == 0) {
      log.error("No configuration received for the iterator, thus not applying any config changes");
      return;
    }

    // Apply the read iterator configurations
    for (IteratorExecutionHandler.DynamicIteratorConfig configOption : configOptions) {
      iteratorExecutionHandler.applyConfiguration(configOption);
    }
  }
}
