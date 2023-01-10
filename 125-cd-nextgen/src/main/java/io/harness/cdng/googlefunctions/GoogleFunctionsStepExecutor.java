package io.harness.cdng.googlefunctions;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionsExecutionPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionsPrepareRollbackPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionsStepExecutorParams;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
public interface GoogleFunctionsStepExecutor {
    TaskChainResponse executeTask(Ambiance ambiance, StepElementParameters stepParameters,
                                  GoogleFunctionsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
                                  GoogleFunctionsStepExecutorParams googleFunctionsStepExecutorParams);

    TaskChainResponse executePrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
                                                 GoogleFunctionsPrepareRollbackPassThroughData
                                                 googleFunctionsPrepareRollbackPassThroughData,
                                                 UnitProgressData unitProgressData);
}
