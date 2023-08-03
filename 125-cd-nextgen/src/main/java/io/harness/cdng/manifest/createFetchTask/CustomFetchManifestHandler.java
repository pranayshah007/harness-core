/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.createFetchTask;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.k8s.K8sStepExecutor;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.tasks.ResponseData;

import java.util.List;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})

public class CustomFetchManifestHandler extends K8sHelmCommonStepHelper implements ManifestHandler {
  @Override
  public TaskChainResponse prepareFetchResponseForStoreType(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifestOutcome);
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    return prepareCustomFetchManifestAndValuesTaskChainResponse(
        storeConfig, ambiance, stepElementParameters, manifestOutcomes, k8sStepPassThroughData);
  }

  @Override
  public TaskChainResponse handleFailedFetchResponseForStoreType(ResponseData responseData,
      K8sStepExecutor k8sStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData, ManifestOutcome k8sManifest) {
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        (CustomManifestValuesFetchResponse) responseData;
    CustomFetchResponsePassThroughData customFetchResponsePassThroughData =
        CustomFetchResponsePassThroughData.builder()
            .errorMsg(customManifestValuesFetchResponse.getErrorMessage())
            .unitProgressData(customManifestValuesFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().chainEnd(true).passThroughData(customFetchResponsePassThroughData).build();
  }
  @Override
  public K8sStepPassThroughData updatePassThroughData(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        (CustomManifestValuesFetchResponse) responseData;
    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder()
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .build();
    return updatedK8sStepPassThroughData;
  }
}
