/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.executables;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;

import java.util.Map;

/**
 * An executable interface for Chained Async activities. This is used when you want to do Run Multiple async activities
 * in a chain
 * TaskExecutable}
 *
 * Interface Signature Details:
 *
 * startChainLink : The execution for this step start with this method. It expects as childNodeId in the response
 * based on which we spawn the child. If you set the chain end flag to true in the response we will straight away call
 * finalize execution else we will call executeNextChild.
 *
 * executeNextChild : This is the repetitive link which is repetitively called until the chainEnd boolean is set in
 * the response.
 *
 * finalizeExecution : This is where the step concludes and responds with step response.
 *
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface AsyncChainExecutable<T extends StepParameters> extends Step<T> {
  AsyncChainExecutableResponse startChainLink(Ambiance ambiance, T stepParameters, StepInputPackage inputPackage);

  AsyncChainExecutableResponse executeNextLink(Ambiance ambiance, T stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier) throws Exception;

  StepResponse finalizeExecution(Ambiance ambiance, T stepParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception;

  default void handleAbort(
      Ambiance ambiance, T stepParameters, AsyncChainExecutableResponse executableResponse, boolean userMarked) {
    // NOOP : By default this is noop as task abortion is handled by the PMS, but you are free to override it
  }

  default void handleFailure(
      Ambiance ambiance, T stepParameters, AsyncChainExecutableResponse response, Map<String, String> metadata) {
    // NOOP : By default this is noop as task failure is handled by the PMS, but you are free to override it
  }

  default void handleExpire(Ambiance ambiance, T stepParameters, AsyncChainExecutableResponse executableResponse) {
    // NOOP : By default this is noop as task expire is handled by the PMS, but you are free to override it
  }

  default ProgressData handleProgressAsyncChain(Ambiance ambiance, T stepParameters, ProgressData progressData) {
    // NOOP : By default this is noop as task expire is handled by the PMS, but you are free to override it
    return progressData;
  }
}
