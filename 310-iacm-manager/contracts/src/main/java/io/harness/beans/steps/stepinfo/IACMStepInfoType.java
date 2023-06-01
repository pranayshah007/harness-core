/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public enum IACMStepInfoType {
  IACM_TERRAFORM_PLUGIN(IACMStepExecEnvironment.CI_LITE_ENGINE, "IACMTerraformPlugin"),
  IACM_TEMPLATE(IACMStepExecEnvironment.CI_LITE_ENGINE, "IACMTemplate");

  @Getter private final IACMStepExecEnvironment ciStepExecEnvironment;
  private final String displayName;

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  IACMStepInfoType(IACMStepExecEnvironment iacmStepExecEnvironment, String displayName) {
    this.ciStepExecEnvironment = iacmStepExecEnvironment;
    this.displayName = displayName;
  }
  public enum IACMStepExecEnvironment { CI_MANAGER, CI_LITE_ENGINE }
}
