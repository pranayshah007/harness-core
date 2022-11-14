/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

public enum PerpetualTaskState {
  TASK_UNASSIGNED,
  TASK_TO_REBALANCE,
  TASK_PAUSED,
  TASK_ASSIGNED,

  // Keep just for backward compatibility with the database.
  // Never use the logic is already changed to assume these are not in use.
  @Deprecated NO_DELEGATE_INSTALLED,
  @Deprecated NO_DELEGATE_AVAILABLE,
  @Deprecated NO_ELIGIBLE_DELEGATES,
  @Deprecated TASK_RUN_SUCCEEDED,
  @Deprecated TASK_RUN_FAILED,

  TASK_NON_ASSIGNABLE,
  TASK_INVALID
}
