package io.harness.process;

import org.zeroturnaround.exec.ProcessExecutor;

@FunctionalInterface
public interface ProcessExecutorFactory {
  ProcessExecutor create();
}
