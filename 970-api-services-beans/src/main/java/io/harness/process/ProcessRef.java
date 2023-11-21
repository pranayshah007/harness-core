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

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.ProcessResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public interface ProcessRef extends AutoCloseable {
  ProcessResult get() throws InterruptedException, ExecutionException, IOException, TimeoutException;

  @Override
  default void close() throws Exception {
    // noop
  }
}
