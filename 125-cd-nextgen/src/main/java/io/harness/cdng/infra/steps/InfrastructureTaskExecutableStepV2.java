package io.harness.cdng.infra.steps;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;

public class InfrastructureTaskExecutableStepV2
    implements TaskExecutableWithRbac<InfrastructureTaskExecutableStepV2Params, DelegateResponseData> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.INFRASTRUCTURE_TASKSTEP_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Override
  public Class<InfrastructureTaskExecutableStepV2Params> getStepParametersClass() {
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters) {}

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      InfrastructureTaskExecutableStepV2Params stepParameters,
      ThrowingSupplier<DelegateResponseData> responseDataSupplier) throws Exception {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters, StepInputPackage inputPackage) {
    return null;
  }
}
