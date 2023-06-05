package io.harness.cdng.aws.sam;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsStepHelper {
  @Inject private OutcomeService outcomeService;

  public ManifestsOutcome fetchManifestsOutcome(Ambiance ambiance) {
    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();
    return manifestsOutcome;
  }
  public ManifestOutcome getAwsSamDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.AwsSamDirectory.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public ManifestOutcome getAwsSamValuesManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  // Todo(Sahil): Come up with a better way to pass information to child
  Ambiance buildAmbianceForGitClone(Ambiance ambiance, String identifier) {
    Level level = Level.newBuilder()
                      .setIdentifier(identifier)
                      .setSkipExpressionChain(true)
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .build();
    return AmbianceUtils.cloneForChild(ambiance, level);
  }

  public GitCloneStepInfo getGitCloneStepInfoFromManifestOutcome(ManifestOutcome gitManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) gitManifestOutcome.getStore();

    Build build = Build.builder()
                      .spec(BranchBuildSpec.builder().branch(gitStoreConfig.getBranch()).build())
                      .type(BuildType.BRANCH)
                      .build();

    GitCloneStepInfo gitCloneStepInfo =
        GitCloneStepInfo.builder()
            .cloneDirectory(ParameterField.<String>builder().value(gitManifestOutcome.getIdentifier()).build())
            .identifier(gitManifestOutcome.getIdentifier())
            .name(gitManifestOutcome.getIdentifier())
            .connectorRef(gitStoreConfig.getConnectorRef())
            .repoName(gitStoreConfig.getRepoName())
            .build(ParameterField.<Build>builder().value(build).build())
            .build();

    return gitCloneStepInfo;
  }

  public StepElementParameters getGitStepElementParameters(
      ManifestOutcome gitManifestOutcome, GitCloneStepInfo gitCloneStepInfo) {
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .name(gitManifestOutcome.getIdentifier())
                                                      .spec(gitCloneStepInfo)
                                                      .identifier(getGitCloneStepIdentifier(gitManifestOutcome))
                                                      .build();
    return stepElementParameters;
  }

  public GitCloneStepNode getGitCloneStepNode(
      ManifestOutcome gitManifestOutcome, GitCloneStepInfo gitCloneStepInfo, CdAbstractStepNode cdAbstractStepNode) {
    GitCloneStepNode gitCloneStepNode = GitCloneStepNode.builder()
                                            .gitCloneStepInfo(gitCloneStepInfo)
                                            .failureStrategies(cdAbstractStepNode.getFailureStrategies())
                                            .timeout(cdAbstractStepNode.getTimeout())
                                            .type(GitCloneStepNode.StepType.GitClone)
                                            .identifier(GIT_CLONE_STEP_ID + gitManifestOutcome.getIdentifier())
                                            .name(gitManifestOutcome.getIdentifier())
                                            .uuid(gitManifestOutcome.getIdentifier())
                                            .build();
    return gitCloneStepNode;
  }

  public String getGitCloneStepIdentifier(ManifestOutcome gitManifestOutcome) {
    return GIT_CLONE_STEP_ID + gitManifestOutcome.getIdentifier();
  }
}
