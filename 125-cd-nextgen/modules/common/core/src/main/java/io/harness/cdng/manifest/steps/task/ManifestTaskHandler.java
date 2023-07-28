/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps.task;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.tasks.ResponseData;

import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public interface ManifestTaskHandler {
  boolean isSupported(FetchManifestTaskContext context);

  Optional<TaskData> createTaskData(FetchManifestTaskContext context);

  Optional<ManifestOutcome> updateManifestOutcome(ResponseData response, ManifestOutcome manifestOutcome);
}
