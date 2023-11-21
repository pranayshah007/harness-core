package io.harness.process;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.concurrent.ExecutorService;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
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
