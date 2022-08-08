/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.tasks.ResponseData;

import java.time.Duration;
import java.util.List;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateSyncService extends Runnable {
  <T extends ResponseData> T waitForTask(
      String taskId, String description, Duration timeout, List<ExecutionCapability> executionCapabilities);
}
