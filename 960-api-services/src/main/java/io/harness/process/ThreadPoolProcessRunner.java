/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.process;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class ThreadPoolProcessRunner extends AbstractProcessRunner {
  private final ExecutorService executorService;

  public ThreadPoolProcessRunner(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  protected ProcessRef execute(String key, ProcessExecutorFactory processFactory) {
    Future<ProcessResult> resultFuture = executorService.submit(() -> {
      ProcessExecutor executor = processFactory.create();
      return executor.execute();
    });

    return new FutureProcessRef(resultFuture);
  }
}
