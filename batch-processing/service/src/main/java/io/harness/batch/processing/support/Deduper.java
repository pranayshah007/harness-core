/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.support;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

public class Deduper<E> {
  // Time window for which we remember events to check a new event for duplicates.
  private static final long RECENCY_THRESHOLD = Duration.ofMinutes(1).toMillis();

  @Value(staticConstructor = "of")
  public static class Timestamped<E> implements Comparable<Timestamped<E>> {
    private static final Comparator<Timestamped> NATURAL = Comparator.comparingLong(Timestamped::getTimestampMillis);

    long timestampMillis;
    E event;

    @Override
    public int compareTo(@NotNull Timestamped<E> other) {
      return NATURAL.compare(this, other);
    }
  }

  private long lastTimestampMillis;
  private PriorityQueue<Timestamped<E>> recentEventsQ;

  public Deduper(List<Timestamped<E>> timestampedEvents) {
    this.lastTimestampMillis = 0;
    this.recentEventsQ = new PriorityQueue<>();
    timestampedEvents.forEach(this::checkEvent);
  }

  /**
   * Checks if this is a new Event
   * @param timestampedEvent event to be checked
   * @return true if this is new, false if duplicate
   */
  public boolean checkEvent(Timestamped<E> timestampedEvent) {
    if (timestampedEvent.getTimestampMillis() <= lastTimestampMillis) {
      return false;
    }
    lastTimestampMillis = timestampedEvent.getTimestampMillis();

    while (!recentEventsQ.isEmpty()
        && timestampedEvent.getTimestampMillis() - recentEventsQ.peek().getTimestampMillis() >= RECENCY_THRESHOLD) {
      recentEventsQ.poll();
    }
    if (recentEventsQ.stream().anyMatch(timestamped -> timestamped.getEvent().equals(timestampedEvent.getEvent()))) {
      return false;
    }
    recentEventsQ.add(timestampedEvent);
    return true;
  }
}
