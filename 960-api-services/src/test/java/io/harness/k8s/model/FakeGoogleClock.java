/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import com.google.api.client.util.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * To simulate passage of time in tests.
 */
public class FakeGoogleClock implements Clock {
  private final AtomicLong now;

  public FakeGoogleClock(Instant now) {
    this.now = new AtomicLong(now.toEpochMilli());
  }

  public Instant advance(Duration duration) {
    return Instant.ofEpochMilli(now.addAndGet(duration.toMillis()));
  }

  public Instant windBack(Duration duration) {
    return Instant.ofEpochMilli(now.addAndGet(-duration.toMillis()));
  }

  public void setNow(Instant newNow) {
    now.set(newNow.toEpochMilli());
  }

  @Override
  public long currentTimeMillis() {
    return now.get();
  }
}
