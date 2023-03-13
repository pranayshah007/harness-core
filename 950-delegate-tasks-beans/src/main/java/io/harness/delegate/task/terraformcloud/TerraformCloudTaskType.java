/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@Getter
@OwnedBy(HarnessTeam.CDP)
public enum TerraformCloudTaskType {
  VALIDATE("Validate connection"),
  GET_ORGANIZATIONS("Get organizations"),
  GET_WORKSPACES("Get workspaces"),
  RUN_REFRESH_STATE("Refresh state"),
  RUN_PLAN_ONLY("Plan only"),
  RUN_PLAN_AND_APPLY("Plan and Apply"),
  RUN_PLAN_AND_DESTROY("Plan and Destroy"),
  RUN_PLAN("Plan"),
  RUN_APPLY("Apply"),
  ROLLBACK("Rollback"),
  GET_LAST_APPLIED_RUN("Get last applied run");

  private final String displayName;

  TerraformCloudTaskType(String displayName) {
    this.displayName = displayName;
  }
}
