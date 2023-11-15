package io.harness.process;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import org.zeroturnaround.exec.ProcessExecutor;

public abstract class AbstractProcessRunner implements ProcessRunner {
  @Override
  public ProcessRef run(final RunProcessRequest request) {
    return execute(request.getProcessKey(), () -> createProcessExecutor(request));
  }

  protected abstract ProcessRef execute(String key, ProcessExecutorFactory processFactory);

  public ProcessExecutor createProcessExecutor(RunProcessRequest request) {
    return new ProcessExecutor()
        .directory(isNotBlank(request.getPwd()) ? new File(request.getPwd()) : null)
        .timeout(request.getTimeout(), request.getTimeoutTimeUnit())
        .commandSplit(request.getCommand())
        .environment(request.getEnvironment())
        .readOutput(request.isReadOutput())
        .redirectOutput(request.getOutputStream())
        .redirectError(request.getErrorStream());
  }
}
