/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core;

import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.ProgressCallback;

public class TestAsyncWaitEngineImpl implements AsyncWaitEngine {
  @Override
  public void waitForAllOn(NotifyCallback notifyCallback, ProgressCallback progressCallback, String... correlationIds) {
    // Do nothing
  }
}
