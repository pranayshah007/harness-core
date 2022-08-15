/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import java.time.Instant;

public interface PerpetualTaskExecutor {
  String SUCCESS_RESPONSE_MSG = "success";

  // Specify what should be done in a single iteration of the task.
  PerpetualTaskResponse runOnce(PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime);

  // Cleanup any state that's maintained for a  task.
  boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params);
}
