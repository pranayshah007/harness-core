/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.executable.AsyncExecutableWithCapabilities;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public class CdAsyncExecutable<T extends CdTaskExecutable<ResponseData>> extends AsyncExecutableWithCapabilities {
  @Inject protected T cdTaskExecutable;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private AsyncExecutableTaskHelper asyncExecutableTaskHelper;
  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // toDo
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    cdTaskExecutable.validateResources(ambiance, stepParameters);
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    // toDo
    return null;
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return cdTaskExecutable.getStepParametersClass();
  }
}
