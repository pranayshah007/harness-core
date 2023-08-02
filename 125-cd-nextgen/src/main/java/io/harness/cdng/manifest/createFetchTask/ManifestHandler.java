/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.createFetchTask;

import io.harness.annotations.dev.*;
import io.harness.cdng.CDStepHelper;
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
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.tasks.ResponseData;

import software.wings.beans.SettingAttribute;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public interface ManifestHandler {
  TaskChainResponse prepareFetchResponseForStoreType(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> kustomizePatchesManifests, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData);
  TaskChainResponse handleFetchResponseForStoreType(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest);

  K8sStepPassThroughData updatePassThroughData(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest);
}
