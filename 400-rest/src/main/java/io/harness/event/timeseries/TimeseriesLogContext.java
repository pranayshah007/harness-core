/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries;

import io.harness.logging.logcontext.AutoLogContext;

import java.util.UUID;

public class TimeseriesLogContext extends AutoLogContext {
  private static final String ID_KEY = "timeseries_id";

  public TimeseriesLogContext(OverrideBehavior behavior) {
    super(ID_KEY, UUID.randomUUID().toString(), behavior);
  }
}
