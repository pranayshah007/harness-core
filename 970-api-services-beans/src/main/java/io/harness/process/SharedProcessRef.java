package io.harness.process;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.zeroturnaround.exec.ProcessResult;

@Value
@AllArgsConstructor
public class SharedProcessRef implements ProcessRef {
  Future<ProcessResult> resultFuture;
  AtomicInteger refCount;
  Runnable closeCallback;

  @Override
  public ProcessResult get() throws InterruptedException, ExecutionException {
    return resultFuture.get();
  }

  @Override
  public void close() throws Exception {
    if (refCount.decrementAndGet() == 0) {
      closeCallback.run();
    }
  }
}
