/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.executionplan;

import lombok.Getter;

public enum CDPlanCreatorType {
  CD_EXECUTION_PLAN_CREATOR("CD_EXECUTION_PLAN_CREATOR"),
  CD_STEP_PLAN_CREATOR("CD_STEP_PLAN_CREATOR"),
  SERVICE_PLAN_CREATOR("SERVICE_PLAN_CREATOR"),
  INFRA_PLAN_CREATOR("INFRA_PLAN_CREATOR"),
  ROLLBACK_PLAN_CREATOR("ROLLBACK_PLAN_CREATOR"),
  STEP_GROUPS_ROLLBACK_PLAN_CREATOR("STEP_GROUPS_ROLLBACK_PLAN_CREATOR"),
  STEP_GROUP_ROLLBACK_PLAN_CREATOR("STEP_GROUP_ROLLBACK_PLAN_CREATOR"),
  EXECUTION_ROLLBACK_PLAN_CREATOR("EXECUTION_ROLLBACK_PLAN_CREATOR"),
  PARALLEL_STEP_GROUP_ROLLBACK_PLAN_CREATOR("PARALLEL_STEP_GROUP_ROLLBACK_PLAN_CREATOR");

  @Getter private final String name;

  CDPlanCreatorType(String name) {
    this.name = name;
  }
}
