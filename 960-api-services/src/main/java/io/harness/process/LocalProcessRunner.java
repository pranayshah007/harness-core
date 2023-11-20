package io.harness.process;

import java.util.concurrent.ExecutorService;

public class LocalProcessRunner extends AbstractProcessRunner {
  private final ExecutorService executorService;

  public LocalProcessRunner(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  protected ProcessRef execute(String processKey, ProcessExecutorFactory processFactory) {
    return new LocalProcessRef(processFactory);
  }
}
