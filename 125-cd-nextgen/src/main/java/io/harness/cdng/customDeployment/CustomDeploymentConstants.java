/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentConstants {
  public static final String TEMPLATE = "template";
  public static final String VALUE = "value";
  public static final String VARIABLES = "variables";
  public static final String INFRASTRUCTURE_DEFINITION = "infrastructureDefinition";
  public static final String FETCH_INSTANCE_SCRIPT = "Fetch Instance Script";
}
