package io.harness.cdng.tas;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.exception.ExceptionUtils;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasRollbackStep extends TaskExecutableWithRollbackAndRbac<CfCommandResponseNG> {
    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

    @Override
    public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
                                                            StepElementParameters stepElementParameters,
                                                            ThrowingSupplier<CfCommandResponseNG> responseDataSupplier)
            throws Exception {
        StepResponse stepResponse = null;
        try {
            CfRollbackCommandResponseNG tasRollbackCommandResponseNG = (CfRollbackCommandResponseNG) responseDataSupplier.get();
            StepResponse.StepResponseBuilder stepResponseBuilder =
                    StepResponse.builder().unitProgressList(tasRollbackCommandResponseNG.getUnitProgressData().getUnitProgresses());

            stepResponse = generateStepResponse(ambiance, tasRollbackCommandResponseNG, stepResponseBuilder);
        } catch (Exception e) {
            log.error("Error while processing ecs rolling rollback response: {}", ExceptionUtils.getMessage(e), e);
            throw e;
        }
        return stepResponse;
    }

    private StepResponse generateStepResponse(Ambiance ambiance,
                                              CfRollbackCommandResponseNG tasRollbackCommandResponseNG,
                                              StepResponse.StepResponseBuilder stepResponseBuilder) {



    }


    @Override
    public Class<StepElementParameters> getStepParametersClass() {
        return StepElementParameters.class;
    }

}
