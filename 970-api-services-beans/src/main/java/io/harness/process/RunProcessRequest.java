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

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class RunProcessRequest {
  String command;
  String pwd;
  Map<String, String> environment;
  OutputStream outputStream;
  OutputStream errorStream;
  long timeout;
  TimeUnit timeoutTimeUnit;
  boolean readOutput;

  public String getProcessKey() {
    return command;
  }
}
