package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
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
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters, StepInputPackage inputPackage) {
    throw new UnsupportedOperationException("todo");
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      InfrastructureTaskExecutableStepV2Params stepParameters,
      ThrowingSupplier<DelegateResponseData> responseDataSupplier) throws Exception {
    throw new UnsupportedOperationException("todo");
  }
}
