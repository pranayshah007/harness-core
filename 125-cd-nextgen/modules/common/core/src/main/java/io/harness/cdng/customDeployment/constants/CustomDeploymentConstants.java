/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment.constants;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentConstants {
  public static final String TEMPLATE = "template";
  public static final String VALUE = "value";
  public static final String VARIABLES = "variables";
  public static final String INFRASTRUCTURE_DEFINITION = "infrastructureDefinition";
  public static final String FETCH_INSTANCE_SCRIPT = "Fetch Instance Script";
  public static final String STABLE_VERSION = "__STABLE__";
}
