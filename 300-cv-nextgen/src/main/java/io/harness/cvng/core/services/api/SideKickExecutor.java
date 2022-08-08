/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.entities.SideKick.SideKickData;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

public interface SideKickExecutor<E extends SideKickData> {
  void execute(E sideKickInfo);

  default boolean canExecute(E sideKickInfo) {
    return true;
  }

  default Duration delayExecutionBy() {
    return Duration.ZERO;
  }

  @Value
  @Builder
  class RetryData {
    boolean shouldRetry;
    Instant nextRetryTime;
  }

  default RetryData shouldRetry(int lastRetryCount) {
    return RetryData.builder().shouldRetry(false).build();
  }
}
