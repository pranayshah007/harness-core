/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud.model;

public enum RunStatus {
  pending,
  fetching,
  fetching_completed,
  pre_plan_running,
  pre_plan_completed,
  queuing,
  plan_queued,
  planning,
  planned,
  cost_estimating,
  cost_estimated,
  policy_checking,
  policy_override,
  policy_soft_failed,
  policy_checked,
  confirmed,
  post_plan_running,
  post_plan_completed,
  planned_and_finished,
  apply_queued,
  applying,
  applied,
  discarded,
  errored,
  canceled,
  force_canceled
}
