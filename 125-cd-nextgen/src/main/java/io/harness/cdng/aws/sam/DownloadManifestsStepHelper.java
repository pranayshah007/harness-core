/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
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
    return (ManifestsOutcome) outcomeService
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
        .getOutcome();
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

  public String getValuesPathFromValuesManifestOutcome(ValuesManifestOutcome valuesManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) valuesManifestOutcome.getStore();
    return "/harness/" + valuesManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
  }

  public String getGitCloneStepIdentifier(ManifestOutcome gitManifestOutcome) {
    return GIT_CLONE_STEP_ID + gitManifestOutcome.getIdentifier();
  }
}
