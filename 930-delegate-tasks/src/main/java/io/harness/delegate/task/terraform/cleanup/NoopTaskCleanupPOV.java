package io.harness.delegate.task.terraform.cleanup;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;

@Slf4j
public class NoopTaskCleanupPOV extends AbstractDelegateRunnableTask {
  public NoopTaskCleanupPOV(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof NoopTaskCleanupParameters)) {
      throw new UnsupportedOperationException("Unsupported parameters type");
    }

    NoopTaskCleanupParameters noopTaskParameters = (NoopTaskCleanupParameters) parameters;
    log.info("Cleaning up: {}", noopTaskParameters.getDummyUuid());
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      log.error("Interrupted", e);
    }

    log.info("Cleaning completed for: {}", noopTaskParameters.getDummyUuid());
    return NoopTaskCleanupResponse.builder().responseDataUuid(noopTaskParameters.getDummyUuid()).build();
  }
}
