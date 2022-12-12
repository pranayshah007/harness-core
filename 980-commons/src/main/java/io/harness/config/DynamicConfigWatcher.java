/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.config;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Class that implements a common functionality to watch
 * for modify events in the provided directory path.
 * The classes extending this should override the abstract
 * methods to provide handling to the modify events that
 * are specific to the overriding class.
 */
public abstract class DynamicConfigWatcher implements Runnable {
  private final String configPathToWatch;
  private WatchService watcher;
  private HashMap<WatchKey, Path> keys;

  public DynamicConfigWatcher(String configPathToWatchToWatch) {
    this.configPathToWatch = configPathToWatchToWatch;
  }

  /**
   * The main thread or job loop for this Task. The steps involved are -
   * 1. Initialize all the instance variables.
   * 2. Watch the given file to see if any updates happen using the WatchService.
   * 3. If a change is observed then the handle method is invoked to handle the changes.
   */
  @Override
  public void run() {
    log.info("Iterator Monitor Task is running");
    try {
      /*
       * 1. Initialize the instance variables.
       */
      configureWatcher();

      while (true) {
        /*
         * 2. Watch the dynamic configMap path for any updates.
         *
         * Wait for the key to be signaled, whenever the dir gets modified
         */
        WatchKey key;
        try {
          key = watcher.take();
        } catch (InterruptedException e) {
          log.error("WatchKey not obtained, so exiting");
          return;
        }

        Path dir = keys.get(key);
        if (dir == null) {
          log.error("WatchKey not recognized {} ", key);
          continue;
        }

        // Retrieve the event for the watch key
        for (WatchEvent<?> event : key.pollEvents()) {
          WatchEvent.Kind kind = event.kind();

          // Ignore any event apart from Modify
          if (kind != ENTRY_MODIFY) {
            continue;
          }

          // Context provides the file name of entry
          WatchEvent<Path> ev = cast(event);
          Path name = ev.context();
          Path child = dir.resolve(name);

          log.info("Config path received an event {}: {}", event.kind().name(), child);

          /*
           * Kubernetes configMap edits will keep modifying a directory with timestamp to
           * store the edits made to the configMap. And the actual configMap will be a
           * symbolic link pointing to this timestamp directory. Any Modify events in the
           * /opt/harness/config folder will correspond to edits for the config path.
           * */

          // Handle the configuration event
          handleConfigEvent();
        }

        // reset key and remove from set if directory no longer accessible
        boolean valid = key.reset();
        if (!valid) {
          keys.remove(key);

          // all directories are inaccessible
          if (keys.isEmpty()) {
            break;
          }
        }
      }
    } catch (Exception e) {
      log.info("Exception received while monitoring Config path {} ", configPathToWatch);
    }
  }

  /**
   * This method has to be overridden by child classes to implement handlers for the event.
   */
  protected abstract void handleConfigEvent();

  /**
   * Register the given directory with WatchService.
   * This will allow to receive MODIFY events occurring in the given directory.
   */
  private void register(Path dir) throws IOException {
    WatchKey key = dir.register(watcher, ENTRY_MODIFY);
    keys.put(key, dir);
  }

  /**
   *  Configure or initialize all the watcher related variables
   */
  private void configureWatcher() throws IOException {
    log.info("Config path to watch is {} ", configPathToWatch);
    Path dir = Paths.get(configPathToWatch);
    watcher = FileSystems.getDefault().newWatchService();
    keys = new HashMap<WatchKey, Path>();

    register(dir);
  }

  @SuppressWarnings("unchecked")
  static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }
}
