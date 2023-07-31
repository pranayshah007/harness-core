/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.createFetchTask;

import static java.util.Collections.emptyMap;

import io.harness.cdng.k8s.K8sStepExecutor;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.tasks.ResponseData;

import java.util.List;

public class GitFetchManifestHandler extends ManifestHandler {
  @Override
  public TaskChainResponse prepareFetchResponseForStoreType(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<ValuesManifestOutcome> aggregatedValuesManifests =
        k8sHelmCommonStepHelper.getAggregatedValuesManifests(manifestOutcomes);
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = k8sHelmCommonStepHelper.extractStoreConfigFromManifestOutcome(k8sManifestOutcome);
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    K8sStepPassThroughData deepCopyOfK8sPassThroughData =
        k8sStepPassThroughData.toBuilder().customFetchContent(emptyMap()).zippedManifestFileId("").build();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(k8sManifestOutcome.getIdentifier()).store(storeConfig).build();
    return k8sHelmCommonStepHelper.prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters,
        valuesManifestOutcome, aggregatedValuesManifests, deepCopyOfK8sPassThroughData, storeConfig);
  }

  @Override
  public TaskChainResponse handleFetchResponseForStoreType(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    return null;
  }
}
