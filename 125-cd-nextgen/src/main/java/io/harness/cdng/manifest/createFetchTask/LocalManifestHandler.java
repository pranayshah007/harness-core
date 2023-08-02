/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.createFetchTask;

import static io.harness.cdng.K8sHelmCommonStepHelper.getAggregatedValuesManifests;

import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.k8s.K8sStepExecutor;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.LogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalManifestHandler extends K8sHelmCommonStepHelper implements ManifestHandler {
  @Override
  public TaskChainResponse prepareFetchResponseForStoreType(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();
    List<ManifestFiles> manifestFiles = k8sStepPassThroughData.getManifestFiles();
    List<ValuesManifestOutcome> aggregatedValuesManifests = getAggregatedValuesManifests(manifestOutcomes);
    if (ManifestStoreType.HARNESS.equals(k8sStepPassThroughData.getManifestOutcome().getStore().getKind())
        || isAnyLocalStore(aggregatedValuesManifests)) {
      k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
      LogCallback logCallback = cdStepHelper.getLogCallback(
          K8sCommandUnitConstants.FetchFiles, ambiance, k8sStepPassThroughData.getShouldOpenFetchFilesStream());
      localStoreFileMapContents.putAll(fetchFilesFromLocalStore(
          ambiance, k8sStepPassThroughData.getManifestOutcome(), manifestOutcomes, manifestFiles, logCallback));
    }
    K8sStepPassThroughData updatedK8sStepPassThroughData = k8sStepPassThroughData.toBuilder()
                                                               .localStoreFileMapContents(localStoreFileMapContents)
                                                               .manifestFiles(manifestFiles)
                                                               .build();
    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(null)
        .passThroughData(updatedK8sStepPassThroughData)
        .build();
  }

  @Override
  public TaskChainResponse handleFetchResponseForStoreType(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    return null;
  }

  @Override
  public K8sStepPassThroughData updatePassThroughData(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    return null;
  }
}
