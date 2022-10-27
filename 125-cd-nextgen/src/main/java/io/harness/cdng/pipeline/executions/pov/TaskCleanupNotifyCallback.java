package io.harness.cdng.pipeline.executions.pov;

import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskCleanupNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    Supplier<ResponseData> responseSupplier = response.values().stream().findFirst().get();
    try {
      log.info("Received cleanup response: {}", responseSupplier.get());
    } catch (Exception e) {
      log.error("Something went wrong for cleanup response", e);
    }
  }
}
