/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps.task;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.tasks.ResponseData;

import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public interface ManifestTaskService {
  boolean isSupported(Ambiance ambiance, ManifestOutcome manifest);

  Optional<TaskData> createTaskData(Ambiance ambiance, ManifestOutcome manifest);

  void handleTaskResponses(
      Map<String, ResponseData> responseDataMap, ManifestsOutcome manifests, Map<String, String> taskIdMapping);
}
