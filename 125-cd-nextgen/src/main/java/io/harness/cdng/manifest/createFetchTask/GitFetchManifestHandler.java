/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.createFetchTask;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.k8s.K8sGitFetchInfo;
import io.harness.cdng.k8s.K8sGitInfo;
import io.harness.cdng.k8s.K8sStepExecutor;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.git.model.FetchFilesResult;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})

public class GitFetchManifestHandler extends K8sHelmCommonStepHelper implements ManifestHandler {
  @Override
  public TaskChainResponse prepareFetchResponseForStoreType(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<ValuesManifestOutcome> aggregatedValuesManifests = getAggregatedValuesManifests(manifestOutcomes);
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifestOutcome);
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(k8sManifestOutcome.getIdentifier()).store(storeConfig).build();
    return prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters, valuesManifestOutcome,
        aggregatedValuesManifests, k8sStepPassThroughData, storeConfig);
  }

  @Override
  public TaskChainResponse handleFailedFetchResponseForStoreType(ResponseData responseData,
      K8sStepExecutor k8sStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData, ManifestOutcome k8sManifest) {
    GitFetchResponse gitFetchResponse = (GitFetchResponse) responseData;
    GitFetchResponsePassThroughData gitFetchResponsePassThroughData =
        GitFetchResponsePassThroughData.builder()
            .errorMsg(gitFetchResponse.getErrorMessage())
            .unitProgressData(gitFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().chainEnd(true).passThroughData(gitFetchResponsePassThroughData).build();
  }

  @Override
  public K8sStepPassThroughData updatePassThroughData(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    GitFetchResponse gitFetchResponse = (GitFetchResponse) responseData;
    Map<String, FetchFilesResult> gitValuesFetchResponse = gitFetchResponse.getFilesFromMultipleRepo();
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();

    K8sGitFetchInfo k8sGitFetchInfo = K8sGitFetchInfo.builder().build();
    if (isNotEmpty(gitFetchResponse.getFetchedCommitIdsMap())) {
      Map<String, K8sGitInfo> variables = new HashMap<>();
      Map<String, String> gitFetchFilesConfigMap = gitFetchResponse.getFetchedCommitIdsMap();
      gitFetchFilesConfigMap.forEach(
          (String key, String value) -> variables.put(key, K8sGitInfo.builder().commitId(value).build()));
      k8sGitFetchInfo.putAll(variables);
    }
    K8sStepPassThroughData updatedK8sStepPassThroughData = k8sStepPassThroughData.toBuilder()
                                                               .gitValuesMapContent(gitValuesFetchResponse)
                                                               .k8sGitFetchInfo(k8sGitFetchInfo)
                                                               .build();
    updatedK8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    return updatedK8sStepPassThroughData;
  }
}
