/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.GeneralException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
public class DownloadManifestsCommonHelper {
  @Inject private OutcomeService outcomeService;

  public ManifestsOutcome fetchManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("This");
      throw new GeneralException(format(
          "No manifests found in stage %s. %s step requires at least one manifest defined in stage service definition",
          stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  public Ambiance buildAmbianceForGitClone(Ambiance ambiance, String identifier) {
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

    Build build = new Build(BuildType.BRANCH, BranchBuildSpec.builder().branch(gitStoreConfig.getBranch()).build());

    return GitCloneStepInfo.builder()
        .cloneDirectory(ParameterField.<String>builder().value(gitManifestOutcome.getIdentifier()).build())
        .identifier(gitManifestOutcome.getIdentifier())
        .name(gitManifestOutcome.getIdentifier())
        .connectorRef(gitStoreConfig.getConnectorRef())
        .repoName(gitStoreConfig.getRepoName())
        .build(ParameterField.<Build>builder().value(build).build())
        .build();
  }

  public GitCloneStepInfo getGitCloneStepInfoFromManifestOutcomeWithOutputFilePathContents(
      ManifestOutcome gitManifestOutcome, List<String> outputFilePathContent) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) gitManifestOutcome.getStore();

    Build build = new Build(BuildType.BRANCH, BranchBuildSpec.builder().branch(gitStoreConfig.getBranch()).build());

    return GitCloneStepInfo.builder()
        .cloneDirectory(ParameterField.<String>builder().value(gitManifestOutcome.getIdentifier()).build())
        .identifier(gitManifestOutcome.getIdentifier())
        .name(gitManifestOutcome.getIdentifier())
        .connectorRef(gitStoreConfig.getConnectorRef())
        .repoName(gitStoreConfig.getRepoName())
        .outputFilePathsContent(ParameterField.<List<String>>builder().value(outputFilePathContent).build())
        .build(ParameterField.<Build>builder().value(build).build())
        .build();
  }

  public StepElementParameters getGitStepElementParameters(
      ManifestOutcome gitManifestOutcome, GitCloneStepInfo gitCloneStepInfo) {
    return StepElementParameters.builder()
        .name(gitManifestOutcome.getIdentifier())
        .spec(gitCloneStepInfo)
        .identifier(getGitCloneStepIdentifier(gitManifestOutcome))
        .build();
  }

  public GitCloneStepNode getGitCloneStepNode(
      ManifestOutcome gitManifestOutcome, GitCloneStepInfo gitCloneStepInfo, CdAbstractStepNode cdAbstractStepNode) {
    return GitCloneStepNode.builder()
        .gitCloneStepInfo(gitCloneStepInfo)
        .failureStrategies(cdAbstractStepNode.getFailureStrategies())
        .timeout(cdAbstractStepNode.getTimeout())
        .type(GitCloneStepNode.StepType.GitClone)
        .identifier(GIT_CLONE_STEP_ID + gitManifestOutcome.getIdentifier())
        .name(gitManifestOutcome.getIdentifier())
        .uuid(gitManifestOutcome.getIdentifier())
        .build();
  }

  public String getGitCloneStepIdentifier(ManifestOutcome gitManifestOutcome) {
    return GIT_CLONE_STEP_ID + gitManifestOutcome.getIdentifier();
  }
}
