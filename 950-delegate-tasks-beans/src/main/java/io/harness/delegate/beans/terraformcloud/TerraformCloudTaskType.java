/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.terraformcloud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum TerraformCloudTaskType {
  VALIDATE,
  GET_ORGANIZATIONS,
  GET_WORKSPACES,
  RUN_REFRESH_STATE,
  RUN_PLAN_ONLY,
  RUN_PLAN_AND_APPLY,
  RUN_PLAN_AND_DESTROY,
  RUN_PLAN,
  RUN_APPLY,
  ROLLBACK,
  GET_LAST_APPLIED_RUN
}
