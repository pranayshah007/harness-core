/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapper;
import io.harness.cdng.artifact.steps.beans.ArtifactStepParameters;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
public class ArtifactSyncStep implements SyncExecutable<ArtifactStepParameters> {
  @Inject private ArtifactStepHelper artifactStepHelper;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public StepResponse executeSync(Ambiance ambiance, ArtifactStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ArtifactConfig finalArtifact = artifactStepHelper.applyArtifactsOverlay(stepParameters);
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (finalArtifact.isPrimaryArtifact()) {
      logCallback.saveExecutionLog("Processing primary artifact...");
      logCallback.saveExecutionLog(String.format(
          "Primary artifact info: %s", ArtifactUtils.getLogInfo(finalArtifact, finalArtifact.getSourceType())));
    } else {
      logCallback.saveExecutionLog(String.format("Processing sidecar artifact [%s]...", finalArtifact.getIdentifier()));
      logCallback.saveExecutionLog(String.format("Sidecar artifact [%s] info: %s", finalArtifact.getIdentifier(),
          ArtifactUtils.getLogInfo(finalArtifact, finalArtifact.getSourceType())));
    }

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name("output")
                         .outcome(ArtifactResponseToOutcomeMapper.toArtifactOutcome(finalArtifact, null, false))
                         .build())
        .build();
  }

  @Override
  public Class<ArtifactStepParameters> getStepParametersClass() {
    return ArtifactStepParameters.class;
  }
}
