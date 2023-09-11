/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.client.impl.appender;

import static java.util.Arrays.stream;
import static net.openhft.chronicle.queue.impl.single.SingleChronicleQueue.SUFFIX;

import io.harness.logging.LoggingListener;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;

/**
 * Monitors queue health so that we don't fill up all disk space on the delegate with too many files
 */
@Slf4j
@Singleton
public class ChronicleQueueMonitor extends AbstractScheduledService {
  // Since the queue is configured with RollCycles.MINUTELY, each file correspond to one minute of data.
  private static final int THRESHOLD = 30;

  private final RollingChronicleQueue queue;

  private volatile boolean healthy = true;

  @Inject
  public ChronicleQueueMonitor(@Named("appender") RollingChronicleQueue queue) {
    this.queue = queue;
    addListener(new LoggingListener(this), MoreExecutors.directExecutor());
  }

  @Override
  protected void runOneIteration() {
    try {
      long fileCount = Optional.ofNullable(queue.file())
                           .map(File::listFiles)
                           .map(fileList -> stream(fileList).filter(file -> file.getName().endsWith(SUFFIX)).count())
                           .orElse(0L);
      log.info("eventQueue fileCount: {}", fileCount);
      if (fileCount > THRESHOLD) {
        log.warn("EventQueue file count on delegate is too high. Marking unhealthy. Current count {}", fileCount);
        healthy = false;
      } else if (!healthy) {
        log.info("Event queue recovered. Marking healthy. Current count {}", fileCount);
        healthy = true;
      }
    } catch (Exception e) {
      log.error("Ignoring encountered exception", e);
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(1, 1, TimeUnit.MINUTES);
  }

  public boolean isHealthy() {
    return healthy;
  }
}
