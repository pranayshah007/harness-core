package io.harness.cdng.manifest.steps.task;

import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManifestTaskServiceImpl implements ManifestTaskService {
  @Inject private Map<String, ManifestTaskHandler> manifestTaskHandlers;

  @Override
  public boolean isSupported(Ambiance ambiance, ManifestOutcome manifest) {
    Optional<ManifestTaskHandler> manifestTaskHandler = getManifestTaskHandler(manifest.getType());
    return manifestTaskHandler.map(handler -> handler.isSupported(ambiance, manifest)).orElse(false);
  }

  @Override
  public Optional<TaskData> createTaskData(Ambiance ambiance, ManifestOutcome manifest) {
    Optional<ManifestTaskHandler> manifestTaskHandler = getManifestTaskHandler(manifest.getType());
    return manifestTaskHandler.flatMap(handler -> handler.createTaskData(ambiance, manifest));
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
