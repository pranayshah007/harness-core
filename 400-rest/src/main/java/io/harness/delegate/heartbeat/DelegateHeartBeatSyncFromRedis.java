/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import com.google.inject.Inject;
import io.harness.service.intfc.DelegateCache;

public class DelegateHeartBeatSyncFromRedis implements Runnable {
    @Inject
    private DelegateCache delegateCache;

  @Override
  public void run() {

  }
}
