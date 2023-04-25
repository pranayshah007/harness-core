package io.harness.cdng.aws.sam;

import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import java.util.Map;

public class DownloadManifestsStep extends ChildrenExecutableWithRollbackAndRbac<DownloadManifestsStepParameters> {

    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.DOWNLOAD_MANIFESTS.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();
    @Override
    public Class<DownloadManifestsStepParameters> getStepParametersClass() {
        return DownloadManifestsStepParameters.class;
    }

    @Override
    public void validateResources(Ambiance ambiance, DownloadManifestsStepParameters stepParameters) {

    }

    @Override
    public ChildrenExecutableResponse obtainChildrenAfterRbac(Ambiance ambiance, DownloadManifestsStepParameters stepParameters, StepInputPackage inputPackage) {
        return null;
    }

    @Override
    public StepResponse handleChildrenResponseInternal(Ambiance ambiance, DownloadManifestsStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
        return null;
    }
}
