package io.harness.cdng.tas;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.exception.ExceptionUtils;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasAppResizeStep extends TaskExecutableWithRollbackAndRbac<CfCommandResponseNG> {
    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters){


    }
    @Override
    public Class<StepElementParameters> getStepParametersClass() {
        return StepElementParameters.class;
    }
    @Override
    public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
                                                            ThrowingSupplier<CfCommandResponseNG> responseDataSupplier) throws Exception {
        StepResponse.StepResponseBuilder builder = StepResponse.builder();

        CfCommandResponseNG response;
        try {
            response = responseDataSupplier.get();
        } catch (Exception ex) {
            log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
            throw ex;
        }
        builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
        builder.status(Status.SUCCEEDED);
        return builder.build();
    }
    @Override
    public TaskRequest obtainTaskAfterRbac(
            Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {












    }
}
