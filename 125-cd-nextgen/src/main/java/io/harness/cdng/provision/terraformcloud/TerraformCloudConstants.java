/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public final class TerraformCloudConstants {
  static final String REFRESH_STATE = "RefreshState";
  static final String PLAN_ONLY = "PlanOnly";
  static final String PLAN_AND_APPLY = "PlanAndApply";
  static final String PLAN_AND_DESTROY = "PlanAndDestroy";
  static final String PLAN = "Plan";
  static final String APPLY = "Apply";
  static final String DESTROY = "Destroy";

  public static final String DEFAULT_TIMEOUT = "10m";

  static final String TFC_PLAN_NAME_PREFIX_NG = "tfcPlan_%s_%s";
  static final String TFC_DESTROY_PLAN_NAME_PREFIX_NG = "tfcDestroyPlan_%s_%s";

  private TerraformCloudConstants() {
    throw new UnsupportedOperationException();
  }
}
