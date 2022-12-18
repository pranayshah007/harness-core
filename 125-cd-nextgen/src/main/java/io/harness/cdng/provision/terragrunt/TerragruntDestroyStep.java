/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.terragrunt.response.TerragruntDestroyTaskResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerragruntDestroyStep extends TaskExecutableWithRollbackAndRbac<TerragruntDestroyTaskResponse> {
  public static final StepType STEP_TYPE =
      TerragruntStepHelper.addStepType(ExecutionNodeType.TERRAGRUNT_DESTROY.getYamlType());

  @Inject private TerragruntStepHelper terragruntStepHelper;

  @Override
  public Class getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    terragruntStepHelper.checkIfTerragruntFeatureIsEnabled(ambiance, "Terragrunt Destroy");
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<TerragruntDestroyTaskResponse> responseDataSupplier) throws Exception {
    // to be added destroy handle task logic
    return StepResponse.builder().build();
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    // to be added destroy obtain task logic
    return TaskRequest.newBuilder().build();
  }
}
