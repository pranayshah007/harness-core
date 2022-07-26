/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timeout.trackers.active;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.timeout.TimeoutParameters;

import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class ActiveTimeoutParameters implements TimeoutParameters {
  @Builder.Default long timeoutMillis = TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS;
  boolean runningAtStart;
}
