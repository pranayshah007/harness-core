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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.zeroturnaround.exec.ProcessResult;

@Value
@AllArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
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
