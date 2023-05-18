package io.harness.cdng.aws.sam;

import com.google.inject.Inject;
import graphql.execution.AsyncExecutionStrategy;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.GitCloneStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DownloadManifestsStep implements AsyncExecutableWithRbac<StepElementParameters> {

    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.DOWNLOAD_MANIFESTS.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();

    @Inject
    GitCloneStep gitCloneStep;

    @Inject private OutcomeService outcomeService;

    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {

    }

    @Override
    public AsyncExecutableResponse executeAsyncAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {

        ManifestsOutcome manifestsOutcome = (ManifestsOutcome) outcomeService.resolveOptional(
                ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS)).getOutcome();

        AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = (AwsSamDirectoryManifestOutcome) getAwsSamDirectoryManifestOutcome(manifestsOutcome.values());

        GitStoreConfig gitStoreConfig = (GitStoreConfig) awsSamDirectoryManifestOutcome.getStore();

        Build build = Build.builder()
                .spec(BranchBuildSpec.builder()
                        .branch(gitStoreConfig.getBranch())
                        .build())
                .type(BuildType.BRANCH)
                .build();

        GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder()
                .cloneDirectory(ParameterField.<String>builder().value(awsSamDirectoryManifestOutcome.getIdentifier()).build())
                .identifier(awsSamDirectoryManifestOutcome.getIdentifier())
                .name(awsSamDirectoryManifestOutcome.getIdentifier())
                .connectorRef(gitStoreConfig.getConnectorRef())
                .repoName(gitStoreConfig.getRepoName())
                .build(ParameterField.<Build>builder().value(build).build())
                .build();

        StepElementParameters stepElementParameters = StepElementParameters.builder()
                .name(awsSamDirectoryManifestOutcome.getIdentifier())
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
        return StepResponse.builder()
                .status(Status.SUCCEEDED)
                .build();
    }

    public ManifestOutcome getAwsSamDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
        List<ManifestOutcome> manifestOutcomeList =
                manifestOutcomes.stream()
                        .filter(manifestOutcome -> ManifestType.AwsSamDirectory.equals(manifestOutcome.getType()))
                        .collect(Collectors.toList());
        return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
    }
}
