/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.steps.shellscript.v1.ExportScope")
public enum ExportScope {
  @JsonProperty(ExportScopeConstants.PIPELINE) PIPELINE(ExportScopeConstants.PIPELINE),
  @JsonProperty(ExportScopeConstants.STAGE) STAGE(ExportScopeConstants.STAGE),
  @JsonProperty(ExportScopeConstants.STEP_GROUP) STEP_GROUP(ExportScopeConstants.STEP_GROUP);

  private final String displayName;

  ExportScope(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public String toStepOutcomeGroup() {
    switch (this) {
      case PIPELINE:
        return StepOutcomeGroup.PIPELINE.name();
      case STAGE:
        return StepOutcomeGroup.STAGE.name();
      case STEP_GROUP:
        return StepOutcomeGroup.STEP_GROUP.name();
      default:
        throw new InvalidRequestException(String.format("Unsupported output alias scope value : %s", this.displayName));
    }
  }
}
