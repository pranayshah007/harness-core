/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.aws.sam.DownloadManifestsStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;

  @Inject private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Inject private AwsSamPluginInfoProviderHelper awsSamPluginInfoProviderHelper;

  @Inject private DownloadManifestsStepHelper downloadManifestsStepHelper;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    return null;
  }

  @Override
  public PluginCreationResponseList getPluginInfoList(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    List<PluginCreationResponseWrapper> pluginCreationResponseWrapperList = new ArrayList<>();

    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) awsSamPluginInfoProviderHelper.getAwsSamDirectoryManifestOutcome(
            manifestsOutcome.values());

    GitCloneStepInfo gitCloneStepInfo =
        downloadManifestsStepHelper.getGitCloneStepInfoFromManifestOutcome(awsSamDirectoryManifestOutcome);

    GitCloneStepNode gitCloneStepNode = downloadManifestsStepHelper.getGitCloneStepNode(
        awsSamDirectoryManifestOutcome, gitCloneStepInfo, cdAbstractStepNode);

    PluginCreationRequest pluginCreationRequest =
        request.toBuilder().setStepJsonNode(YamlUtils.writeYamlString(gitCloneStepNode)).build();

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        gitClonePluginInfoProvider.getPluginInfo(pluginCreationRequest, usedPorts, ambiance);

    usedPorts.addAll(pluginCreationResponseWrapper.getResponse().getPluginDetails().getPortUsedList());

    pluginCreationResponseWrapperList.add(pluginCreationResponseWrapper);

    // Values Yaml

    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) awsSamPluginInfoProviderHelper.getAwsSamValuesManifestOutcome(
            manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      GitCloneStepInfo valuesGitCloneStepInfo =
          downloadManifestsStepHelper.getGitCloneStepInfoFromManifestOutcome(valuesManifestOutcome);

      GitCloneStepNode valuesGitCloneStepNode = downloadManifestsStepHelper.getGitCloneStepNode(
          valuesManifestOutcome, valuesGitCloneStepInfo, cdAbstractStepNode);

      PluginCreationRequest valuesPluginCreationRequest =
          request.toBuilder().setStepJsonNode(YamlUtils.writeYamlString(valuesGitCloneStepNode)).build();

      PluginCreationResponseWrapper valuesPluginCreationResponseWrapper =
          gitClonePluginInfoProvider.getPluginInfo(valuesPluginCreationRequest, usedPorts, ambiance);
      pluginCreationResponseWrapperList.add(valuesPluginCreationResponseWrapper);
    }

    return PluginCreationResponseList.newBuilder().addAllResponse(pluginCreationResponseWrapperList).build();
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
