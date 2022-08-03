/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timeout.trackers.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;
import io.harness.timeout.TimeoutEvent;

import lombok.AllArgsConstructor;
import lombok.Value;

@OwnedBy(CDC)
@Value
@AllArgsConstructor
public class StatusUpdateTimeoutEvent implements TimeoutEvent {
  public static final String EVENT_TYPE = "STATUS_UPDATE";

  Status status;

  @Override
  public String getType() {
    return EVENT_TYPE;
  }
}
