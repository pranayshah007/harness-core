package io.harness.process;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.ProcessResult;

public interface ProcessRef extends AutoCloseable {
  ProcessResult get() throws InterruptedException, ExecutionException, IOException, TimeoutException;

  @Override
  default void close() throws Exception {
    // noop
  }
}
