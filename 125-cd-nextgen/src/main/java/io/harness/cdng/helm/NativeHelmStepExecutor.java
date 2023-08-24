/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm;

import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;

import java.util.List;

public interface NativeHelmStepExecutor {
  TaskChainResponse executeHelmTask(ManifestOutcome HelmChartManifestOutcome, Ambiance ambiance,
      StepBaseParameters stepParameters, List<String> valuesFileContents,
      NativeHelmExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData);
}
