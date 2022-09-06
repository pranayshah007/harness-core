package io.harness.cdng.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsBlueGreenCreateServiceStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor  {
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.ECS_BLUE_GREEN_CREATE_SERVICE.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();

    @Inject private EcsStepCommonHelper ecsStepCommonHelper;
    @Inject private EcsStepHelperImpl ecsStepHelper;

    @Override
    public TaskChainResponse executeEcsTask(Ambiance ambiance, StepElementParameters stepParameters, EcsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData, EcsStepExecutorParams ecsStepExecutorParams) {
        return null;
    }

    @Override
    public TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters, EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData, UnitProgressData unitProgressData) {
        return null;
    }

    @Override
    public Class<StepElementParameters> getStepParametersClass() {
        return StepElementParameters.class;
    }

    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
        // nothing
    }

    @Override
    public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
        log.info("Calling executeNextLink");
        return TaskChainResponse.builder()
                .chainEnd(true)
                .build();
    }

    @Override
    public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
        return null;
    }

    @Override
    public TaskChainResponse startChainLinkAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
        return ecsStepCommonHelper.startChainLink(ambiance, stepParameters, ecsStepHelper);
    }
}
