package io.harness.process;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.zeroturnaround.exec.ProcessResult;

@Value
@AllArgsConstructor
public class LocalProcessRef implements ProcessRef {
  ProcessExecutorFactory processFactory;

  @Override
  public ProcessResult get() throws InterruptedException, ExecutionException, IOException, TimeoutException {
    return processFactory.create().execute();
  }
}
