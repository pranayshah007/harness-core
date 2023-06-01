/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.services.ConnectorService;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.*;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import java.io.IOException;
import java.util.*;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;

  @Inject private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Inject private AwsSamPluginInfoProviderHelper awsSamPluginInfoProviderHelper;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public PluginCreationResponseList getPluginInfoList(PluginCreationRequest request, Set<Integer> usedPorts) {

    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
              String.format("Error in parsing CI step for step type [%s]", request.getType()), e);
    }

    Ambiance ambiance = request.getAmbiance();

    List<PluginCreationResponseWrapper> pluginCreationResponseWrapperList = new ArrayList<>();

    ManifestsOutcome manifestsOutcome = (ManifestsOutcome) outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS)).getOutcome();

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = (AwsSamDirectoryManifestOutcome) awsSamPluginInfoProviderHelper.getAwsSamDirectoryManifestOutcome(manifestsOutcome.values());

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

    GitCloneStepNode gitCloneStepNode = GitCloneStepNode.builder()
            .gitCloneStepInfo(gitCloneStepInfo)
            .failureStrategies(cdAbstractStepNode.getFailureStrategies())
            .timeout(cdAbstractStepNode.getTimeout())
            .type(GitCloneStepNode.StepType.GitClone)
            .identifier(GIT_CLONE_STEP_ID+awsSamDirectoryManifestOutcome.getIdentifier())
            .name(awsSamDirectoryManifestOutcome.getIdentifier())
            .uuid(awsSamDirectoryManifestOutcome.getIdentifier())
            .build();

    PluginCreationRequest pluginCreationRequest = request.toBuilder().setStepJsonNode(YamlUtils.write(gitCloneStepNode))
            .build();

    PluginCreationResponseWrapper pluginCreationResponseWrapper = gitClonePluginInfoProvider.getPluginInfo(pluginCreationRequest, new HashSet<>(pluginCreationRequest.getUsedPortDetails().getUsedPortsList()));

    pluginCreationResponseWrapperList.add(pluginCreationResponseWrapper);

    // Values Yaml

    ValuesManifestOutcome valuesManifestOutcome = (ValuesManifestOutcome) awsSamPluginInfoProviderHelper.getAwsSamValuesManifestOutcome(manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      GitStoreConfig valuesGitStoreConfig = (GitStoreConfig) valuesManifestOutcome.getStore();

      Build valuesBuild = Build.builder()
              .spec(BranchBuildSpec.builder()
                      .branch(valuesGitStoreConfig.getBranch())
                      .build())
              .type(BuildType.BRANCH)
              .build();

      GitCloneStepInfo valuesGitCloneStepInfo = GitCloneStepInfo.builder()
              .cloneDirectory(ParameterField.<String>builder().value(valuesManifestOutcome.getIdentifier()).build())
              .identifier(valuesManifestOutcome.getIdentifier())
              .name(valuesManifestOutcome.getIdentifier())
              .connectorRef(valuesGitStoreConfig.getConnectorRef())
              .repoName(valuesGitStoreConfig.getRepoName())
              .build(ParameterField.<Build>builder().value(valuesBuild).build())
              .outputFilePathsContent(ParameterField.<List<String>>builder().value(Arrays.asList(awsSamPluginInfoProviderHelper.getValuesPathFromValuesManifestOutcome(valuesManifestOutcome))).build())
              .build();

      GitCloneStepNode valuesGitCloneStepNode = GitCloneStepNode.builder()
              .gitCloneStepInfo(valuesGitCloneStepInfo)
              .failureStrategies(cdAbstractStepNode.getFailureStrategies())
              .timeout(cdAbstractStepNode.getTimeout())
              .type(GitCloneStepNode.StepType.GitClone)
              .identifier(GIT_CLONE_STEP_ID + valuesManifestOutcome.getIdentifier())
              .name(valuesManifestOutcome.getIdentifier())
              .uuid(valuesManifestOutcome.getIdentifier())
              .build();

      PluginCreationRequest valuesPluginCreationRequest = request.toBuilder().setStepJsonNode(YamlUtils.write(valuesGitCloneStepNode))
              .build();

      PluginCreationResponseWrapper valuesPluginCreationResponseWrapper = gitClonePluginInfoProvider.getPluginInfo(valuesPluginCreationRequest, new HashSet<>(valuesPluginCreationRequest.getUsedPortDetails().getUsedPortsList()));
      pluginCreationResponseWrapperList.add(valuesPluginCreationResponseWrapper);
    }


    return PluginCreationResponseList.newBuilder()
            .addAllResponse(pluginCreationResponseWrapperList)
            .build();
  }


  @Override
  public PluginCreationResponseWrapper getPluginInfo(PluginCreationRequest request, Set<Integer> usedPorts) {
    return null;
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.DOWNLOAD_MANIFESTS)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean willReturnMultipleContainers() {
    return true;
  }

}
