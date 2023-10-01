/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.executables.Progressable;
import io.harness.tasks.ProgressData;

import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class ProgressableStrategy implements ExecuteStrategy {
  @Inject protected SdkNodeExecutionService sdkNodeExecutionService;

  @Override
  public void progress(ProgressPackage progressPackage) {
    Ambiance ambiance = progressPackage.getAmbiance();
    Step<?> step = extractStep(ambiance);
    if (step instanceof Progressable) {
      ProgressData progressData = ((Progressable) step)
                                      .handleProgress(progressPackage.getAmbiance(),
                                          progressPackage.getStepParameters(), progressPackage.getProgressData());
      if (progressData != null) {
        sdkNodeExecutionService.handleProgressResponse(ambiance, progressData);
      }
      return;
    }
    throw new UnsupportedOperationException("Progress Update not supported for strategy: " + this.getClass().getName());
  }
}
