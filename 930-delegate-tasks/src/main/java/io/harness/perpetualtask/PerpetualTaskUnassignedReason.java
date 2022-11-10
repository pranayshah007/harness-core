/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

public enum PerpetualTaskUnassignedReason {
  NO_DELEGATE_INSTALLED,
  NO_DELEGATE_AVAILABLE,
  NO_ELIGIBLE_DELEGATES, // No eligible delegates to process delegate sync task
  MULTIPLE_FAILED_PERPETUAL_TASK, // task failure at delegate
  VALIDATION_TASK_FAILED, // deprecated
  TASK_EXPIRED, // sync task expired
  TASK_VALIDATION_FAILED, // sync task validation failed
  PT_TASK_FAILED, // PT task failed due unexpected reasons
}
