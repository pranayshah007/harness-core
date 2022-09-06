package io.harness.cdng.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsBlueGreenRollbackStep extends TaskExecutableWithRollbackAndRbac<EcsCommandResponse> {
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.ECS_BLUE_GREEN_ROLLBACK.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();
    @Override
    public Class<StepElementParameters> getStepParametersClass() {
        return StepElementParameters.class;
    }

    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
        // Nothing to validate
    }

    @Override
    public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters, ThrowingSupplier<EcsCommandResponse> responseDataSupplier) throws Exception {
        return null;
    }

    @Override
    public TaskRequest obtainTaskAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
        return null;
    }
}
