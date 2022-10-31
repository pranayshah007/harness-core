/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.service;

public interface DelegateAgentService {
  void run(boolean watched, boolean isServer);

  void pause();

  void stop();

  void freeze();

  boolean isHeartbeatHealthy();

  boolean isSocketHealthy();

  void shutdown(boolean shouldUnregister) throws InterruptedException;

  void recordMetrics();
}
