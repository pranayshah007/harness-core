/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum RunStatus {
  @JsonProperty("pending") PENDING,
  @JsonProperty("fetching") FETCHING,
  @JsonProperty("fetching_completed") FETCHING_COMPLETED,
  @JsonProperty("pre_plan_running") PRE_PLAN_RUNNING,
  @JsonProperty("pre_plan_completed") PRE_PLAN_COMPLETED,
  @JsonProperty("queuing") QUEUING,
  @JsonProperty("plan_queued") PLAN_QUEUED,
  @JsonProperty("planning") PLANNING,
  @JsonProperty("planned") PLANNED,
  @JsonProperty("cost_estimating") COST_ESTIMATING,
  @JsonProperty("cost_estimated") COST_ESTIMATED,
  @JsonProperty("policy_checking") POLICY_CHECKING,
  @JsonProperty("policy_override") POLICY_OVERRIDE,
  @JsonProperty("policy_soft_failed") POLICY_SOFT_FAILED,
  @JsonProperty("policy_checked") POLICY_CHECKED,
  @JsonProperty("confirmed") CONFIRMED,
  @JsonProperty("post_plan_running") POST_PLAN_RUNNING,
  @JsonProperty("post_plan_completed") POST_PLAN_COMPLETED,
  @JsonProperty("planned_and_finished") PLANNED_AND_FINISHED,
  @JsonProperty("apply_queued") APPLY_QUEUED,
  @JsonProperty("applying") APPLYING,
  @JsonProperty("applied") APPLIED,
  @JsonProperty("discarded") DISCARDED,
  @JsonProperty("errored") ERRORED,
  @JsonProperty("canceled") CANCELED,
  @JsonProperty("force_canceled") FORCE_CANCELED,
  @JsonEnumDefaultValue UNKNOWN
}
