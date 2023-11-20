package io.harness.process;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

public class LocalProcessRunner extends AbstractProcessRunner {
  private final ExecutorService executorService;

  public LocalProcessRunner(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  protected ProcessRef execute(String processKey, ProcessExecutorFactory processFactory) {
    Future<ProcessResult> resultFuture = executorService.submit(() -> {
      ProcessExecutor executor = processFactory.create();
      return executor.execute();
    });

    return new SharedProcessRef(resultFuture, new AtomicInteger(1), createClosable(resultFuture));
  }

  private Runnable createClosable(final Future<ProcessResult> future) {
    return () -> {
      if (!future.isDone() && !future.isCancelled()) {
        future.cancel(true);
      }
    };
  }
}
