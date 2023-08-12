/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
public class PermissionTypes {
  public static final String DEPLOYMENT_FREEZE_MANAGE_PERMISSION = "core_deploymentfreeze_manage";
  public static final String DEPLOYMENT_FREEZE_OVERRIDE_PERMISSION = "core_deploymentfreeze_override";
  public static final String DEPLOYMENT_FREEZE_GLOBAL_PERMISSION = "core_deploymentfreeze_global";
}
