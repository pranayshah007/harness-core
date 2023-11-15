package io.harness.process;

public class LocalProcessRunner extends AbstractProcessRunner {
  @Override
  protected ProcessRef execute(String processKey, ProcessExecutorFactory processFactory) {
    return new LocalProcessRef(processFactory);
  }
}
