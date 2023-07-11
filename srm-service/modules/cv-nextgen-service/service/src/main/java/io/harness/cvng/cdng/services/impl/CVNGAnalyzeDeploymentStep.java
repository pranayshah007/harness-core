/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.annotation.RecasterAlias;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.SyncExecutableWithCapabilities;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

public class CVNGAnalyzeDeploymentStep extends SyncExecutableWithCapabilities {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CVNGStepType.CVNG_ANALYZE_DEPLOYMENT.getType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  @Value
  @Builder
  @JsonTypeName("analyzeDeploymentStepOutcome")
  @TypeAlias("analyzeDeploymentStepOutcome")
  @RecasterAlias("io.harness.cvng.cdng.services.impl.AnalyzeDeploymentStepOutcome")
  public static class AnalyzeDeploymentStepOutcome implements Outcome {
    String activityId;
  }
}
