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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public interface ExecuteStrategy {
  void start(InvokerPackage invokerPackage);

  default void resume(ResumePackage resumePackage) {
    throw new UnsupportedOperationException();
  }

  <T extends Step> T extractStep(Ambiance ambiance);

  default void progress(ProgressPackage progressPackage) {
    throw new UnsupportedOperationException();
  };

  void abort(InterruptPackage interruptPackage);

  void expire(InterruptPackage interruptPackage);

  void failure(InterruptPackage interruptPackage);
}
