/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.AutoLogContext;
import io.harness.pms.execution.utils.AmbianceUtils;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor
@Slf4j
public class ExecutableProcessor {
  ExecuteStrategy executeStrategy;

  public void handleStart(InvokerPackage invokerPackage) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(invokerPackage.getAmbiance())) {
      executeStrategy.start(invokerPackage);
    }
  }

  public void handleResume(ResumePackage resumePackage) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(resumePackage.getAmbiance())) {
      executeStrategy.resume(resumePackage);
    }
  }

  public void handleProgress(ProgressPackage progressPackage) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(progressPackage.getAmbiance())) {
      executeStrategy.progress(progressPackage);
    }
  }

  public void handleAbort(InterruptPackage interruptPackage) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(interruptPackage.getAmbiance())) {
      executeStrategy.abort(interruptPackage);
    }
  }

  public void handleExpire(InterruptPackage interruptPackage) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(interruptPackage.getAmbiance())) {
      executeStrategy.expire(interruptPackage);
    }
  }

  public void handleFailure(InterruptPackage interruptPackage) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(interruptPackage.getAmbiance())) {
      executeStrategy.failure(interruptPackage);
    }
  }
}
