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
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ManifestTaskServiceImpl implements ManifestTaskService {
  @Inject private Map<String, ManifestTaskHandler> manifestTaskHandlers;

  @Override
  public boolean isSupported(FetchManifestTaskContext context) {
    Optional<ManifestTaskHandler> manifestTaskHandler = getManifestTaskHandler(context.getType());
    return manifestTaskHandler.map(handler -> handler.isSupported(context)).orElse(false);
  }

  @Override
  public Optional<TaskData> createTaskData(FetchManifestTaskContext context) {
    Optional<ManifestTaskHandler> manifestTaskHandler = getManifestTaskHandler(context.getType());
    return manifestTaskHandler.flatMap(handler -> handler.createTaskData(context));
  }

  @Override
  public void handleTaskResponses(
      Map<String, ResponseData> responseDataMap, ManifestsOutcome manifests, Map<String, String> taskIdMapping) {
    responseDataMap.forEach((taskId, response) -> handleTaskResponse(taskId, response, manifests, taskIdMapping));
  }

  private void handleTaskResponse(
      String taskId, ResponseData response, ManifestsOutcome manifests, Map<String, String> taskIdMapping) {
    if (!taskIdMapping.containsKey(taskId)) {
      log.warn("Unable to find task mapping for task id {}", taskId);
      return;
    }

    String manifestIdentifier = taskIdMapping.get(taskId);
    if (!manifests.containsKey(manifestIdentifier)) {
      log.warn("Unable to find manifest by id {} for task id {}", manifestIdentifier, taskId);
      return;
    }

    if (response instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorResponseData = (ErrorNotifyResponseData) response;
      throw errorResponseData.getException() != null ? errorResponseData.getException()
                                                     : new InvalidRequestException(errorResponseData.getErrorMessage());
    }

    ManifestOutcome manifestOutcome = manifests.get(manifestIdentifier);
    Optional<ManifestTaskHandler> manifestTaskHandler = getManifestTaskHandler(manifestOutcome.getType());

    manifestTaskHandler.flatMap(handler -> handler.updateManifestOutcome(response, manifestOutcome))
        .ifPresentOrElse(updatedManifest
            -> manifests.put(manifestIdentifier, updatedManifest),
            ()
                -> log.warn(
                    "No manifest task handler for task id {} and manifest type {}", taskId, manifestOutcome.getType()));
  }

  private Optional<ManifestTaskHandler> getManifestTaskHandler(String type) {
    return Optional.ofNullable(manifestTaskHandlers.get(type));
  }
}
