package io.harness.cdng.googlefunctions;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsStepCommonHelper;
import io.harness.cdng.ecs.EcsStepExecutor;
import io.harness.cdng.ecs.EcsStepHelperImpl;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionsExecutionPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionsPrepareRollbackPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionsStepExecutorParams;
import io.harness.cdng.instance.info.InstanceInfoService;
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
public class GoogleFunctionsDeployStep extends TaskChainExecutableWithRollbackAndRbac implements GoogleFunctionsStepExecutor {
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.GOOGLE_CLOUD_FUNCTIONS_DEPLOY.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();

    private final String Google_Functions_DEPLOY_COMMAND_NAME = "DeployCloudFunction";
    private final String Google_Functions_PREPARE_ROLLBACK_COMMAND_NAME = "PrepareRollbackCloudFunction";


    @Inject private InstanceInfoService instanceInfoService;

    @Override
    public TaskChainResponse executeTask(Ambiance ambiance, StepElementParameters stepParameters, GoogleFunctionsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData, GoogleFunctionsStepExecutorParams googleFunctionsStepExecutorParams) {
        return null;
    }

    @Override
    public TaskChainResponse executePrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters, GoogleFunctionsPrepareRollbackPassThroughData googleFunctionsPrepareRollbackPassThroughData, UnitProgressData unitProgressData) {
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
        return null;
    }

    @Override
    public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
        return null;
    }

    @Override
    public TaskChainResponse startChainLinkAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
        return null;
    }
}
