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
import io.harness.cdng.k8s.K8sStepExecutor;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmValuesFetchResponse;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})

public class HelmFetchManifestHandler extends K8sHelmCommonStepHelper implements ManifestHandler {
  @Override
  public TaskChainResponse prepareFetchResponseForStoreType(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<ValuesManifestOutcome> aggregatedValuesManifests = getAggregatedValuesManifests(manifestOutcomes);
    return prepareHelmFetchValuesTaskChainResponse(
        ambiance, stepElementParameters, aggregatedValuesManifests, k8sStepPassThroughData);
  }

  @Override
  public TaskChainResponse handleFailedFetchResponseForStoreType(ResponseData responseData,
      K8sStepExecutor k8sStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData, ManifestOutcome k8sManifest) {
    HelmValuesFetchResponse helmValuesFetchResponse = (HelmValuesFetchResponse) responseData;
    HelmValuesFetchResponsePassThroughData helmValuesFetchPassTroughData =
        HelmValuesFetchResponsePassThroughData.builder()
            .errorMsg(helmValuesFetchResponse.getErrorMessage())
            .unitProgressData(helmValuesFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().chainEnd(true).passThroughData(helmValuesFetchPassTroughData).build();
  }

  @Override
  public K8sStepPassThroughData updatePassThroughData(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    HelmValuesFetchResponse helmValuesFetchResponse = (HelmValuesFetchResponse) responseData;
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    String k8sManifestIdentifier = k8sManifest.getIdentifier();
    String valuesFileContent = helmValuesFetchResponse.getValuesFileContent();
    Map<String, HelmFetchFileResult> helmValuesFetchFilesResultMap =
        helmValuesFetchResponse.getHelmChartValuesFileMapContent();
    if (isNotEmpty(valuesFileContent)) {
      helmValuesFetchFilesResultMap = new HashMap<>();
      helmValuesFetchFilesResultMap.put(k8sManifestIdentifier,
          HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(Arrays.asList(valuesFileContent))).build());
    }
    List<ValuesManifestOutcome> aggregatedValuesManifest = new ArrayList<>();
    aggregatedValuesManifest.addAll(k8sStepPassThroughData.getValuesManifestOutcomes());
    return k8sStepPassThroughData.toBuilder()
        .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifest))
        .helmValuesFileMapContents(helmValuesFetchFilesResultMap)
        .build();
  }
}
