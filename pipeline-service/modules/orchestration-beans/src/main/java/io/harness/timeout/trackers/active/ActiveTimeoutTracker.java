/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timeout.trackers.active;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.timeout.TimeoutEvent;
import io.harness.timeout.contracts.Dimension;
import io.harness.timeout.trackers.PausableTimeoutTracker;
import io.harness.timeout.trackers.events.StatusUpdateTimeoutEvent;

import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@TypeAlias("activeTimeoutTracker")
public class ActiveTimeoutTracker extends PausableTimeoutTracker {
  public ActiveTimeoutTracker(long timeoutMillis, boolean running) {
    super(timeoutMillis, running);
  }

  @Override
  public Dimension getDimension() {
    return ActiveTimeoutTrackerFactory.DIMENSION;
  }

  @Override
  public boolean onEvent(TimeoutEvent event) {
    if (!event.getType().equals(StatusUpdateTimeoutEvent.EVENT_TYPE)) {
      return false;
    }

    StatusUpdateTimeoutEvent statusUpdateTimeoutEvent = (StatusUpdateTimeoutEvent) event;
    Status status = statusUpdateTimeoutEvent.getStatus();
    if (StatusUtils.flowingStatuses().contains(status)) {
      // Execution is running and is not in a paused state
      if (isTicking()) {
        return false;
      } else {
        resume();
        return true;
      }
    } else {
      if (isTicking()) {
        pause();
        return true;
      } else {
        return false;
      }
    }
  }
}
