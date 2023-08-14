/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.entity;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
@UtilityClass
public class FreezeConstants {
  public static final String CREATE_API_YAML = "freeze:\n"
      + "  name: Sample Freeze\n"
      + "  identifier: Sample_Freeze\n"
      + "  entityConfigs:\n"
      + "    - name: Rule 1\n"
      + "      entities:\n"
      + "        - type: Service\n"
      + "          filterType: All\n"
      + "        - type: EnvType\n"
      + "          filterType: All\n"
      + "  status: Disabled\n"
      + "  orgIdentifier: org1\n"
      + "  projectIdentifier: Project1\n"
      + "  windows:\n"
      + "    - timeZone: Asia/Calcutta\n"
      + "      startTime: 2023-02-20 11:28 AM\n"
      + "      duration: 30m";

  public static final String UPDATE_API_YAML = "freeze:\n"
      + "  name: Sample Freeze\n"
      + "  identifier: Sample_Freeze\n"
      + "  entityConfigs:\n"
      + "    - name: Rule 1\n"
      + "      entities:\n"
      + "        - type: Service\n"
      + "          filterType: All\n"
      + "        - type: EnvType\n"
      + "          filterType: All\n"
      + "  status: Disabled\n"
      + "  orgIdentifier: org1\n"
      + "  projectIdentifier: project1\n"
      + "  windows:\n"
      + "    - timeZone: Asia/Calcutta\n"
      + "      startTime: 2023-02-20 11:28 AM\n"
      + "      duration: 33m\n"
      + "  description: \"\"";
}
