/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.remote;

import io.harness.logging.common.CustomJsonLayout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.collect.Queues;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StackdriverLogAppender implements LogAppender<ILoggingEvent> {
  private final CustomJsonLayout layout = new CustomJsonLayout();
  private final BlockingQueue<Map<String, ?>> logQueue = Queues.newLinkedBlockingQueue(500000);

  @Override
  public synchronized void append(final ILoggingEvent logEvent) {
    try {
      if (!logQueue.offer(layout.toJsonMap(logEvent))) {
        logQueue.clear();
        log.error("No space left in log queue. Cleared.");
      }
    } catch (Exception ex) {
      log.error("Error appending log entry", ex);
    }
  }

  @Override
  public synchronized void drain(final List<Map<String, ?>> queue, final int max) {
    logQueue.drainTo(queue, max);
  }

  @Override
  public boolean isEmpty() {
    return logQueue.isEmpty();
  }
}
