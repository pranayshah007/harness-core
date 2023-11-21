/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.process;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.io.File;
import org.zeroturnaround.exec.ProcessExecutor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
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
