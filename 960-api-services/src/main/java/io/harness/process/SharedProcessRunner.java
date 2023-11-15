package io.harness.process;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

public class SharedProcessRunner extends AbstractProcessRunner {
  private final ExecutorService executorService;

  @Getter private final Map<String, RunningProcessHandler> runningProcesses = new ConcurrentHashMap<>();

  public SharedProcessRunner(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  protected ProcessRef execute(String processKey, ProcessExecutorFactory processFactory) {
    RunningProcessHandler processHandler =
        runningProcesses.compute(processKey, (ignore, current) -> submitProcess(current, processFactory));
    return new SharedProcessRef(processHandler.getProcessFeature(), processHandler.getRefCounter(),
        createCloseRunnable(processKey, processHandler));
  }

  private RunningProcessHandler submitProcess(
      final RunningProcessHandler current, final ProcessExecutorFactory factory) {
    if (current == null) {
      Future<ProcessResult> processFeature = executorService.submit(() -> {
        ProcessExecutor exec = factory.create();
        return exec.execute();
      });

      return new RunningProcessHandler(processFeature, new AtomicInteger(1));
    }

    current.getRefCounter().incrementAndGet();
    return current;
  }

  private Runnable createCloseRunnable(final String processKey, final RunningProcessHandler handler) {
    return () -> close(processKey, handler);
  }

  private void close(final String processKey, final RunningProcessHandler handler) {
    if (handler == null) {
      return;
    }

    // prevent other threads to execute further logic
    if (!handler.getRefCounter().compareAndSet(0, -1)) {
      return;
    }

    if (!handler.getProcessFeature().isDone()) {
      handler.getProcessFeature().cancel(true);
    }

    runningProcesses.remove(processKey, handler);
  }

  @Value
  @AllArgsConstructor
  private static class RunningProcessHandler {
    Future<ProcessResult> processFeature;
    AtomicInteger refCounter;
  }
}
