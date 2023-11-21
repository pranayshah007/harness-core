package io.harness.process;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.zeroturnaround.exec.ProcessResult;

@Value
@AllArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class FutureProcessRef implements ProcessRef {
  Future<ProcessResult> resultFuture;

  @Override
  public ProcessResult get() throws InterruptedException, ExecutionException, IOException, TimeoutException {
    return resultFuture.get();
  }

  @Override
  public void close() throws Exception {
    if (resultFuture.isDone() || resultFuture.isCancelled()) {
      return;
    }

    resultFuture.cancel(true);
  }
}
