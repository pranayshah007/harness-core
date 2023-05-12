package io.harness.cdng.aws.sam;

import com.google.inject.Inject;
import graphql.execution.AsyncExecutionStrategy;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.GitCloneStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import java.util.Map;

public class DownloadManifestsStep implements AsyncExecutableWithRbac<StepElementParameters> {

    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.DOWNLOAD_MANIFESTS.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();

    @Inject
    GitCloneStep gitCloneStep;

    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {

    }

    @Override
    public AsyncExecutableResponse executeAsyncAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
        Build build = Build.builder()
                .spec(BranchBuildSpec.builder()
                        .branch(ParameterField.<String>builder().value("main").build())
                        .build())
                .type(BuildType.BRANCH)
                .build();

        GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder()
                .cloneDirectory(ParameterField.<String>builder().value("m1").build())
                .identifier("m1")
                .name("m1")
                .connectorRef(ParameterField.<String>builder().value("Sainath_Github").build())
                .repoName(ParameterField.<String>builder().value("Sainath-Test").build())
                .build(ParameterField.<Build>builder().value(build).build())
                .build();

        StepElementParameters stepElementParameters = StepElementParameters.builder()
                .name("m1")
                .spec(gitCloneStepInfo)
                .build();

        return gitCloneStep.executeAsyncAfterRbac(ambiance, stepElementParameters, inputPackage);
    }

    @Override
    public Class<StepElementParameters> getStepParametersClass() {
        return StepElementParameters.class;
    }

    @Override
    public void handleAbort(Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {

    }

    @Override
    public StepResponse handleAsyncResponse(Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
        return null;
    }
}
