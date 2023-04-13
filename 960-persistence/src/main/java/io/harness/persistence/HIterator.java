/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ProcessTimeLogContext;
import io.harness.mongo.log.CollectionLogContext;

import dev.morphia.query.MorphiaIterator;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;

// This is a simple wrapper around MorphiaIterator to provide AutoCloseable implementation
@OwnedBy(PL)
@Slf4j
public class HIterator<T> implements AutoCloseable, Iterable<T>, Iterator<T> {
  private static final long SLOW_PROCESSING = 1000;
  private static final long DANGEROUSLY_SLOW_PROCESSING = 5000;

  private final MorphiaIterator<T, T> iterator;

  private final StopWatch watch = new StopWatch();
  private final long slowProcessing;
  private final long dangerouslySlowProcessing;

  private long count;

  public HIterator(MorphiaIterator<T, T> iterator, long slowProcessing, long dangerouslySlowProcessing) {
    watch.start();
    count = 0;
    this.iterator = iterator;
    this.slowProcessing = slowProcessing;
    this.dangerouslySlowProcessing = dangerouslySlowProcessing;
  }

  public HIterator(MorphiaIterator<T, T> iterator) {
    this(iterator, SLOW_PROCESSING, DANGEROUSLY_SLOW_PROCESSING);
  }

  @Override
  public void close() {
    iterator.close();

    watch.stop();
    if (watch.getTime() > slowProcessing) {
      try (CollectionLogContext ignore1 = new CollectionLogContext(iterator.getCollection(), OVERRIDE_NESTS);
           ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(watch.getTime(), OVERRIDE_NESTS)) {
        if (watch.getTime() > dangerouslySlowProcessing) {
          log.error("HIterator is dangerously slow processing the data for query: {} time: {}",
              iterator.getCursor().getQuery().toString(), watch.getTime());
        } else {
          log.info("Time consuming HIterator processing. time: {}", watch.getTime());
        }
      }
    }

    if (count > 20000) {
      log.warn("Iterator query {} returns {} items for collection {}.", iterator.getCursor().getQuery().toString(),
          count, iterator.getCollection(), new Exception());
    }
  }

  @Override
  public boolean hasNext() {
    return HPersistence.retry(() -> iterator.hasNext());
  }

  @Override
  public T next() {
    try (AutoLogContext ignore = new CollectionLogContext(iterator.getCollection(), OVERRIDE_ERROR)) {
      count++;
      return HPersistence.retry(() -> iterator.next());
    }
  }

  @Override
  // This is just wrapper around the morphia iterator, it cannot be reused anyways
  @SuppressWarnings("squid:S4348")
  public Iterator<T> iterator() {
    return this;
  }
}
